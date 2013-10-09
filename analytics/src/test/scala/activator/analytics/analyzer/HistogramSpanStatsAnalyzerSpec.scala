/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.{ TimeRange, Spans, Span, Scope }
import activator.analytics.repository.MemoryHistogramSpanStatsRepository
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.Batch
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.util.Uuid
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration._
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HistogramSpanStatsAnalyzerSpec extends AnalyticsSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  var statsRepository: MemoryHistogramSpanStatsRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryHistogramSpanStatsRepository
    analyzer = system.actorOf(Props(new HistogramSpanStatsAnalyzer(None, statsRepository, traceRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  "A HistogramSpanStatsAnalyzer" must {

    "produce statistics grouped by time ranges and scopes" in {

      // minute, hour, day, month, all
      // all, node, ...
      // 5 x 2 = 10
      val expectedStats = 10
      val latch = new CountDownLatch(expectedStats)
      statsRepository.useLatch(latch) { stat ⇒ stat.buckets.sum > 0 }

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
      stat1.get.buckets.sum must be(3)
    }
  }
}

