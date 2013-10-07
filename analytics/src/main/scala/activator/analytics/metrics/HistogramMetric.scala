/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import activator.analytics.metrics.HistogramMetric._
import scala.annotation.tailrec

/**
 * Histogram is number of samples that fall into each interval (bucket).
 * A value fall into a bucket when the value is less than the corresponding bucket
 * boundary and greater than or equal to the previous bucket boundary. The last
 * bucket contains all values greater than or equal to the last bucket boundary.
 * The first bucket contains all values less than the first bucket boundary.
 * This means that there is always one more bucket than the number of
 * elements in the bucketBoundaries.
 */
class HistogramMetric(val bucketBoundaries: IndexedSeq[Long]) {

  private val bucketCounts = Array.fill(bucketBoundaries.size + 1)(0L)
  private val lastBucketIndex = bucketCounts.size - 1

  def this(bucketBoundaries: IndexedSeq[Long], initalBuckets: IndexedSeq[Long]) {
    this(bucketBoundaries)
    for (i ← 0 to lastBucketIndex) {
      bucketCounts(i) += initalBuckets(i)
    }
  }

  def +=(value: Long, count: Long = 1L) {
    @tailrec
    def addToBucket(i: Int) {
      if (i == lastBucketIndex) {
        bucketCounts(i) += count
      } else if (value < bucketBoundaries(i)) {
        bucketCounts(i) += count
      } else {
        addToBucket(i + 1)
      }
    }

    addToBucket(0)
  }

  def +=(other: HistogramMetric) {
    val otherBuckets = other.redistribute(bucketBoundaries).buckets
    for (i ← 0 to lastBucketIndex) {
      bucketCounts(i) += otherBuckets(i)
    }
  }

  def buckets: IndexedSeq[Long] = bucketCounts.toIndexedSeq

  def redistribute(bucketWidth: Long, numberOfBuckets: Int): HistogramMetric = {
    redistribute(HistogramMetric.bucketBoundaries(bucketWidth, numberOfBuckets))
  }

  def redistribute(newBucketBoundaries: IndexedSeq[Long]): HistogramMetric = {
    if (bucketBoundaries == newBucketBoundaries) {
      this
    } else {
      val averageValues = for (i ← 0 to lastBucketIndex) yield {
        i match {
          case 0                 ⇒ bucketBoundaries(i) / 2
          case `lastBucketIndex` ⇒ bucketBoundaries(i - 1)
          case _                 ⇒ (bucketBoundaries(i) + bucketBoundaries(i - 1)) / 2
        }
      }
      val result = new HistogramMetric(newBucketBoundaries)
      for (i ← 0 to lastBucketIndex) {
        result += (averageValues(i), bucketCounts(i))
      }
      result
    }
  }
}

object HistogramMetric {
  def apply(bucketWidth: Long, numberOfBuckets: Int): HistogramMetric = {
    new HistogramMetric(bucketBoundaries(bucketWidth, numberOfBuckets))
  }

  def bucketBoundaries(bucketWidth: Long, numberOfBuckets: Int): IndexedSeq[Long] = {
    var bucketBoundaries = Vector[Long]()
    for (n ← 1 to numberOfBuckets) {
      bucketBoundaries = bucketBoundaries :+ n * bucketWidth
    }
    bucketBoundaries
  }

  def bucketBoundaries(definition: String): IndexedSeq[Long] = {
    val sections =
      for {
        value ← definition.split(',')
      } yield {
        if (value.indexOf('x') == -1) {
          IndexedSeq(value.trim.toLong)
        } else {
          val x = value.split('x')
          bucketBoundaries(x(0).trim.toLong, x(1).trim.toInt)
        }
      }

    sections.flatten
  }
}
