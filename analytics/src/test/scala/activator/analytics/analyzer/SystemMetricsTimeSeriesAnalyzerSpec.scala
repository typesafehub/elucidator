/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.TimeRange
import activator.analytics.repository.{ SpecialSystemMetricsTimeSeriesRepository, MemorySystemMetricsTimeSeriesRepository }
import com.typesafe.trace.ActorCreated
import com.typesafe.trace.ActorInfo
import com.typesafe.trace.store.MemoryTraceRepository
import com.typesafe.trace.SysMsgCompleted
import com.typesafe.trace.SystemMetrics
import com.typesafe.trace.TerminateSysMsg
import com.typesafe.trace.TraceEvents
import java.util.concurrent.CountDownLatch
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class SystemMetricsTimeSeriesAnalyzerSpec extends AnalyticsSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  var timeSeriesRepository: MemorySystemMetricsTimeSeriesRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    timeSeriesRepository = new MemorySystemMetricsTimeSeriesRepository
    analyzer = system.actorOf(Props(new SystemMetricsTimeSeriesAnalyzer(timeSeriesRepository, traceRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    timeSeriesRepository.clear()
    timeSeriesRepository = null
  }

  "A SystemMetricsTimeSeriesAnalyzer" must {

    "produce statistics grouped by minute based time ranges and node" in {

      val expectedStats = 1
      val latch = new CountDownLatch(expectedStats)
      timeSeriesRepository.useLatch(latch) { stat ⇒ !stat.points.isEmpty }

      val node = DefaultNode
      val timestamp = TimeRange.minuteRange(System.currentTimeMillis - 10000).startTime
      val runningActors = -1
      val startTime = timestamp
      val upTime = 12345
      val availableProcessors = 4
      val daemonThreadCount = 23
      val threadCount = 46
      val peakThreadCount = 46
      val committedHeap = 123456
      val maxHeap = 234567
      val usedHeap = 321
      val committedNonHeap = 12345
      val maxNonHeap = 23456
      val usedNonHeap = 32
      val gcCountPerMinute = 15.0
      val gcTimePercent = 2.0
      val systemAverageLoad = 50.0

      val traceEvent1 = event(SystemMetrics(runningActors, startTime, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvent2 = event(SystemMetrics(runningActors, startTime, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvents = TraceEvents(traceEvent1 :: traceEvent2 :: Nil)

      analyzer ! traceEvents
      latch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val systemMetricsTimeSeries = timeSeriesRepository.findBy(TimeRange.minuteRange(timestamp), node)
      systemMetricsTimeSeries must not be (None)
      systemMetricsTimeSeries.get.node must equal(node)
      systemMetricsTimeSeries.get.points.size must be(2)
    }

    "count number of runningActors" in {

      val expectedStats = 1
      val latch = new CountDownLatch(expectedStats)
      timeSeriesRepository.useLatch(latch) { stat ⇒
        !stat.points.isEmpty
      }

      val node = DefaultNode
      val timestamp = TimeRange.minuteRange(System.currentTimeMillis - 10000).startTime
      val runningActors = 0
      val startTime = timestamp
      val upTime = 12345
      val availableProcessors = 4
      val daemonThreadCount = 23
      val threadCount = 46
      val peakThreadCount = 46
      val committedHeap = 123456
      val maxHeap = 234567
      val usedHeap = 321
      val committedNonHeap = 12345
      val maxNonHeap = 23456
      val usedNonHeap = 32
      val gcCountPerMinute = 15.0
      val gcTimePercent = 2.0
      val systemAverageLoad = 50.0

      val event1 = event(SystemMetrics(runningActors, startTime, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val actorInfo1 = ActorInfo("akka://SystemMetricsTimeSeriesAnalyzerSpec/user/actor1", Some("dispatcher"), false, false, Set.empty)
      val event2 = event(ActorCreated(actorInfo1)).copy(timestamp = startTime + 100)
      val event3 = event(ActorCreated(ActorInfo("akka://SystemMetricsTimeSeriesAnalyzerSpec/user/actor2", Some("dispatcher"), false, false, Set.empty))).copy(timestamp = startTime + 200)
      val event4 = event(SystemMetrics(runningActors, startTime, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime + 3000)
      val event5 = event(SysMsgCompleted(actorInfo1, TerminateSysMsg)).copy(timestamp = startTime + 3020)
      val event6 = event(SystemMetrics(runningActors, startTime, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime + 6000)
      val traceEvents = TraceEvents(event1 :: event2 :: event3 :: event4 :: event5 :: event6 :: Nil)

      analyzer ! traceEvents
      latch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val systemMetricsTimeSeries = timeSeriesRepository.findBy(TimeRange.minuteRange(timestamp), node)
      systemMetricsTimeSeries must not be (None)
      systemMetricsTimeSeries.get.node must equal(node)
      systemMetricsTimeSeries.get.points.size must be(3)
      systemMetricsTimeSeries.get.points(0).metrics.runningActors must be(0)
      systemMetricsTimeSeries.get.points(1).metrics.runningActors must be(2)
      systemMetricsTimeSeries.get.points(2).metrics.runningActors must be(1)
    }
  }
}
