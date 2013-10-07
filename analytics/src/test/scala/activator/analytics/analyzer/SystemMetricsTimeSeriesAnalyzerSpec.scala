/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.TimeRange
import activator.analytics.repository.DuplicatesRepositoryCache
import activator.analytics.repository.MemorySystemMetricsTimeSeriesRepository
import activator.analytics.repository.SimpleDuplicatesRepositoryCache
import activator.analytics.repository.SpecialSystemMetricsTimeSeriesRepository
import com.typesafe.atmos.trace.ActorCreated
import com.typesafe.atmos.trace.ActorInfo
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.trace.SysMsgCompleted
import com.typesafe.atmos.trace.SystemMetrics
import com.typesafe.atmos.trace.TerminateSysMsg
import com.typesafe.atmos.trace.TraceEvents
import com.typesafe.atmos.util.AtmosSpec
import java.util.concurrent.CountDownLatch

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class SystemMetricsTimeSeriesAnalyzerSpec extends AtmosSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  val cacheRepository = new SimpleDuplicatesRepositoryCache
  var timeSeriesRepository: MemorySystemMetricsTimeSeriesRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    timeSeriesRepository = new MemorySystemMetricsTimeSeriesRepository
    analyzer = system.actorOf(Props(new SystemMetricsTimeSeriesAnalyzer(timeSeriesRepository, traceRepository, cacheRepository, alertDispatcher)))
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

    "not create analytic data from the same trace event more than once" in {
      val expectedStats = 1
      val latch = new CountDownLatch(expectedStats)
      timeSeriesRepository.useLatch(latch) { stat ⇒
        !stat.points.isEmpty
      }

      // Use real duplicates cache implementation
      val duplicatesRepo = new DuplicatesRepositoryCache(system)
      val duplicatesAnalyzer = system.actorOf(Props(new SystemMetricsTimeSeriesAnalyzer(timeSeriesRepository, traceRepository, duplicatesRepo, alertDispatcher)))

      val node = DefaultNode
      val timestamp = TimeRange.minuteRange(System.currentTimeMillis - 10000).startTime
      val runningActors = 44
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
      val traceEvent2 = event(SystemMetrics(runningActors, startTime + 1, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvent3 = event(SystemMetrics(runningActors, startTime + 2, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvent4 = event(SystemMetrics(runningActors, startTime + 3, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvent5 = event(SystemMetrics(runningActors, startTime + 4, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEventsTraceEvents1 = TraceEvents(traceEvent1 :: traceEvent2 :: traceEvent3 :: Nil)
      val traceEventsTraceEvents2 = TraceEvents(traceEvent1 :: traceEvent2 :: traceEvent3 :: traceEvent4 :: traceEvent5 :: Nil)

      // Send batch one and verify that stats have been created
      duplicatesAnalyzer ! traceEventsTraceEvents1
      latch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val systemMetricsTimeSeries = timeSeriesRepository.findBy(TimeRange.minuteRange(timestamp), node)
      systemMetricsTimeSeries must not be (None)
      systemMetricsTimeSeries.get.node must equal(node)
      systemMetricsTimeSeries.get.points.size must be(3)

      val latch2 = new CountDownLatch(expectedStats)
      timeSeriesRepository.useLatch(latch2) { stat ⇒
        !stat.points.isEmpty
      }

      // Send batch two and verify that only new stats (traceEvent4 and traceEvent5) have been created
      duplicatesAnalyzer ! traceEventsTraceEvents2
      latch2.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val systemMetricsTimeSeries2 = timeSeriesRepository.findBy(TimeRange.minuteRange(timestamp), node)
      systemMetricsTimeSeries2 must not be (None)
      systemMetricsTimeSeries2.get.node must equal(node)
      systemMetricsTimeSeries2.get.points.size must be(5)
    }

    "should handle an exception from repository and retry without loosing any data" in {
      val systemMetricsRepo = new SpecialSystemMetricsTimeSeriesRepository
      val duplicatesRepo = new DuplicatesRepositoryCache(system)
      val duplicatesAnalyzer = system.actorOf(Props(new SystemMetricsTimeSeriesAnalyzer(systemMetricsRepo, traceRepository, duplicatesRepo, alertDispatcher)))

      val node = DefaultNode
      val timestamp = TimeRange.minuteRange(System.currentTimeMillis - 10000).startTime
      val runningActors = 44
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
      val traceEvent2 = event(SystemMetrics(runningActors, startTime + 1, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvent3 = event(SystemMetrics(runningActors, startTime + 2, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvent4 = event(SystemMetrics(runningActors, startTime + 3, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEvent5 = event(SystemMetrics(runningActors, startTime + 4, upTime, availableProcessors, daemonThreadCount, threadCount, peakThreadCount, committedHeap, maxHeap, usedHeap, committedNonHeap, maxNonHeap, usedNonHeap, gcCountPerMinute, gcTimePercent, systemAverageLoad)).copy(timestamp = startTime)
      val traceEventsTraceEvents1 = TraceEvents(traceEvent1 :: traceEvent2 :: traceEvent3 :: Nil)
      val traceEventsTraceEvents2 = TraceEvents(traceEvent1 :: traceEvent2 :: traceEvent3 :: traceEvent4 :: traceEvent5 :: Nil)

      val expectedStats = 1
      val latch = new CountDownLatch(expectedStats)
      systemMetricsRepo.useLatch(latch) { stat ⇒
        !stat.points.isEmpty
      }

      duplicatesAnalyzer ! traceEventsTraceEvents1
      latch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val systemMetricsTimeSeries = systemMetricsRepo.findBy(TimeRange.minuteRange(timestamp), node)
      systemMetricsTimeSeries must not be (None)
      systemMetricsTimeSeries.get.node must equal(node)
      systemMetricsTimeSeries.get.points.size must be(3)

      // Now make the repo throw an exception
      systemMetricsRepo.throwExceptionDuringSave(true)
      duplicatesAnalyzer ! traceEventsTraceEvents2

      // Wait a little for everything to settle
      Thread.sleep(1000)

      // Make sure repo is not altered since previous save
      val systemMetricsTimeSeries2 = systemMetricsRepo.findBy(TimeRange.minuteRange(timestamp), node)
      systemMetricsTimeSeries2 must not be (None)
      systemMetricsTimeSeries2.get.node must equal(node)
      systemMetricsTimeSeries2.get.points.size must be(3)

      // Toggle repo behavior again (no exception thrown this time)
      systemMetricsRepo.throwExceptionDuringSave(false)
      val latch2 = new CountDownLatch(expectedStats)
      systemMetricsRepo.useLatch(latch2) { stat ⇒
        !stat.points.isEmpty
      }
      duplicatesAnalyzer ! traceEventsTraceEvents2
      latch2.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      // Now all 5 time series must be available
      val systemMetricsTimeSeries3 = systemMetricsRepo.findBy(TimeRange.minuteRange(timestamp), node)
      systemMetricsTimeSeries3 must not be (None)
      systemMetricsTimeSeries3.get.node must equal(node)
      systemMetricsTimeSeries3.get.points.size must be(5)
    }
  }
}
