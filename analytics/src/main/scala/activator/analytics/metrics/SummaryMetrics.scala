/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

/**
 * Simple summary metrics for added values.
 * It is designed for positive values, such as durations.
 */
class SummaryMetrics(initialCount: Long = 0L, initialSum: Long = 0L, initalMin: Long = -1, initalMax: Long = -1) {
  private var _count: Long = initialCount
  def count = _count
  private var _sum: Long = initialSum
  def sum = _sum
  private var _min: Long = initalMin
  /**
   * Minimum value. -1 when no values.
   */
  def min = _min
  private var _max: Long = initalMax
  /**
   * Maximum value. -1 when no values.
   */
  def max = _max

  /**
   * Add a value with specified sampling factor.
   * The sum and count are multiplied with sampling factor.
   */
  def +=(value: Long, sampled: Int = 1): Unit = {
    _count += sampled
    _sum += (value * sampled)
    updateMin(value)
    updateMax(value)
  }

  def +=(other: SummaryMetrics): Unit = {
    _count += other.count
    _sum += other.sum
    updateMin(other.min)
    updateMax(other.max)
  }

  /**
   * Arithmetic mean of all values.
   * -1.0 when no values.
   */
  def mean: Double = _count match {
    case 0 ⇒ -1.0
    case _ ⇒ _sum.toDouble / _count;
  }

  private def updateMax(value: Long): Unit = {
    _max = math.max(_max, value)
  }

  private def updateMin(value: Long): Unit = {
    if (_min < 0L)
      _min = value
    else if (value >= 0L)
      _min = math.min(min, value)
  }

}
