/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data.{ TimeRange, SpanTimeSeries, Scope }

import scala.collection.mutable.{ Map ⇒ MutableMap }

trait SpanTimeSeriesRepository {
  def save(spans: Iterable[SpanTimeSeries]): Unit
  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[SpanTimeSeries]
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[SpanTimeSeries]
}

class MemorySpanTimeSeriesRepository extends BasicMemoryStatsRepository[SpanTimeSeries, SpanScope] with SpanTimeSeriesRepository {

  override val customizer = new BasicStatsCustomizer[SpanTimeSeries]()

  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[SpanTimeSeries] = {
    findBy(timeRange, SpanScope(scope, spanTypeName))
  }
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[SpanTimeSeries] = {
    findWithinTimePeriod(timeRange, SpanScope(scope, spanTypeName))
  }

}

object LocalMemorySpanTimeSeriesRepository extends MemorySpanTimeSeriesRepository

