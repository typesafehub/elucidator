/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.TimeRange
import activator.analytics.repository.MemoryRemoteStatusStatsRepository
import com.typesafe.atmos.trace.RemoteStatus
import com.typesafe.atmos.trace.RemoteStatusType.RemoteClientWriteFailedType
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.trace.TraceEvents
import java.util.concurrent.CountDownLatch
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class RemoteStatusStatsAnalyzerSpec extends AnalyticsSpec with AnalyzeTest {
  val traceRepository = new MemoryTraceRepository
  var statsRepository: MemoryRemoteStatusStatsRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryRemoteStatusStatsRepository
    analyzer = system.actorOf(Props(new RemoteStatusStatsAnalyzer(statsRepository, traceRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  "An RemoteStatusStatsAnalyzer" must {

    "count remote status events" in {
      val node = DefaultNode
      val timeRange = TimeRange()

      val statsLatch = new CountDownLatch(1)
      statsRepository.useLatch(statsLatch) { stat ⇒
        stat.timeRange == timeRange && stat.node == Some(node)
      }

      val events = List(
        event(RemoteStatus(statusType = RemoteClientWriteFailedType, serverNode = Some(node))))

      analyzer ! TraceEvents(events)

      statsLatch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val stat = statsRepository.findBy(timeRange, Some(node), None)
      stat must not be (None)
      stat.get.metrics.counts(RemoteClientWriteFailedType.toString) must be(1)
      stat.get.metrics.counts.size must be(1)

    }
  }

}

