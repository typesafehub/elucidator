/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.data.{ TimeRange, Scope }
import activator.analytics.repository.MemoryMessageRateTimeSeriesRepository
import com.typesafe.atmos.trace.ActorInfo
import com.typesafe.atmos.trace.ActorReceived
import com.typesafe.atmos.trace.store.MemoryTraceRepository
import com.typesafe.atmos.trace.TraceEvents
import com.typesafe.atmos.util.AtmosSpec
import java.util.concurrent.CountDownLatch
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MessageRateTimeSeriesAnalyzerSpec extends AtmosSpec with AnalyzeTest {

  val traceRepository = new MemoryTraceRepository
  var statsRepository: MemoryMessageRateTimeSeriesRepository = _
  var analyzer: ActorRef = _
  val alertDispatcher: Option[ActorRef] = None

  override def beforeEach() {
    traceRepository.clear()
    statsRepository = new MemoryMessageRateTimeSeriesRepository
    statsRepository = new MemoryMessageRateTimeSeriesRepository
    analyzer = system.actorOf(Props(new MessageRateTimeSeriesAnalyzer(None, statsRepository, traceRepository, alertDispatcher)))
  }

  override def afterEach() {
    awaitStop(analyzer)
    traceRepository.clear()
    statsRepository.clear()
    statsRepository = null
  }

  "An MessageRateTimeSeriesAnalyzer" must {

    "produce message rate time series with points each second" in {
      val scope = Scope(node = Some(DefaultNode))

      val timeRange = TimeRange.minuteRange(System.currentTimeMillis - 10000)
      val startTime = timeRange.startTime

      val expectedPoints = 10
      val statsLatch = new CountDownLatch(1)
      statsRepository.useLatch(statsLatch) { stat ⇒
        stat.timeRange == timeRange && stat.scope == scope && stat.points.size == expectedPoints
      }

      val actorInfo = ActorInfo("akka://MessageRateTimeSeriesAnalyzerSpec/user/actor", Some("dispatcher"), false, false, Set.empty)
      val delay1 = 100
      val events1 = for (i ← 0 to 100) yield {
        event(ActorReceived(actorInfo, "msg" + i)).copy(timestamp = startTime + i * delay1)
      }

      analyzer ! TraceEvents(events1)

      val duration = timeoutHandler.timeoutify(10.seconds)
      statsLatch.await(duration.length, duration.unit) must be(true)

      val stat = statsRepository.findBy(timeRange, scope)
      stat must not be (None)
      stat.get.points.size must be(expectedPoints)
      val rates1 = stat.get.points.head.rates
      rates1.totalMessageRate must be(10.0 plusOrMinus 0.1)
      rates1.receiveRate must be(10.0 plusOrMinus 0.1)

    }
  }

}

