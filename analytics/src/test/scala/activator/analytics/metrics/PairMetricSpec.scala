/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import com.typesafe.trace.util.Uuid
import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class PairMetricSpec extends WordSpec with MustMatchers {
  import PairMetric.Single

  def createPair(t1: Long = 1, n1: Long = 1, t2: Long = 2, n2: Long = 2) = {
    val uuid = Uuid()
    (Single(uuid, true, t1, n1, 1), Single(uuid, false, t2, n2, 1))
  }

  "Pair metrics" must {

    "record expected first counts" in {
      val m = new PairMetric

      val (a1, a2) = createPair()
      val (b1, b2) = createPair()
      val (c1, c2) = createPair()

      m.add(a1)
      m.add(b2)
      m.add(c1)

      m.count must be(2)

      m.add(a2)
      m.add(b1)
      m.add(c2)

      m.count must be(0)
    }

    "record max expected first count and timestamp" in {
      val m = new PairMetric

      val (a1, a2) = createPair()
      val (b1, b2) = createPair()
      val (c1, c2) = createPair()
      val (d1, d2) = createPair(3, 3, 4, 4) // max
      val (e1, e2) = createPair()

      m.add(a1)
      m.add(b2) // not counted
      m.add(c1)
      m.add(d1) // max at 3 (timestamp = 3)
      m.add(e2)
      m.add(a2)
      m.add(b1)
      m.add(c2)
      m.add(d2)
      m.add(e1)
      m.add(e2)

      m.count must be(0)
      m.maxCount must be(3)
      m.maxCountTimestamp must be(3)
    }

    "record correct durations" in {
      val m = new PairMetric

      val start = 0
      val nanos = System.nanoTime

      val (a1, a2) = createPair(start + 1, nanos + 0, start + 2, nanos + 100)
      val (b1, b2) = createPair(start + 3, nanos + 200, start + 4, nanos + 350)
      val (c1, c2) = createPair(start + 5, nanos + 1000, start + 6, nanos + 1500)
      val (d1, d2) = createPair(start + 7, nanos + 1600, start + 8, nanos + 1650)

      m.add(a1) // only first
      m.count must be(1)
      m.previousDuration must be(0)
      m.previousDurationTimestamp must be(0)
      m.maxDuration must be(0)
      m.maxDurationTimestamp must be(0)

      m.add(a2) // duration matched
      m.count must be(0)
      m.previousDuration must be(100)
      m.previousDurationTimestamp must be(2)
      m.maxDuration must be(100)
      m.maxDurationTimestamp must be(2)

      m.add(b2) // only second - no change
      m.count must be(0)
      m.previousDuration must be(100)
      m.previousDurationTimestamp must be(2)
      m.maxDuration must be(100)
      m.maxDurationTimestamp must be(2)

      m.add(c1) // new first - no matches
      m.count must be(1)
      m.previousDuration must be(100)
      m.previousDurationTimestamp must be(2)
      m.maxDuration must be(100)
      m.maxDurationTimestamp must be(2)

      m.add(c2) // match - new previous duration, new max
      m.count must be(0)
      m.previousDuration must be(500)
      m.previousDurationTimestamp must be(6)
      m.maxDuration must be(500)
      m.maxDurationTimestamp must be(6)

      m.add(b1) // match - no change - outdated duration, not max
      m.count must be(0)
      m.previousDuration must be(500)
      m.previousDurationTimestamp must be(6)
      m.maxDuration must be(500)
      m.maxDurationTimestamp must be(6)

      m.add(d2) // only second - no change
      m.count must be(0)
      m.previousDuration must be(500)
      m.previousDurationTimestamp must be(6)
      m.maxDuration must be(500)
      m.maxDurationTimestamp must be(6)

      m.add(d1) // match - new previous duration, not max
      m.count must be(0)
      m.previousDuration must be(50)
      m.previousDurationTimestamp must be(8)
      m.maxDuration must be(500)
      m.maxDurationTimestamp must be(6)
    }
  }
}
