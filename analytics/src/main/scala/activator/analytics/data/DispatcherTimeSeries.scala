/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes.Timestamp
import com.typesafe.trace.DispatcherMetrics
import com.typesafe.trace.uuid.UUID

case class DispatcherTimeSeries(
  timeRange: TimeRange,
  node: String,
  actorSystem: String,
  dispatcher: String,
  dispatcherType: String,
  points: IndexedSeq[DispatcherTimeSeriesPoint] = IndexedSeq.empty,
  id: UUID = new UUID()) extends TimeSeries[DispatcherTimeSeriesPoint] {

  override def withPoints(p: IndexedSeq[DispatcherTimeSeriesPoint]): DispatcherTimeSeries = {
    copy(points = p)
  }
}

object DispatcherTimeSeries {
  def concatenate(timeSeries: Iterable[DispatcherTimeSeries], timeRange: TimeRange, node: String, actorSystem: String, dispatcher: String): DispatcherTimeSeries = {
    val dispatcherType = if (timeSeries.isEmpty) "" else timeSeries.head.dispatcherType
    val allPoints = timeSeries.flatMap(_.points).toIndexedSeq
    DispatcherTimeSeries(timeRange, node, actorSystem, dispatcher, dispatcherType, allPoints)
  }
}

case class DispatcherTimeSeriesPoint(
  timestamp: Timestamp,
  metrics: DispatcherMetrics) extends TimeSeriesPoint
