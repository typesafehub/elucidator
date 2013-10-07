/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class RateMetricSpec extends WordSpec with MustMatchers {

  "Rate metrics" must {

    "calculate correct rate" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric
      // 1 msg/sec
      for (i ← 0 until 100) {
        r += startTime + i * 1000
        if (i >= 1) {
          r.rate must be(1.0 plusOrMinus (0.0001))
        } else {
          r.rate must be(0.0 plusOrMinus (0.0001))
        }
      }
    }

    "calculate correct rate with sampling" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric
      val sampled = 3
      // 3 msg/sec
      for (i ← 0 until 100) {
        r += (startTime + i * 1000, sampled)
        if (i >= 1) {
          r.rate must be(3.0 plusOrMinus (0.0001))
        } else {
          r.rate must be(0.0 plusOrMinus (0.0001))
        }
      }
    }

    "calculate rate to 0.0 when empty" in {
      val r = new RateMetric
      r.rate must be(0.0 plusOrMinus (0.0001))
    }

    "calculate rate to 0.0 for one value" in {
      val r = new RateMetric
      r += System.currentTimeMillis
      r.rate must be(0.0 plusOrMinus (0.0001))
    }

    "calculate rate to 0.0 for one value with sampling" in {
      val r = new RateMetric
      val sampled = 3
      r += (System.currentTimeMillis, sampled)
      r.rate must be(0.0 plusOrMinus (0.0001))
    }

    "calculate correct rate for two values" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric
      r += startTime
      r += startTime + 1000

      r.rate must be(1.0 plusOrMinus (0.0001))
    }

    "calculate correct rate for two values with sampling" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric
      val sampled = 3
      r += (startTime, sampled)
      r += (startTime + 1000, sampled)

      r.rate must be(3.0 plusOrMinus (0.0001))
    }

    "calculate correct rate for a few values" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric
      r += startTime
      r += startTime + 10
      r += startTime + 30
      r += startTime + 120
      r += startTime + 350
      r += startTime + 500

      r.rate must be(10.0 plusOrMinus (0.0001))
    }

    "calculate correct rate for low frequency values" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric(maxTimeWindow = 100.seconds)
      r += startTime
      r += startTime + 1000
      r += startTime + 3000
      r += startTime + 12000
      r += startTime + 35000
      r += startTime + 50000

      r.rate must be(0.1 plusOrMinus (0.0001))
    }

    "drop old values when history buffer is full" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric(maxHistory = 6)
      r += startTime - 90
      r += startTime - 30
      r += startTime
      r += startTime + 10
      r += startTime + 30
      r += startTime + 120
      r += startTime + 350
      r += startTime + 500

      r.rate must be(10.0 plusOrMinus (0.0001))
    }

    "discard old values" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric(maxTimeWindow = 5.seconds)
      r += startTime - 3300
      r += startTime - 1100
      r += startTime
      r += startTime + 100
      r += startTime + 300
      r += startTime + 1200
      r += startTime + 3500
      r += startTime + 5000

      r.rate must be(1.0 plusOrMinus (0.0001))
    }

    "track peak rate" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric(maxHistory = 6, minHistoryForPeakRate = 3)
      r += startTime - 5000
      r += startTime - 3000
      r += startTime
      r += startTime + 10
      r += startTime + 30
      r += startTime + 120
      r += startTime + 350
      r += startTime + 500
      r += startTime + 1000
      r += startTime + 2000

      r.peakRate must be(10.0 plusOrMinus (0.0001))
      r.peakRateTimestamp must be(startTime + 250)
    }

    "ignore inital values for peak rate" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric(maxHistory = 100)
      r += startTime - 1000
      r += startTime - 999
      r += startTime - 998
      for (i ← 0 until 1000) r += startTime + i * 10

      // the inital burst would give ~2000, which isn't representative
      r.peakRate must be(100.0 plusOrMinus (20.0))
    }

    "handle burst" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric(maxHistory = 21)
      for (i ← 0 until 20) r += startTime + i * 100
      r.rate must be(10.0 plusOrMinus (0.1))

      val startTime2 = startTime + 20 * 100
      // these happen within same millisecond
      for (i ← 0 until 20) r += startTime2
      // 200.0 = 20 * 1000.0 / 100 (elapsed time is 100ms between the 1st and 21st enqueued msg)q
      r.rate must be((200.0) plusOrMinus (0.1))
    }

    "handle out of order" in {
      val startTime = System.currentTimeMillis
      val r = new RateMetric
      r += startTime
      r += startTime + 10
      r += startTime + 30
      r += startTime + 120
      r += startTime + 350
      r += startTime - 500

      // note that it expect messages to be in order and if not the positive delta is used
      // another, more performance expensive approach would be to sort the events
      r.rate must be(10.0 plusOrMinus (0.1))
    }

  }

}
