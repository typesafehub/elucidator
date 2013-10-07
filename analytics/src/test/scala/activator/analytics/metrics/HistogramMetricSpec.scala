/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class HistogramMetricSpec extends WordSpec with MustMatchers {

  "Histogram metrics" must {

    "construct bucket equally sized buckets" in {
      val hist = HistogramMetric(20, 3)
      hist.bucketBoundaries must be(IndexedSeq(20, 40, 60))
    }

    "place values in correct buckets" in {
      val hist = HistogramMetric(10, 5)
      hist += 2
      hist += 13
      hist += 20
      hist += 35
      hist += 41
      hist += 55

      hist.buckets must be(IndexedSeq(1, 1, 1, 1, 1, 1))
    }

    "account for sampling factor" in {
      val hist = HistogramMetric(10, 5)
      hist += (2, 1)
      hist += (13, 2)
      hist += (20, 3)
      hist += (35, 4)
      hist += (41, 5)
      hist += (55, 6)

      hist.buckets must be(IndexedSeq(1, 2, 3, 4, 5, 6))
    }

    "increase bucket counts" in {
      val hist = HistogramMetric(10, 5)
      for (i ← 0 until 100) hist += i

      hist.buckets must be(IndexedSeq(10, 10, 10, 10, 10, 50))
    }

    "bea able to redistribute buckets" in {
      val hist = HistogramMetric(10, 5)
      hist += 2
      hist += 7
      hist += 13
      hist += 20
      hist += 25
      hist += 29
      hist += 35
      hist += 35
      hist += 41
      hist += 50
      hist += 51
      hist += 51
      hist += 52

      hist.bucketBoundaries must be(IndexedSeq(10, 20, 30, 40, 50))
      hist.buckets must be(IndexedSeq(2, 1, 3, 2, 1, 4))
      hist.redistribute(hist.bucketBoundaries).buckets must be(hist.buckets)

      // 5x2, 15x1, 25x3, 35x2, 45x1, 50x4
      HistogramMetric.bucketBoundaries(15, 4) must be(IndexedSeq(15, 30, 45, 60))
      hist.redistribute(HistogramMetric.bucketBoundaries(15, 4)).buckets must be(IndexedSeq(2, 4, 2, 5, 0))

      hist.redistribute(IndexedSeq(25, 45)).buckets must be(IndexedSeq(3, 5, 5))
    }

    "add another histogram" in {
      val hist = HistogramMetric(10, 3)
      hist += 2
      hist += 13
      hist += 14
      hist += 100

      val hist2 = HistogramMetric(10, 3)
      hist2 += 1
      hist2 += 12
      hist2 += 35
      hist2 += 103

      hist += hist2

      hist.buckets must be(IndexedSeq(2, 3, 0, 3))
    }

    "add another histogram with different boundaries" in {
      val hist = HistogramMetric(10, 3)
      hist += 2
      hist += 13
      hist += 14
      hist += 100

      val hist2 = HistogramMetric(5, 6)
      hist2 += 1
      hist2 += 7
      hist2 += 25

      hist += hist2

      hist.buckets must be(IndexedSeq(3, 2, 1, 1))
    }

    "construct bucket boundaries from string of comma separated values" in {
      HistogramMetric.bucketBoundaries("20,40, 60") must be(IndexedSeq(20, 40, 60))
    }

    "construct bucket boundaries from several comma separated strings" in {
      HistogramMetric.bucketBoundaries("20,40, 60,100,200,300 ,400") must be(IndexedSeq(20, 40, 60, 100, 200, 300, 400))
    }

    "construct bucket boundaries from string of width times number of buckets" in {
      HistogramMetric.bucketBoundaries("20x4") must be(IndexedSeq(20, 40, 60, 80))
    }

    "construct bucket boundaries from string of mixed definition" in {
      HistogramMetric.bucketBoundaries("20x4, 100, 110, 120, 200x3") must be(IndexedSeq(20, 40, 60, 80, 100, 110, 120, 200, 400, 600))
    }

  }

}
