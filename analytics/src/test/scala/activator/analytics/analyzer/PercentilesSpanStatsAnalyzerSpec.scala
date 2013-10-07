/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ Props, ActorSystem, ActorRef, PoisonPill }
import activator.analytics.data.{ TimeRange, Spans, Span, Scope }
import activator.analytics.repository.{ SimpleDuplicatesRepositoryCache, MemoryPercentilesSpanStatsRepository }
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.util.AtmosSpec
import com.typesafe.atmos.util.Uuid
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class PercentilesSpanStatsAnalyzerSpec extends AtmosSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  val cacheRepository = new SimpleDuplicatesRepositoryCache
  var statsRepository: MemoryPercentilesSpanStatsRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryPercentilesSpanStatsRepository
    analyzer = system.actorOf(Props(new PercentilesSpanStatsAnalyzer(None, statsRepository, traceRepository, cacheRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  "A PercentilesSpanStatsAnalyzer" must {

    "produce statistics grouped by time ranges and scopes" in {
      // hour,all
      // all, node, ...
      // 2 x 2 = 4
      val expectedStats = 4
      val latch = new CountDownLatch(expectedStats)
      statsRepository.useLatch(latch) { stat ⇒ stat.n > 0 }

      val startTime = System.currentTimeMillis - 10000
      val markerSpanName = "span1"

      val startEvent = event(MarkerStarted(markerSpanName))
      val endEvent = event(MarkerEnded(markerSpanName))
      traceRepository.store(Batch(Seq(TraceEvents(startEvent :: endEvent :: Nil))))

      val span1 = Span(
        id = Uuid(),
        trace = startEvent.trace,
        spanTypeName = markerSpanName,
        startTime = startTime,
        duration = 10.millis,
        sampled = 3,
        startEvent = startEvent.id,
        endEvent = endEvent.id)

      analyzer ! Spans(span1 :: Nil)
      latch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val scope1 = Scope(node = Some(DefaultNode))
      val stat1 = statsRepository.findBy(TimeRange(), scope1, markerSpanName)
      stat1 must not be (None)
      // note sampling factor of 3
      stat1.get.n must be(3)
      stat1.get.percentiles("50") must be(10.millis.toNanos)

    }
  }

}

