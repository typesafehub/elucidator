/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.{ TimeRange, Spans, Span, Scope }
import activator.analytics.repository.{ DuplicatesRepositoryCache, SimpleDuplicatesRepositoryCache, MemoryHistogramSpanStatsRepository }
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.Batch
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.util.AtmosSpec
import com.typesafe.atmos.util.Uuid
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HistogramSpanStatsAnalyzerSpec extends AtmosSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  val cacheRepository = new SimpleDuplicatesRepositoryCache
  var statsRepository: MemoryHistogramSpanStatsRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryHistogramSpanStatsRepository
    analyzer = system.actorOf(Props(new HistogramSpanStatsAnalyzer(None, statsRepository, traceRepository, cacheRepository, alertDispatcher)))
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

    "not create analytic data from the same span more than once" in {
      // Use real cache implementation
      val duplicatesRepo = new DuplicatesRepositoryCache(system)
      val duplicatesAnalyzer = system.actorOf(Props(new HistogramSpanStatsAnalyzer(None, statsRepository, traceRepository, duplicatesRepo, alertDispatcher)))

      val expectedStats = 10
      val latch1 = new CountDownLatch(expectedStats)
      statsRepository.useLatch(latch1) { stat ⇒
        stat.buckets.sum > 0
      }

      val startTime = System.currentTimeMillis - 10000

      val nodeAddress = DefaultNode
      val userSpanName = "span1"

      val startEvent1 = event(MarkerStarted(userSpanName))
      val endEvent1 = event(MarkerEnded(userSpanName))
      val startEvent2 = event(MarkerStarted(userSpanName))
      val endEvent2 = event(MarkerEnded(userSpanName))
      traceRepository.store(Batch(Seq(TraceEvents(startEvent1 :: endEvent1 :: startEvent2 :: endEvent2 :: Nil))))

      val span1 = Span(
        id = Uuid(),
        trace = startEvent1.trace,
        spanTypeName = userSpanName,
        startTime = startTime,
        duration = 10.millis,
        sampled = 3,
        startEvent = startEvent1.id,
        endEvent = endEvent1.id)

      duplicatesAnalyzer ! Spans(span1 :: Nil)

      latch1.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val scope1 = Scope(node = Some(nodeAddress))
      val stats1 = statsRepository.findWithinTimePeriod(TimeRange.minuteRange(startTime), scope1, userSpanName)
      stats1 must not be (None)
      stats1.size must be(1)
      // note sampling factor of 3
      stats1.head.buckets.sum must be(3)

      // Now send a new and the old span to the analyzer

      val latch2 = new CountDownLatch(expectedStats)
      statsRepository.useLatch(latch2) { stat ⇒
        stat.buckets.sum > 0
      }

      val span2 = Span(
        id = Uuid(),
        trace = startEvent2.trace,
        spanTypeName = userSpanName,
        startTime = startTime,
        duration = 23.millis,
        sampled = 3,
        startEvent = startEvent2.id,
        endEvent = endEvent2.id)

      duplicatesAnalyzer ! Spans(span1 :: span2 :: Nil)

      latch2.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val stats2 = statsRepository.findWithinTimePeriod(TimeRange.minuteRange(startTime), scope1, userSpanName)
      stats2 must not be (None)
      stats2.size must be(1)
      stats2.head.buckets.sum must be(6)
    }
  }

}

