/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.TimeRange
import activator.analytics.repository.{ SimpleDuplicatesRepositoryCache, MemoryDispatcherTimeSeriesRepository }
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.trace.{ TraceEvents, TraceEvent, DispatcherStatus, DispatcherMetrics }
import com.typesafe.atmos.util.AtmosSpec
import java.util.concurrent.CountDownLatch

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class DispatcherTimeSeriesAnalyzerSpec extends AtmosSpec with AnalyzeTest {
  val traceRepository = new MemoryTraceRepository
  var statsRepository: MemoryDispatcherTimeSeriesRepository = _
  val cacheRepository = new SimpleDuplicatesRepositoryCache
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryDispatcherTimeSeriesRepository
    analyzer = system.actorOf(Props(new DispatcherTimeSeriesAnalyzer(statsRepository, traceRepository, cacheRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  "A DispatcherTimeSeriesAnalyzer" must {
    "produce statistics grouped by time ranges, dispatcher and node" in {

      val expectedStats = 1
      val latch = new CountDownLatch(expectedStats)
      statsRepository.useLatch(latch) { stat ⇒
        !stat.points.isEmpty
      }

      val startTime = TimeRange.minuteRange(System.currentTimeMillis - 10000).startTime
      val node = DefaultNode
      val dispatcher1 = "dispatcher1"
      val dispatcherType1 = "ThreadPoolExecutor"
      val dispatcher2 = "dispatcher2"
      val dispatcherType2 = "MyOwnExecutor"

      val corePoolSize = 4
      val maximumPoolSize = 8
      val keepAliveTime = 10000
      val rejectedHandler = ""
      val activeThreadCount = 4
      val taskCount = 1000
      val completedTaskCount = 2000
      val largestPoolSize = 20
      val poolSize = 10
      val queueSize = 23

      val status1 = event(DispatcherStatus(dispatcher1, dispatcherType1,
        DispatcherMetrics(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler, activeThreadCount, taskCount, completedTaskCount, largestPoolSize, poolSize, queueSize))).copy(timestamp = startTime)
      val status2 = event(DispatcherStatus(dispatcher1, dispatcherType1,
        DispatcherMetrics(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler, activeThreadCount, taskCount, completedTaskCount, largestPoolSize, poolSize, queueSize))).copy(timestamp = startTime)
      val status3 = event(DispatcherStatus(dispatcher1, dispatcherType1,
        DispatcherMetrics(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler, activeThreadCount, taskCount, completedTaskCount, largestPoolSize, poolSize, queueSize))).copy(timestamp = startTime)
      val status4 = event(DispatcherStatus(dispatcher1, dispatcherType1,
        DispatcherMetrics(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler, activeThreadCount, taskCount, completedTaskCount, largestPoolSize, poolSize, queueSize))).copy(timestamp = startTime)
      val status5 = event(DispatcherStatus(dispatcher1, dispatcherType1,
        DispatcherMetrics(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler, activeThreadCount, taskCount, completedTaskCount, largestPoolSize, poolSize, queueSize))).copy(timestamp = startTime)
      val status6 = event(DispatcherStatus(dispatcher2, dispatcherType2,
        DispatcherMetrics(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler, activeThreadCount, taskCount, completedTaskCount, largestPoolSize, poolSize, queueSize))).copy(timestamp = startTime)
      val status7 = event(DispatcherStatus(dispatcher2, dispatcherType2,
        DispatcherMetrics(corePoolSize, maximumPoolSize, keepAliveTime, rejectedHandler, activeThreadCount, taskCount, completedTaskCount, largestPoolSize, poolSize, queueSize))).copy(timestamp = startTime)
      val traceEvents = TraceEvents(status1 :: status2 :: status3 :: status4 :: status5 :: status6 :: status7 :: Nil)

      analyzer ! traceEvents
      latch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val dispatcherTimeSeries1_1 = statsRepository.findBy(TimeRange.minuteRange(startTime), node, system.name, dispatcher1)
      dispatcherTimeSeries1_1 must not be (None)
      dispatcherTimeSeries1_1.get.dispatcher must equal(dispatcher1)
      dispatcherTimeSeries1_1.get.dispatcherType must equal(dispatcherType1)
      dispatcherTimeSeries1_1.get.points.size must be(5)

      val dispatcherTimeSeries2_1 = statsRepository.findBy(TimeRange.minuteRange(startTime), node, system.name, dispatcher2)
      dispatcherTimeSeries2_1 must not be (None)
      dispatcherTimeSeries2_1.get.dispatcher must equal(dispatcher2)
      dispatcherTimeSeries2_1.get.dispatcherType must equal(dispatcherType2)
      dispatcherTimeSeries2_1.get.points.size must be(2)
    }
  }
}
