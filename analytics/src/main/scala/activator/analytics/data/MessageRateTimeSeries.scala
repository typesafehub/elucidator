/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes.Timestamp
import com.typesafe.atmos.uuid.UUID

/**
 * Current messages per second at a specific point in time, measured
 * over a short time window.
 * A series of these value can typically be used for graphing values
 * over time.
 */
case class MessageRateTimeSeries(
  timeRange: TimeRange,
  scope: Scope,
  points: IndexedSeq[MessageRateTimeSeriesPoint] = IndexedSeq.empty,
  id: UUID = new UUID()) extends TimeSeries[MessageRateTimeSeriesPoint] {

  override def withPoints(p: IndexedSeq[MessageRateTimeSeriesPoint]): MessageRateTimeSeries = {
    copy(points = p)
  }
}

object MessageRateTimeSeries {
  def concatenate(timeSeries: Iterable[MessageRateTimeSeries], timeRange: TimeRange, scope: Scope): MessageRateTimeSeries = {
    val allPoints = timeSeries.flatMap(_.points).toIndexedSeq
    MessageRateTimeSeries(timeRange, scope, allPoints)
  }
}

case class MessageRateTimeSeriesPoint(
  timestamp: Timestamp,
  rates: MessageRateMetrics) extends TimeSeriesPoint
