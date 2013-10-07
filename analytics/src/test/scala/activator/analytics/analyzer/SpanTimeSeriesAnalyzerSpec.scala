/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.{ TimeRange, Spans, Span, Scope }
import activator.analytics.repository.{ SimpleDuplicatesRepositoryCache, MemorySpanTimeSeriesRepository }
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.util.AtmosSpec
import com.typesafe.atmos.util.Uuid
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class SpanTimeSeriesAnalyzerSpec extends AtmosSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  val cacheRepository = new SimpleDuplicatesRepositoryCache
  var statsRepository: MemorySpanTimeSeriesRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemorySpanTimeSeriesRepository
    analyzer = system.actorOf(Props(new SpanTimeSeriesAnalyzer(None, statsRepository, traceRepository, cacheRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  "A SpanTimeSeriesAnalyzer" must {

    "produce statistics grouped by time ranges and scopes" in {

      // minute
      // all, node, ...
      // 1 x 2 = 2
      val expectedStats = 2
      val latch = new CountDownLatch(expectedStats)
      statsRepository.useLatch(latch) { stat ⇒
        stat.points.nonEmpty
      }

      val startTime = TimeRange.minuteRange(System.currentTimeMillis - 10000).startTime

      val markerSpanName = "span1"

      val startEvent = event(MarkerStarted(markerSpanName)).copy(timestamp = startTime)
      val endEvent = event(MarkerEnded(markerSpanName)).copy(timestamp = startTime + 10)
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
      val stat1 = statsRepository.findBy(TimeRange.minuteRange(startTime), scope1, markerSpanName)
      stat1 must not be (None)
      stat1.get.points.size must be(1)
      stat1.get.points.head.duration must be(10.millis.toNanos)
    }
  }

}

