/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import scala.math.floor

/**
 * Simple summary metrics for added values.
 * It is designed for positive values, such as durations.
 */
class PercentilesMetrics(sample: Sample) {

  /**
   * Add a value with specified sampling factor.
   */
  def +=(value: Long, sampled: Int = 1): Unit = {
    for (n ← 1 to sampled) {
      sample.update(value)
    }
  }

  def count = sample.size

  /**
   * Returns values at the given percentiles.
   */
  def percentiles(percentiles: Seq[Double]): Seq[Double] = {
    val scores = new Array[Double](percentiles.length)
    for (i ← 0 until scores.size) scores(i) = -1L

    if (sample.size > 0) {
      val values = sample.values.sorted
      for (i ← (0 until percentiles.length)) {
        val p = percentiles(i)
        val pos = p * (values.size + 1)
        pos match {
          case n if n < 1            ⇒ scores(i) = values(0)
          case n if n >= values.size ⇒ scores(i) = values.last
          case n ⇒
            val lower = values(n.toInt - 1)
            val upper = values(n.toInt)
            scores(i) = lower + (pos - floor(pos)) * (upper - lower)
        }
      }
    }

    return scores.toIndexedSeq
  }

}
