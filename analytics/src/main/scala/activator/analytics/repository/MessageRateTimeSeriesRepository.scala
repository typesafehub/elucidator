/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data.{ TimeRange, Scope, MessageRateTimeSeries, BasicTypes }

import scala.collection.mutable.{ Map ⇒ MutableMap }

trait MessageRateTimeSeriesRepository {
  def save(spans: Iterable[MessageRateTimeSeries]): Unit
  def findBy(timeRange: TimeRange, scope: Scope): Option[MessageRateTimeSeries]
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope): Seq[MessageRateTimeSeries]
  def findLatest(timestamp: BasicTypes.Timestamp, scope: Scope): Option[MessageRateTimeSeries]
}

class MemoryMessageRateTimeSeriesRepository extends BasicMemoryStatsRepository[MessageRateTimeSeries, Scope] with MessageRateTimeSeriesRepository {

  override val customizer = new BasicMemoryStatsCustomizer[MessageRateTimeSeries, Scope]() {
    def scope(stat: MessageRateTimeSeries): Scope = stat.scope
    def timeRange(stat: MessageRateTimeSeries): TimeRange = stat.timeRange
    def anonymous(stat: MessageRateTimeSeries): Boolean = stat.scope.anonymous
  }

  def findLatest(timestamp: BasicTypes.Timestamp, scope: Scope): Option[MessageRateTimeSeries] = {
    val allScopedTimeSeries = withByScopeByTimeRange(_.get(scope).toSeq.flatMap(_.values))
    allScopedTimeSeries.filterNot(_.points.isEmpty).sortBy(_.timeRange.startTime).reverse.find(_.timeRange.startTime <= timestamp)
  }
}

object LocalMemoryMessageRateTimeSeriesRepository extends MemoryMessageRateTimeSeriesRepository

