/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes.{ DurationNanos, Timestamp }
import com.typesafe.atmos.uuid.UUID

/**
 * Current mailbox size and message waiting time in mailbox at a specific point in time.
 * A series of these value can typically be used for graphing values over time.
 */
case class MailboxTimeSeries(
  timeRange: TimeRange,
  path: String,
  points: IndexedSeq[MailboxTimeSeriesPoint] = IndexedSeq.empty,
  id: UUID = new UUID()) extends TimeSeries[MailboxTimeSeriesPoint] {

  override def withPoints(p: IndexedSeq[MailboxTimeSeriesPoint]): MailboxTimeSeries = {
    copy(points = p)
  }
}

object MailboxTimeSeries {
  def concatenate(timeSeries: Iterable[MailboxTimeSeries], timeRange: TimeRange, path: String): MailboxTimeSeries = {
    val allPoints = timeSeries.flatMap(_.points).toIndexedSeq
    MailboxTimeSeries(timeRange, path, allPoints)
  }
}
case class MailboxTimeSeriesPoint(
  timestamp: Timestamp,
  size: Int,
  waitTime: DurationNanos) extends TimeSeriesPoint
