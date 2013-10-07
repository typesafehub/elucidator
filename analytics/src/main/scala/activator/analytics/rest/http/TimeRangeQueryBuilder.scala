/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import activator.analytics.data.{ TimeRangeType, TimeRange }
import TimeRangeQueryBuilder._
import TimeRangeType._
import URLDecoder.decode

trait TimeRangeQueryBuilder {

  def extractTime(path: String): Either[String, TimeRange] = {
    decode(path) match {
      case RollingMinutePattern(value)    ⇒ Right(TimeRange.minuteRange(System.currentTimeMillis, value.toInt))
      case RollingHourPattern(value)      ⇒ Right(TimeRange.hourRange(System.currentTimeMillis, value.toInt))
      case RollingDayPattern(value)       ⇒ Right(TimeRange.dayRange(System.currentTimeMillis, value.toInt))
      case RollingMonthPattern(value)     ⇒ Right(TimeRange.monthRange(System.currentTimeMillis, value.toInt))
      case x if x.contains(MinutePattern) ⇒ parseTime(startTime(path), endTime(path), Minutes)
      case x if x.contains(HourPattern)   ⇒ parseTime(startTime(path), endTime(path), Hours)
      case x if x.contains(DayPattern)    ⇒ parseTime(startTime(path), endTime(path), Days)
      case x if x.contains(MonthPattern)  ⇒ parseTime(startTime(path), endTime(path), Months)
      case _                              ⇒ parseTime(startTime(path), endTime(path), Exact)
    }
  }

  private def startTime(path: String): Option[String] = decode(path) match {
    case StartTimePattern(start) ⇒ Some(start)
    case _                       ⇒ None
  }

  private def endTime(path: String): Option[String] = decode(path) match {
    case EndTimePattern(end) ⇒ Some(end)
    case _                   ⇒ None
  }

  def parseTime(fromTime: Option[String], toTime: Option[String], timeRangeType: TimeRangeType): Either[String, TimeRange] = {
    val result = for {
      from ← fromTime
      to ← toTime
    } yield {
      val parsedFrom = TimeParser.parse(decode(from)).getOrElse(-1L)
      val parsedTo = TimeParser.parse(decode(to)).getOrElse(-1L)
      (parsedFrom, parsedTo, timeRangeType) match {
        case (-1L, -1L, _)         ⇒ Left("Unrecognized start and end time parameters: [%s], [%s]".format(from, to))
        case (-1L, _, _)           ⇒ Left("Unrecognized start time parameter: [%s]" format from)
        case (_, -1L, _)           ⇒ Left("Unrecognized end time parameter: [%s]" format to)
        case (start, end, Minutes) ⇒ Right(TimeRange.minuteRange(start, end))
        case (start, end, Hours)   ⇒ Right(TimeRange.hourRange(start, end))
        case (start, end, Days)    ⇒ Right(TimeRange.dayRange(start, end))
        case (start, end, Months)  ⇒ Right(TimeRange.monthRange(start, end))
        case (start, end, Exact)   ⇒ Right(TimeRange.exactRange(start, end))
      }
    }

    result.getOrElse(Right(TimeRange()))
  }
}

object TimeRangeQueryBuilder {
  final val StartTimePattern = """^.*startTime=([0-9T\-\:]*)?.*""".r
  final val EndTimePattern = """^.*endTime=([0-9T\-\:]*)?.*""".r

  final val MonthPattern = "rangeType=month"
  final val DayPattern = "rangeType=day"
  final val HourPattern = "rangeType=hour"
  final val MinutePattern = "rangeType=minute"

  final val RollingMonthPattern = """^.*rolling=([1-9][0-9]?)month[s]?.*""".r
  final val RollingDayPattern = """^.*rolling=([1-9][0-9]?)day[s]?.*""".r
  final val RollingHourPattern = """^.*rolling=([1-9][0-9]?)hour[s]?.*""".r
  final val RollingMinutePattern = """^.*rolling=([1-9][0-9]?)minute[s]?.*""".r
}

