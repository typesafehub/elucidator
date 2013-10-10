/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data.{ TimeRange, SystemMetricsTimeSeries, BasicTypes }
import com.typesafe.trace.util.ExpectedFailureException

/**
 * Used to simulate exception from repository
 */
class SpecialSystemMetricsTimeSeriesRepository extends BasicMemoryStatsRepository[SystemMetricsTimeSeries, SystemMetricsScope] with SystemMetricsTimeSeriesRepository {
  override val customizer = new BasicMemoryStatsCustomizer[SystemMetricsTimeSeries, SystemMetricsScope] {
    override def scope(stat: SystemMetricsTimeSeries): SystemMetricsScope = SystemMetricsScope(stat.node)
    override def timeRange(stat: SystemMetricsTimeSeries): TimeRange = stat.timeRange
    override def anonymous(stat: SystemMetricsTimeSeries): Boolean = false
  }

  var throwException = false

  override def save(dispatcherTimeSeries: Iterable[SystemMetricsTimeSeries]) {
    if (throwException) throw new ExpectedFailureException("Simulated exception")
    super.save(dispatcherTimeSeries)
  }

  def throwExceptionDuringSave(value: Boolean) = {
    throwException = value
  }

  def findBy(timeRange: TimeRange, node: String): Option[SystemMetricsTimeSeries] = findBy(timeRange, SystemMetricsScope(node))

  def findWithinTimePeriod(timeRange: TimeRange, node: String): Seq[SystemMetricsTimeSeries] = findWithinTimePeriod(timeRange, SystemMetricsScope(node))

  override def findLatest(timestamp: BasicTypes.Timestamp, node: String) = null
}
