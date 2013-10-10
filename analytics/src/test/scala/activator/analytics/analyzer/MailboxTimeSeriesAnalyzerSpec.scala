/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.TimeRange
import activator.analytics.repository.MemoryMailboxTimeSeriesRepository
import com.typesafe.trace._
import com.typesafe.trace.store.MemoryTraceRepository
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration._
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MailboxTimeSeriesAnalyzerSpec extends AnalyticsSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  var statsRepository: MemoryMailboxTimeSeriesRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryMailboxTimeSeriesRepository
    analyzer = system.actorOf(Props(new MailboxTimeSeriesAnalyzer(statsRepository, traceRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  "An MailboxTimeSeriesAnalyzer" must {

    "produce mailbox time series with points each second" in {
      val timeRange = TimeRange.minuteRange(System.currentTimeMillis - 10000)
      val startTime = timeRange.startTime
      val path = "akka://MailboxTimeSeriesAnalyzerSpec/user/actorA"

      val expectedPoints = 10
      val statsLatch = new CountDownLatch(1)
      statsRepository.useLatch(statsLatch) { stat ⇒
        stat.timeRange == timeRange && stat.path == path && stat.points.size == expectedPoints
      }

      val actorInfo = ActorInfo(path, Some("dispatcher"), false, false, Set.empty)
      val delay1 = 100

      val sendEvents = for (i ← 0 to 100) yield {
        event(ActorTold(actorInfo, "msg" + i, None)).copy(timestamp = startTime + i * delay1)
      }
      val receiveEvents = for (i ← 2 to 100) yield {
        event(ActorReceived(actorInfo, "msg" + i)).copy(timestamp = startTime + i * delay1 + 1)
      }

      val allEvents = (sendEvents ++ receiveEvents).sortBy(_.timestamp)
      analyzer ! TraceEvents(allEvents)

      val duration = timeoutHandler.timeoutify(10.seconds)
      statsLatch.await(duration.length, duration.unit) must be(true)

      val stat = statsRepository.findBy(timeRange, path)
      stat must not be (None)
      stat.get.points.size must be(expectedPoints)
      stat.get.points.head.size must be >= (1)
      stat.get.points.head.waitTime must be > (0L)

    }

    "collect max mailbox size by actor instance (uuid) when several actors with same address" in {
      val timeRange = TimeRange.minuteRange(System.currentTimeMillis - 10000)
      val startTime = timeRange.startTime
      val path1 = "akka://MailboxTimeSeriesAnalyzerSpec/user/actorA"
      val path2 = "akka://MailboxTimeSeriesAnalyzerSpec/user/actorB"

      val expectedPoints = 1
      val statsLatch = new CountDownLatch(1)
      statsRepository.useLatch(statsLatch) { stat ⇒
        stat.timeRange == timeRange && stat.path == path1 && stat.points.size == expectedPoints
      }

      val actorInfo1 = ActorInfo(path1, Some("dispatcher"), false, false, Set.empty)
      val actorInfo2 = ActorInfo(path2, Some("dispatcher"), false, false, Set.empty)

      val t1 = event(ActorTold(actorInfo1, "msg1", None)).copy(timestamp = startTime)
      val t2 = event(ActorTold(actorInfo1, "msg2", None)).copy(timestamp = startTime + 2)
      val t3 = event(ActorTold(actorInfo1, "msg3", None)).copy(timestamp = startTime + 3)
      val r1 = event(ActorReceived(actorInfo1, "msg1")).copy(parent = t1.id, timestamp = startTime + 4)
      val t4 = event(ActorTold(actorInfo2, "msg4", None)).copy(timestamp = startTime + 1005)
      val r4 = event(ActorReceived(actorInfo2, "msg4")).copy(parent = t4.id, timestamp = startTime + 1006)

      val events = Seq(t1, t2, t3, r1, t4, r4)

      analyzer ! TraceEvents(events)

      val duration = timeoutHandler.timeoutify(10.seconds)
      statsLatch.await(duration.length, duration.unit) must be(true)

      val stat = statsRepository.findBy(timeRange, path1)
      stat must not be (None)
      stat.get.points.size must be(expectedPoints)
      stat.get.points.head.size must be(2)
    }

    "include previous mailbox size when switching time range" in {
      val timeRange1 = TimeRange.minuteRange(System.currentTimeMillis - 10000)
      val timeRange2 = TimeRange.minuteRange(timeRange1.endTime + 100)
      val path = "akka://MailboxTimeSeriesAnalyzerSpec/user/actorC"

      val expectedPoints = 2
      val statsLatch = new CountDownLatch(2)
      statsRepository.useLatch(statsLatch) { stat ⇒
        (stat.timeRange == timeRange1 || stat.timeRange == timeRange2) && stat.path == path
      }

      val actorInfo = ActorInfo(path, Some("dispatcher"), false, false, Set.empty)

      val t1 = event(ActorTold(actorInfo, "msg1", None)).copy(timestamp = timeRange1.startTime + 10)
      val t2 = event(ActorTold(actorInfo, "msg2", None)).copy(timestamp = timeRange1.startTime + 20)
      val t3 = event(ActorTold(actorInfo, "msg3", None)).copy(timestamp = timeRange1.startTime + 30)
      val r1 = event(ActorReceived(actorInfo, "msg1")).copy(parent = t1.id, timestamp = timeRange1.endTime - 5)
      val t4 = event(ActorTold(actorInfo, "msg4", None)).copy(timestamp = timeRange2.startTime + 10)
      val t5 = event(ActorTold(actorInfo, "msg5", None)).copy(timestamp = timeRange2.startTime + 20)
      val r2 = event(ActorReceived(actorInfo, "msg2")).copy(parent = t2.id, timestamp = timeRange2.startTime + 1040)

      val events = Seq(t1, t2, t3, r1, t4, t5, r2)
      analyzer ! TraceEvents(events)

      val duration = timeoutHandler.timeoutify(10.seconds)
      statsLatch.await(duration.length, duration.unit) must be(true)

      val stat1 = statsRepository.findBy(timeRange1, path)
      stat1 must not be (None)
      stat1.get.points.size must be(1)
      stat1.get.points.head.size must be(2)

      val stat2 = statsRepository.findBy(timeRange2, path)
      stat2 must not be (None)
      stat2.get.points.size must be(1)
      stat2.get.points.head.size must be(3)
    }

    "don't use ActorReplied as enqueue" in {
      val timeRange = TimeRange.minuteRange(System.currentTimeMillis - 10000)
      val startTime = timeRange.startTime
      val path = "akka://MailboxTimeSeriesAnalyzerSpec/user/actorD"

      val expectedPoints = 2
      val statsLatch = new CountDownLatch(1)
      statsRepository.useLatch(statsLatch) { stat ⇒
        (stat.timeRange == timeRange) && stat.path == path && stat.points.size == expectedPoints
      }

      val actorInfo = ActorInfo(path, Some("dispatcher"), false, false, Set.empty)
      val t1 = event(ActorTold(actorInfo, "msg1", None)).copy(timestamp = startTime)
      val r1 = event(ActorReceived(actorInfo, "msg1")).copy(parent = t1.id, timestamp = startTime + 1001)
      val a2 = event(ActorAsked(actorInfo, "question2")).copy(timestamp = startTime + 1010)
      val t2 = event(ActorTold(actorInfo, "question2", None)).copy(parent = a2.id, timestamp = startTime + 1010)
      val r2 = event(ActorReceived(actorInfo, "question2")).copy(parent = t2.id, timestamp = startTime + 1020)
      val t3 = event(ActorTold(actorInfo, "msg3", None)).copy(timestamp = startTime + 2002)
      val r3 = event(ActorReceived(actorInfo, "msg3")).copy(parent = t3.id, timestamp = startTime + 2003)
      val events = Seq(t1, r1, a2, t2, r2, t3, r3)

      analyzer ! TraceEvents(events)
      val duration = timeoutHandler.timeoutify(10.seconds)
      statsLatch.await(duration.length, duration.unit) must be(true)

      val stat1 = statsRepository.findBy(timeRange, path)
      stat1 must not be (None)
      stat1.get.points.size must be(expectedPoints)
      stat1.get.points.head.size must be(0) // msg1 told received
      stat1.get.points.last.size must be(0) // question1 asked received
    }
  }

}

