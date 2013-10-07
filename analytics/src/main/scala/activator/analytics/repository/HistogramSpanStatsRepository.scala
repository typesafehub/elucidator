/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.repository

import activator.analytics.data.{ TimeRange, Scope, HistogramSpanStats }

import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ Map ⇒ MutableMap }

trait HistogramSpanStatsRepository {
  def save(stats: Iterable[HistogramSpanStats]): Unit
  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[HistogramSpanStats]
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[HistogramSpanStats]
}

class MemoryHistogramSpanStatsRepository extends BasicMemoryStatsRepository[HistogramSpanStats, SpanScope] with HistogramSpanStatsRepository {

  override val customizer = new BasicStatsCustomizer[HistogramSpanStats]()

  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[HistogramSpanStats] = {
    findBy(timeRange, SpanScope(scope, spanTypeName))
  }
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[HistogramSpanStats] = {
    findWithinTimePeriod(timeRange, SpanScope(scope, spanTypeName))
  }
}

object LocalMemoryHistogramSpanStatsRepository extends MemoryHistogramSpanStatsRepository
