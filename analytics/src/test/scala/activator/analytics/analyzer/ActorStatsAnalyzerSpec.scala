/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.{ TimeRange, Scope }
import activator.analytics.metrics.RateMetric
import activator.analytics.repository.MemoryActorStatsRepository
import com.typesafe.trace._
import com.typesafe.trace.store.MemoryTraceEventListener
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit._
import activator.analytics.AnalyticsSpec
import activator.analytics.TimeoutHandler

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ActorStatsAnalyzerSpec extends AnalyticsSpec(ActorStatsAnalyzerSpec.testConfig) with AnalyzeTest {
  import ActorStatsAnalyzerSpec._

  val traceReceiver = TraceReceiver(config)
  val traceRepository = MemoryTraceEventListener.getRepositoryFor(traceReceiver).getOrElse(
    throw new RuntimeException("Need to use MemoryTraceEventListener with " + system.name))
  var statsRepository: MemoryActorStatsRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryActorStatsRepository
    analyzer = system.actorOf(Props(new ActorStatsAnalyzer(None, statsRepository, traceRepository, alertDispatcher)), "testAnalyzer")
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  override def afterAll() {
    traceReceiver.shutdown()
    super.afterAll()
  }

  def node(system: ActorSystem) = system.name + "@" + HostName

  def allTraces = traceRepository.allTraces

  def orderedTraces = allTraces.toSeq.sortBy(t ⇒ t.map(_.nanoTime).min)

  def printTraces() = orderedTraces.map(PrettyTrace.show).foreach(println)

  "An ActorStatsAnalyzer" must {

    "produce message rates and account for the sampling factor" in {
      val scope = Scope(node = Some(DefaultNode))
      val timeRange = TimeRange()

      val statsLatch = new CountDownLatch(2)
      statsRepository.useLatch(statsLatch) { stat ⇒
        stat.timeRange == timeRange && stat.scope == scope && stat.metrics.counts.processedMessagesCount > 0
      }

      val actorInfo = ActorInfo("akka://ActorStatsAnalyzerSpec/user/actor", Some("dispatcher"), false, false, Set.empty)
      val startTime = System.currentTimeMillis
      val delay1 = 10
      val events1 = for (i ← 0 until RateMetric.DefaultMaxHistory) yield {
        event(ActorReceived(actorInfo, "msg" + i)).copy(timestamp = startTime + i * delay1, sampled = 3)
      }

      analyzer ! TraceEvents(events1)

      val startTime2 = startTime + RateMetric.DefaultMaxHistory * delay1
      val events2 = for (i ← 0 until RateMetric.DefaultMaxHistory) yield {
        event(ActorReceived(actorInfo, "anothermsg" + i)).copy(timestamp = startTime2 + i, sampled = 3)
      }

      analyzer ! TraceEvents(events2)

      statsLatch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val stat = statsRepository.findBy(timeRange, scope)

      stat must not be (None)
      stat.get.metrics.latestMessageTimestamp must not be (0L)
      stat.get.metrics.latestTraceEventTimestamp must not be (0L)
      // note that the sampling factor is 3
      stat.get.metrics.messageRateMetrics.totalMessageRate must be(3000.0 plusOrMinus 0.1)
      stat.get.metrics.peakMessageRateMetrics.totalMessageRate must be(3000.0 plusOrMinus 0.1)
      stat.get.metrics.peakMessageRateMetrics.totalMessageRateTimestamp must not be (0L)
      stat.get.metrics.messageRateMetrics.receiveRate must be(3000.0 plusOrMinus 0.1)
      stat.get.metrics.peakMessageRateMetrics.receiveRate must be(3000.0 plusOrMinus 0.1)
      stat.get.metrics.peakMessageRateMetrics.receiveRateTimestamp must not be (0L)
    }

    "produce mean/max mailbox size and time" in {
      val scope = Scope(node = Some(DefaultNode))
      val timeRange = TimeRange()

      val statsLatch = new CountDownLatch(1)
      statsRepository.useLatch(statsLatch) { stat ⇒
        stat.timeRange == timeRange && stat.scope == scope
      }

      // note that the actor info of ActorTold is for the target actor
      val actorInfo = ActorInfo("akka://ActorStatsAnalyzerSpec/user/actor1", Some("dispatcher"), false, false, Set.empty)
      val startTime = System.currentTimeMillis
      val nano = System.nanoTime

      val t1 = event(ActorTold(actorInfo, "msg1", None)).copy(timestamp = startTime, nanoTime = nano)
      val r1 = event(ActorReceived(actorInfo, "msg1")).copy(parent = t1.id, timestamp = startTime + 5, nanoTime = nano + 5000000L)
      val t2 = event(ActorTold(actorInfo, "msg2", None)).copy(timestamp = startTime + 10, nanoTime = nano + 10000000L)
      val t3 = event(ActorTold(actorInfo, "msg3", None)).copy(timestamp = startTime + 10, nanoTime = nano + 10500000L)
      val t4 = event(ActorTold(actorInfo, "msg4", None)).copy(timestamp = startTime + 11, nanoTime = nano + 11000000L)
      val t5 = event(ActorTold(actorInfo, "msg5", None)).copy(timestamp = startTime + 11, nanoTime = nano + 11300000L)
      val r2 = event(ActorReceived(actorInfo, "msg2")).copy(parent = t2.id, timestamp = startTime + 20, nanoTime = nano + 20000000L)
      val r3 = event(ActorReceived(actorInfo, "msg3")).copy(parent = t3.id, timestamp = startTime + 25, nanoTime = nano + 25000000L)
      val r4 = event(ActorReceived(actorInfo, "msg4")).copy(parent = t4.id, timestamp = startTime + 32, nanoTime = nano + 32000000L)
      val r5 = event(ActorReceived(actorInfo, "msg5")).copy(parent = t5.id, timestamp = startTime + 39, nanoTime = nano + 39000000L)

      val events = IndexedSeq(t1, r1, t2, t3, t4, t5, r2, r3, r4, r5)

      analyzer ! TraceEvents(events)

      statsLatch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val stat = statsRepository.findBy(timeRange, scope)
      stat must not be (None)

      // the mean is calculated from the mailbox size when msg received, i.e. these values
      // received msg1: size=1, time=5ms
      // received msg2: size=4, time=10ms
      // received msg3: size=3, time=14.5ms
      // received msg4: size=2, time=21ms
      // received msg5: size=1, time=27.7ms
      val expectedMeanSize = (1 + 4 + 3 + 2 + 1).toDouble / 5
      stat.get.metrics.meanMailboxSize must be(expectedMeanSize.plusOrMinus(0.001))
      val expectedMeanTime = NANOSECONDS.convert(5000 + 10000 + 14500 + 21000 + 27700, MICROSECONDS) / 5
      stat.get.metrics.meanTimeInMailbox must be(expectedMeanTime)

      stat.get.metrics.mailbox.maxMailboxSize must be(4)
      stat.get.metrics.mailbox.maxMailboxSizeAddress.path must be("akka://ActorStatsAnalyzerSpec/user/actor1")
      stat.get.metrics.mailbox.maxTimeInMailbox must be(39000000L - 11300000L)
      stat.get.metrics.mailbox.maxMailboxSizeAddress.path must be("akka://ActorStatsAnalyzerSpec/user/actor1")

    }

    "produce counts for trace events" in {
      val scope = Scope(node = Some(DefaultNode))
      val timeRange = TimeRange()

      val statsLatch = new CountDownLatch(1)
      statsRepository.useLatch(statsLatch) { stat ⇒
        stat.timeRange == timeRange && stat.scope == scope
      }

      val actorInfo = ActorInfo("akka://ActorStatsAnalyzerSpec/user/actor1", Some("dispatcher"), false, false, Set.empty)
      val startTime = System.currentTimeMillis

      val t1 = event(ActorTold(actorInfo, "msg1", None)).copy(timestamp = startTime)
      val r1 = event(ActorReceived(actorInfo, "msg1")).copy(parent = t1.id, timestamp = startTime + 9)
      val e1 = event(SysMsgCompleted(actorInfo, RecreateSysMsg("some error"))).copy(timestamp = startTime + 10)
      val t2 = event(ActorTold(actorInfo, "msg2", None)).copy(timestamp = startTime + 10)
      val r2 = event(ActorReceived(actorInfo, "msg2")).copy(parent = t2.id, timestamp = startTime + 12)
      val t3 = event(ActorTold(actorInfo, "msg3", None)).copy(timestamp = startTime + 13)
      val r3 = event(ActorReceived(actorInfo, "msg3")).copy(parent = t3.id, timestamp = startTime + 14)
      val e2 = event(ActorFailed(actorInfo, "some error", actorInfo)).copy(timestamp = startTime + 15)
      val e3 = event(SysMsgCompleted(actorInfo, RecreateSysMsg("some error"))).copy(timestamp = startTime + 17)
      val e4 = event(SysMsgCompleted(actorInfo, TerminateSysMsg)).copy(timestamp = startTime + 19)

      val events = IndexedSeq(t1, r1, e1, t2, r2, t3, r3, e2, e3, e4)

      analyzer ! TraceEvents(events)

      statsLatch.await(timeoutHandler.time, timeoutHandler.unit) must be(true)

      val stat = statsRepository.findBy(timeRange, scope)
      stat must not be (None)
      stat.get.metrics.counts.restartCount must be(2)
      stat.get.metrics.counts.failedCount must be(1)
      stat.get.metrics.counts.stoppedCount must be(1)
      stat.get.metrics.counts.processedMessagesCount must be(3)
    }
  }
}

object ActorStatsAnalyzerSpec {
  val testConfig = """
    activator {
      trace.receive.port = 0
      analytics {
        ignore-span-types = []
        ignore-span-time-series = []
        store-time-interval = 0
        store-limit = 30000
        store-use-random-interval = false
        store-use-all-time = true
        store-use-duplicates-cache = true
        store-flush-delay = 0
        actor-path-time-ranges = ["minutes", "hours", "days", "months", "all"]
      }
    }"""
}

