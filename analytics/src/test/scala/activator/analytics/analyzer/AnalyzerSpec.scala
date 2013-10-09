/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data._
import com.typesafe.atmos.subscribe.{ Notification, Notifications }
import com.typesafe.atmos.trace._
import com.typesafe.atmos.util.Uuid
import com.typesafe.atmos.uuid.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CountDownLatch
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class AnalyzerSpec extends AnalyticsSpec(AnalyzerSpec.testConfig) with AnalyzeTest {
  import AnalyzerSpec._

  val count = new AtomicInteger(0)
  var listener: ActorRef = _
  var receiver: TraceReceiver = _
  var boot: LocalMemoryAnalyzerBoot = _

  def analyzer = boot.analyzer

  override def beforeEach() {
    listener = system.actorOf(Props[TestListener].withDispatcher(Analyzer.dispatcherId), "testListener")
    receiver = TraceReceiver(config)
    boot = new LocalMemoryAnalyzerBoot(system, Some(receiver)) {
      override def createAnalyzer() = new Analyzer(this) {
        override val eventListeners: Seq[ActorRef] = Seq()
        override val spanListeners: Seq[ActorRef] = Seq(listener)
      }
    }
    boot.traceRepository.clear()
    boot.spanRepository.clear()
  }

  override def afterEach() {
    awaitStop(Seq(listener) ++ boot.topLevelActors)
    receiver.shutdown()
    boot.traceRepository.clear()
    boot.spanRepository.clear()
  }

  val trace = Uuid()

  def markerName(event: TraceEvent): String = event.annotation match {
    case MarkerStarted(name) ⇒ name
    case MarkerEnded(name)   ⇒ name
    case _                   ⇒ throw new IllegalArgumentException("Expected MarkerStarted or MarkerEnded, got " + event)
  }

  def startEvent() = {
    event(MarkerStarted("test" + count.incrementAndGet())).copy(trace = trace)
  }

  def endEvent(name: String, parent: UUID) = {
    event(MarkerEnded(name)).copy(trace = trace, parent = parent)
  }

  def allEvents: Iterable[TraceEvent] = {
    traceRepository.allEvents.filter(_.annotation match {
      case _: SystemStarted ⇒ false
      case _                ⇒ true
    })
  }

  def traceRepository = boot.traceRepository

  def allTraces = traceRepository.allTraces

  def orderedTraces = allTraces.toSeq.sortBy(t ⇒ t.map(_.nanoTime).min)

  def printTraces() = orderedTraces.map(PrettyTrace.show).foreach(println)

  "An Analyzer" must {

    "handle incomplete spans, retry later" in {

      val startEvent1 = startEvent()
      val endEvent1 = endEvent(markerName(startEvent1), startEvent1.id)
      val startEvent2 = startEvent()
      val endEvent2 = endEvent(markerName(startEvent2), startEvent2.id)

      // startEvent1 is missing in the first batch
      val events1 = Seq(endEvent1, startEvent2, endEvent2)

      traceRepository.store(Batch(Seq(TraceEvents(events1))))

      val listenerLatch1 = new CountDownLatch(1)
      listener ! listenerLatch1

      analyzer ! TraceEvents(events1)

      listenerLatch1.await(timeoutHandler.time, timeoutHandler.unit) must be(true)
      val latest1 = boot.spanRepository.latest(100)
      latest1.filter(_.spanTypeName == markerName(startEvent2)).size must be(1)
      latest1.size must be(1)

      val listenerLatch2 = new CountDownLatch(1)
      listener ! listenerLatch2

      val events2 = Seq(startEvent1)
      traceRepository.store(Batch(Seq(TraceEvents(events2))))
      analyzer ! TraceEvents(events2)

      listenerLatch2.await(timeoutHandler.time, timeoutHandler.unit) must be(true)
      val latest2 = boot.spanRepository.latest(100)
      latest2.filter(_.spanTypeName == markerName(startEvent1)).size must be(1)
      latest2.filter(_.spanTypeName == markerName(startEvent2)).size must be(1)
      latest2.size must be(2)

    }

    "handle notifications" in {

      val eventPairs = for (n ← 1 to 5) yield {
        val start = startEvent()
        val end = endEvent(markerName(start), start.id)
        Seq(start, end)
      }
      val events = eventPairs.flatten

      traceRepository.store(Batch(Seq(TraceEvents(events))))

      val expectedSpans = 5
      val listenerLatch = new CountDownLatch(expectedSpans)
      listener ! listenerLatch

      val notifications = Notifications(events.map(event ⇒ Notification(event.timestamp, event.id)))
      analyzer ! notifications

      listenerLatch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

    }

    "handle incomplete notifications, retry later" in {

      val startEvent1 = startEvent()
      val endEvent1 = endEvent(markerName(startEvent1), startEvent1.id)
      val startEvent2 = startEvent()
      val endEvent2 = endEvent(markerName(startEvent2), startEvent2.id)

      // endEvent1 is not stored in traceRepository when first batch arrives
      val events1 = Seq(startEvent1, startEvent2, endEvent2)
      val events2 = Seq(endEvent1)
      val allEvents = events1 ++ events2
      traceRepository.store(Batch(Seq(TraceEvents(events1))))

      val listenerLatch1 = new CountDownLatch(1)
      listener ! listenerLatch1

      // notifications for all events
      val notifications = Notifications(allEvents.map(event ⇒ Notification(event.timestamp, event.id)))
      analyzer ! notifications

      listenerLatch1.await(timeoutHandler.time, timeoutHandler.unit) must be(true)
      val latest1 = boot.spanRepository.latest(100)
      latest1.filter(_.spanTypeName == markerName(startEvent1)).size must be(0)
      latest1.filter(_.spanTypeName == markerName(startEvent2)).size must be(1)
      latest1.size must be(1)

      val listenerLatch2 = new CountDownLatch(1)
      listener ! listenerLatch2

      traceRepository.store(Batch(Seq(TraceEvents(events2))))

      listenerLatch2.await(timeoutHandler.time, timeoutHandler.unit) must be(true)
      val latest2 = boot.spanRepository.latest(100)
      latest2.filter(_.spanTypeName == markerName(startEvent1)).size must be(1)
      latest2.filter(_.spanTypeName == markerName(startEvent2)).size must be(1)
      latest2.size must be(2)

    }

  }

}

object AnalyzerSpec {
  class TestListener extends Actor with ActorLogging {
    var spanCount = 0
    var latch: Option[CountDownLatch] = None

    def receive = {
      case cdl: CountDownLatch ⇒
        latch = Some(cdl)
      case Spans(spans) ⇒
        spanCount += spans.size
        log.debug("Received %s spans, now got %s" format (spans.size, spanCount))
        for (cdl ← latch; s ← spans) cdl.countDown()
    }
  }

  val testConfig = """
    activator {
      analytics.retry-delay = 1 second
    }"""
}
