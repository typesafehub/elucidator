/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import com.typesafe.trace.uuid.UUID
import scala.collection.mutable

object PairMetric {
  case class Single(id: UUID, expectedFirst: Boolean, timestamp: Long, nanoTime: Long, sampled: Int)
}

/**
 * Track counts and times for pairs of events that may arrive in any order but must be analyzed
 * in correct order. Both events share a common id (e.g. first.id == second.parent.id).
 * This is typically used for calculating queue lengths and queue wait time, i.e. mailbox
 * metrics.
 */
class PairMetric(var initialValues: Iterable[PairMetric.Single] = Seq.empty) {
  import PairMetric.Single

  private[this] val others = mutable.Map.empty[UUID, Single]

  private[this] var expectedFirstCount: Int = 0
  private[this] var maxExpectedFirstCount: Int = 0
  private[this] var maxExpectedFirstTimestamp: Long = 0L

  private[this] var previousMatchDuration: Long = 0L
  private[this] var previousMatchTimestamp = 0L
  private[this] var maxMatchDuration: Long = 0L
  private[this] var maxMatchDurationTimestamp: Long = 0L

  initialValues foreach add

  // initialValues not used any more, null out for GC
  initialValues = null

  /**
   * Clear all state.
   */
  def reset() {
    others.clear()
    expectedFirstCount = 0
    maxExpectedFirstCount = 0
    maxExpectedFirstTimestamp = 0L
    previousMatchDuration = 0L
    previousMatchTimestamp = 0L
    maxMatchDuration = 0L
    maxMatchDurationTimestamp = 0L
  }

  /**
   * Add an event, checking for a matching pair, otherwise storing these event details.
   */
  def add(single: Single) {
    others.remove(single.id) match {
      case Some(other) ⇒
        if (other.expectedFirst) expectedFirstCount -= other.sampled
        val duration = math.abs(single.nanoTime - other.nanoTime)
        val laterTimestamp = math.max(single.timestamp, other.timestamp)
        if (laterTimestamp > previousMatchTimestamp) {
          previousMatchDuration = duration
          previousMatchTimestamp = laterTimestamp
        }
        if (duration > maxMatchDuration) {
          maxMatchDuration = duration
          maxMatchDurationTimestamp = laterTimestamp
        }
      case None ⇒
        others += single.id -> single
        if (single.expectedFirst) {
          expectedFirstCount += single.sampled
          if (expectedFirstCount > maxExpectedFirstCount) {
            maxExpectedFirstCount = expectedFirstCount
            maxExpectedFirstTimestamp = single.timestamp
          }
        }
    }
  }

  def count: Int = expectedFirstCount

  def maxCount: Int = maxExpectedFirstCount

  def maxCountTimestamp: Long = maxExpectedFirstTimestamp

  def previousDuration: Long = previousMatchDuration

  def previousDurationTimestamp = previousMatchTimestamp

  def maxDuration: Long = maxMatchDuration

  def maxDurationTimestamp: Long = maxMatchDurationTimestamp

  def snapshot: Iterable[Single] = others.values
}
