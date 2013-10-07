/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.repository

import activator.analytics.data.{ TimeRange, DispatcherTimeSeries, BasicTypes }

trait DispatcherTimeSeriesRepository {
  def save(dispatcherTimeSeries: Iterable[DispatcherTimeSeries]): Unit
  def findBy(timeRange: TimeRange, node: String, actorSystem: String, dispatcher: String): Option[DispatcherTimeSeries]
  def findWithinTimePeriod(timeRange: TimeRange, node: String, actorSystem: String, dispatcher: String): Seq[DispatcherTimeSeries]
  def findLatest(timestamp: BasicTypes.Timestamp, node: String, actorSystem: String, dispatcher: String): Option[DispatcherTimeSeries]
}

case class DispatcherScope(node: String, actorSystem: String, dispatcher: String)

class MemoryDispatcherTimeSeriesRepository extends BasicMemoryStatsRepository[DispatcherTimeSeries, DispatcherScope] with DispatcherTimeSeriesRepository {

  override val customizer = new BasicMemoryStatsCustomizer[DispatcherTimeSeries, DispatcherScope] {
    override def scope(stat: DispatcherTimeSeries): DispatcherScope = DispatcherScope(stat.node, stat.actorSystem, stat.dispatcher)
    override def timeRange(stat: DispatcherTimeSeries): TimeRange = stat.timeRange
    override def anonymous(stat: DispatcherTimeSeries): Boolean = false
  }

  def findBy(timeRange: TimeRange, node: String, actorSystem: String, dispatcher: String): Option[DispatcherTimeSeries] = {
    findBy(timeRange, DispatcherScope(node, actorSystem, dispatcher))
  }

  def findWithinTimePeriod(timeRange: TimeRange, node: String, actorSystem: String, dispatcher: String): Seq[DispatcherTimeSeries] = {
    findWithinTimePeriod(timeRange, DispatcherScope(node, actorSystem, dispatcher))
  }

  def findLatest(timestamp: BasicTypes.Timestamp, node: String, actorSystem: String, dispatcher: String): Option[DispatcherTimeSeries] = {
    val allScopedTimeSeries = withByScopeByTimeRange(_.get(DispatcherScope(node, actorSystem, dispatcher)).toSeq.flatMap(_.values))
    allScopedTimeSeries.filterNot(_.points.isEmpty).sortBy(_.timeRange.startTime).reverse.find(_.timeRange.startTime <= timestamp)
  }
}

object LocalMemoryDispatcherTimeSeriesRepository extends MemoryDispatcherTimeSeriesRepository
