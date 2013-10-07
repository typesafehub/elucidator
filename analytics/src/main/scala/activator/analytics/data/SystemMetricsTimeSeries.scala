/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import com.typesafe.atmos.trace.SystemMetrics
import com.typesafe.atmos.uuid.UUID

case class SystemMetricsTimeSeries(
  timeRange: TimeRange,
  node: String,
  points: IndexedSeq[SystemMetricsTimeSeriesPoint] = IndexedSeq.empty,
  id: UUID = new UUID()) extends TimeSeries[SystemMetricsTimeSeriesPoint] {

  override def withPoints(p: IndexedSeq[SystemMetricsTimeSeriesPoint]): SystemMetricsTimeSeries = {
    copy(points = p)
  }
}

object SystemMetricsTimeSeries {
  def concatenate(timeSeries: Iterable[SystemMetricsTimeSeries], timeRange: TimeRange, node: String): SystemMetricsTimeSeries = {
    val allPoints = timeSeries.flatMap(_.points).toIndexedSeq
    SystemMetricsTimeSeries(timeRange, node, allPoints)
  }
}

case class SystemMetricsTimeSeriesPoint(
  timestamp: Long,
  metrics: SystemMetrics) extends TimeSeriesPoint
