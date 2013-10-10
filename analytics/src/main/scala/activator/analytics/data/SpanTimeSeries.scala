/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes.{ DurationNanos, Timestamp }
import com.typesafe.trace.uuid.UUID

/**
 * All, or sampled, durations of the spans that belong to the scope and
 * span type. A point holds the timestamp, duration and sampling factor.
 * This information is typically used for producing time series
 * scatter plot of latencies.
 */
case class SpanTimeSeries(
  timeRange: TimeRange,
  scope: Scope,
  spanTypeName: String,
  points: IndexedSeq[SpanTimeSeriesPoint] = IndexedSeq.empty,
  id: UUID = new UUID()) extends BasicStats with TimeSeries[SpanTimeSeriesPoint] {

  override def withPoints(p: IndexedSeq[SpanTimeSeriesPoint]): SpanTimeSeries = {
    copy(points = p)
  }
}

object SpanTimeSeries {
  def concatenate(timeSeries: Iterable[SpanTimeSeries], timeRange: TimeRange, scope: Scope, spanTypeName: String): SpanTimeSeries = {
    val allPoints = timeSeries.flatMap(_.points).toIndexedSeq
    SpanTimeSeries(timeRange, scope, spanTypeName, allPoints)
  }
}

case class SpanTimeSeriesPoint(
  timestamp: Timestamp,
  duration: DurationNanos,
  sampled: Int = 1) extends TimeSeriesPoint

