/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import activator.analytics.metrics.RateMetric._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.math.abs

/**
 *  Measures mean throughput over a history of timestamped events.
 *  Also tracks peak rate, and when it occurred.
 */
class RateMetric(
  var initialHistory: Iterable[(Long, Int)] = Seq.empty,
  initialPeakRate: Double = 0.0,
  initialPeakRateTimestamp: Long = 0L,
  maxHistory: Int = DefaultMaxHistory,
  maxTimeWindow: Duration = DefaultMaxTimeWindow,
  minHistoryForPeakRate: Int = DefaultMinHistoryForPeakRate,
  calculatePeakRate: Boolean = true) {

  private val maxTimeWindowMillis = maxTimeWindow.toMillis
  // history of (timestamp, sampled)
  private val history = mutable.Queue[(Long, Int)]()
  // sum of number of entries in history, with sampling taken into account
  // it's too slow to sum this by iterating over the values
  private var historyCount = 0
  private var peak = initialPeakRate
  def peakRate: Double = peak
  private var peakTimestamp = initialPeakRateTimestamp
  def peakRateTimestamp: Long = peakTimestamp

  override def toString: String = {
    val lts = try {
      lastTimestamp.toString
    } catch {
      case _: Exception ⇒ "<none>"
    }
    "RateMetric(peakRate=" + peakRate + ", peakRateTimestamp=" + peakRateTimestamp + ", rate=" + rate + ", lastTimestamp=" + lts + ", historyCount=" + historyCount + ", history=" + history + ")"
  }

  for ((timestamp, sampled) ← initialHistory) {
    addToHistory(timestamp, sampled)
  }
  // initialHistory not used any more, null for GC
  initialHistory = null

  /**
   * Add a sample that occurred at the specified timestamp.
   * When calculating the rate the counts are multiplied with the sampling factor.
   */
  def +=(timestamp: Long, sampled: Int = 1) {
    addToHistory(timestamp, sampled)
    dropMaxHistory()
    dropOld()
    if (calculatePeakRate) updatePeakRate()
  }

  /**
   * Add to history window without updating peak and dropping old, i.e. fast
   */
  private def addToHistory(timestamp: Long, sampled: Int) {
    val entry = (timestamp, sampled)
    history += entry
    increaseHistoryCount(sampled)
  }

  def historyWindow: Iterable[(Long, Int)] = history

  private def increaseHistoryCount(sampled: Int) {
    if (history.size <= 1) {
      historyCount += 1
    } else {
      historyCount += sampled
    }
  }

  private def decreaseHistoryCount(sampled: Int) {
    if (history.isEmpty) historyCount -= 1
    else historyCount -= sampled
  }

  private def dropMaxHistory() {
    if (history.size > maxHistory) {
      val dropped = history.dequeue
      decreaseHistoryCount(dropped._2)
    }
  }

  private def dropOld() {
    if (maxTimeWindowMillis > 0 && history.nonEmpty && elapsedMillis > maxTimeWindowMillis) {
      val dropped = history.dequeue
      decreaseHistoryCount(dropped._2)
      dropOld()
    }
  }

  private def updatePeakRate() {
    if (history.size >= minHistoryForPeakRate) {
      val r = rate
      if (r > peak) {
        peak = r
        peakTimestamp = history.head._1 + elapsedMillis / 2
      }
    }
  }

  /**
   * The rate in events/second, calculated over the history window defined
   * by the maxHistory size and maxTimeWindow duration.
   */
  def rate: Double = RateMetric.rate(historyCount, elapsedMillis)

  private def elapsedMillis = {
    if (history.isEmpty) 1L
    else RateMetric.elapsedMillis(history.head._1, history.last._1)
  }

  def lastTimestamp: Long = history.last._1

}

object RateMetric {
  final val DefaultMaxHistory = 1000
  final val SmallMaxHistory = 100
  final val DefaultMaxTimeWindow = 10.seconds
  final val DefaultMinHistoryForPeakRate = 10
  final val MinElapsedTimeForPeakCalculationMillis = 100L
  final val MinElapsedTimeForCalculationMillis = 10L

  /**
   * Expose the actual calculation so that we do it in the same way everywhere
   */
  def rate(numberOfEvents: Long, startTime: Long, endTime: Long): Double = rate(numberOfEvents, elapsedMillis(startTime, endTime))

  /**
   * Expose the actual calculation so that we do it in the same way everywhere
   */
  def rate(numberOfEvents: Long, elapsedMillis: Long): Double = {
    val count = numberOfEvents - 1
    if (count <= 0 || elapsedMillis < MinElapsedTimeForCalculationMillis) 0.0
    else count * 1000.0 / elapsedMillis
  }

  private def elapsedMillis(startTime: Long, endTime: Long) = {
    val delta = abs(endTime - startTime)
    if (delta < 1) 1L
    else delta
  }
}
