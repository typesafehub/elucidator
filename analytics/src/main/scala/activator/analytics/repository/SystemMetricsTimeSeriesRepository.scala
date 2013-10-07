/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.repository

import activator.analytics.data.{ TimeRange, SystemMetricsTimeSeries, BasicTypes }

trait SystemMetricsTimeSeriesRepository {
  def save(dispatcherTimeSeries: Iterable[SystemMetricsTimeSeries]): Unit
  def findBy(timeRange: TimeRange, node: String): Option[SystemMetricsTimeSeries]
  def findWithinTimePeriod(timeRange: TimeRange, node: String): Seq[SystemMetricsTimeSeries]
  def findLatest(timestamp: BasicTypes.Timestamp, node: String): Option[SystemMetricsTimeSeries]
}

case class SystemMetricsScope(node: String)

class MemorySystemMetricsTimeSeriesRepository extends BasicMemoryStatsRepository[SystemMetricsTimeSeries, SystemMetricsScope] with SystemMetricsTimeSeriesRepository {
  override val customizer = new BasicMemoryStatsCustomizer[SystemMetricsTimeSeries, SystemMetricsScope] {
    override def scope(stat: SystemMetricsTimeSeries): SystemMetricsScope = SystemMetricsScope(stat.node)
    override def timeRange(stat: SystemMetricsTimeSeries): TimeRange = stat.timeRange
    override def anonymous(stat: SystemMetricsTimeSeries): Boolean = false
  }

  def findBy(timeRange: TimeRange, node: String): Option[SystemMetricsTimeSeries] = {
    findBy(timeRange, SystemMetricsScope(node))
  }

  def findWithinTimePeriod(timeRange: TimeRange, node: String): Seq[SystemMetricsTimeSeries] = {
    findWithinTimePeriod(timeRange, SystemMetricsScope(node))
  }

  def findLatest(timestamp: BasicTypes.Timestamp, node: String): Option[SystemMetricsTimeSeries] = {
    val allScopedTimeSeries: Seq[SystemMetricsTimeSeries] = withByScopeByTimeRange(_.get(SystemMetricsScope(node)).toSeq.flatMap(_.values))
    allScopedTimeSeries.filterNot(_.points.isEmpty).sortBy(_.timeRange.startTime).reverse.find(_.timeRange.startTime <= timestamp)
  }
}

object LocalMemorySystemMetricsRepository extends MemorySystemMetricsTimeSeriesRepository
