/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.repository

import activator.analytics.data.{ TimeRange, Scope, PercentilesSpanStats }

import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ Map ⇒ MutableMap }

trait PercentilesSpanStatsRepository {
  def save(stats: Iterable[PercentilesSpanStats]): Unit
  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[PercentilesSpanStats]
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[PercentilesSpanStats]
}

class MemoryPercentilesSpanStatsRepository extends BasicMemoryStatsRepository[PercentilesSpanStats, SpanScope] with PercentilesSpanStatsRepository {

  override val customizer = new BasicStatsCustomizer[PercentilesSpanStats]()

  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[PercentilesSpanStats] = {
    findBy(timeRange, SpanScope(scope, spanTypeName))
  }
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[PercentilesSpanStats] = {
    findWithinTimePeriod(timeRange, SpanScope(scope, spanTypeName))
  }
}

object LocalMemoryPercentilesSpanStatsRepository extends MemoryPercentilesSpanStatsRepository
