/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes.Timestamp
import activator.analytics.data.TimeRangeType.AllTime
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.TimeZone
import scala.concurrent.duration._

object TimeRangeType extends Enumeration {
  type TimeRangeType = Value
  val Minutes = Value("minutes")
  val Hours = Value("hours")
  val Days = Value("days")
  val Months = Value("months")
  val AllTime = Value("all")
  val Exact = Value("exact")
}

/**
 * Aggregated statistics are typically grouped by a time period.
 */
case class TimeRange(
  startTime: Timestamp = TimeRange.StartTime,
  endTime: Timestamp = TimeRange.EndTime,
  rangeType: TimeRangeType.Value = AllTime) {

  def duration: Duration = Duration(endTime - startTime, TimeUnit.MILLISECONDS)

  def contains(timestamp: Timestamp): Boolean = {
    startTime <= timestamp && timestamp <= endTime
  }

  def timeRangeValue: String = rangeType.toString
}

object TimeRange {
  import TimeRangeType._

  val StartTime = 0L
  val EndTime = 32503676399999L // 2999-12-31 23:59:59:999
  val UTC = TimeZone.getTimeZone("UTC")

  def rangeFor(pointInTime: Long, rangeType: TimeRangeType): TimeRange = rangeType match {
    case Minutes ⇒ minuteRange(pointInTime)
    case Hours   ⇒ hourRange(pointInTime)
    case Days    ⇒ dayRange(pointInTime)
    case Months  ⇒ monthRange(pointInTime)
    case other   ⇒ TimeRange()
  }

  def exactRange(startTime: Long, endTime: Long): TimeRange = {
    TimeRange(startTime, endTime, Exact)
  }

  def monthRange(startTime: Long, endTime: Long): TimeRange = {
    val start = monthRange(startTime)
    val end = monthRange(endTime)
    TimeRange(start.startTime, end.endTime, Months)
  }

  def monthRange(pointInTime: Long, numberHistoricMonths: Int = 1): TimeRange = {
    val cal = Calendar.getInstance(UTC)
    cal.setTimeInMillis(pointInTime)

    // Back up to historic month start
    clearDays(cal)
    cal.add(Calendar.MONTH, -1 * (numberHistoricMonths - 1))
    val startTime = cal.getTimeInMillis

    // Move forward to end of current month
    cal.add(Calendar.MONTH, numberHistoricMonths)
    cal.add(Calendar.MILLISECOND, -1)
    val endTime = cal.getTimeInMillis

    TimeRange(startTime, endTime, Months)
  }

  def dayRange(startTime: Long, endTime: Long): TimeRange = {
    val start = dayRange(startTime)
    val end = dayRange(endTime)
    TimeRange(start.startTime, end.endTime, Days)
  }

  def dayRange(pointInTime: Long, numberHistoricDays: Int = 1): TimeRange = {
    val cal = Calendar.getInstance(UTC)
    cal.setTimeInMillis(pointInTime)

    // Back up to historic day start
    clearHours(cal)
    cal.add(Calendar.DAY_OF_MONTH, -1 * (numberHistoricDays - 1))
    val startTime = cal.getTimeInMillis

    // Move forward to end of current day
    cal.add(Calendar.DAY_OF_MONTH, numberHistoricDays)
    cal.add(Calendar.MILLISECOND, -1)
    val endTime = cal.getTimeInMillis

    TimeRange(startTime, endTime, Days)
  }

  def hourRange(startTime: Long, endTime: Long): TimeRange = {
    val start = hourRange(startTime)
    val end = hourRange(endTime)
    TimeRange(start.startTime, end.endTime, Hours)
  }

  def hourRange(pointInTime: Long, numberHistoricHours: Int = 1): TimeRange = {
    val cal = Calendar.getInstance(UTC)
    cal.setTimeInMillis(pointInTime)

    // Back up to historic hour start
    clearMinutes(cal)
    cal.add(Calendar.HOUR_OF_DAY, -1 * (numberHistoricHours - 1))
    val startTime = cal.getTimeInMillis

    // Move forward to end of current hour
    cal.add(Calendar.HOUR_OF_DAY, numberHistoricHours)
    cal.add(Calendar.MILLISECOND, -1)
    val endTime = cal.getTimeInMillis

    TimeRange(startTime, endTime, Hours)
  }

  def minuteRange(startTime: Long, endTime: Long): TimeRange = {
    val start = minuteRange(startTime)
    val end = minuteRange(endTime)
    TimeRange(start.startTime, end.endTime, Minutes)
  }

  def minuteRange(pointInTime: Long, numberHistoricMinutes: Int = 1): TimeRange = {
    val cal = Calendar.getInstance(UTC)
    cal.setTimeInMillis(pointInTime)

    // Back up to historic minute start
    clearSeconds(cal)
    cal.add(Calendar.MINUTE, -1 * (numberHistoricMinutes - 1))
    val startTime = cal.getTimeInMillis

    // Move forward to end of current minute
    cal.add(Calendar.MINUTE, numberHistoricMinutes)
    cal.add(Calendar.MILLISECOND, -1)
    val endTime = cal.getTimeInMillis

    TimeRange(startTime, endTime, Minutes)
  }

  private def clearDays(cal: Calendar) {
    cal.set(Calendar.DAY_OF_MONTH, 1)
    clearHours(cal)
  }

  private def clearHours(cal: Calendar) {
    cal.set(Calendar.HOUR_OF_DAY, 0)
    clearMinutes(cal)
  }

  private def clearMinutes(cal: Calendar) {
    cal.set(Calendar.MINUTE, 0)
    clearSeconds(cal)
  }

  private def clearSeconds(cal: Calendar) {
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
  }
}
