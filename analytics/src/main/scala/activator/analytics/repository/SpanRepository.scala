/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import akka.actor.ActorSystem
import activator.analytics.analyzer.{ AnalyzerBoot }
import activator.analytics.data.{ TimeRange, Span }

import com.typesafe.trace.uuid.UUID
import scala.collection.mutable.{ Map ⇒ MutableMap }

trait SpanRepository {
  def save(spans: Iterable[Span]): Unit
  def findBySpanId(spanId: UUID): Option[Span]
  def findWithinTimePeriod(timeRange: TimeRange, offset: Int = 1, limit: Int = 100): Seq[Span]
  def latest(limit: Int): Iterable[Span]
}

class MemorySpanRepository(maxSpanElements: Int = 3333) extends SpanRepository {

  private var all = List[Span]()
  private val bySpanId = MutableMap[UUID, Span]()

  def save(spans: Iterable[Span]): Unit = synchronized {
    for (each ← spans) {
      all = each :: all
      bySpanId(each.id) = each
    }

    clearOld()
  }

  def findBySpanId(spanId: UUID): Option[Span] = synchronized {
    bySpanId.get(spanId)
  }

  def latest(limit: Int): Iterable[Span] = synchronized {
    val sorted = all.sortBy(_.startTime).reverse
    if (limit > 0 && sorted.size > limit)
      sorted.slice(0, limit)
    else sorted
  }

  def findWithinTimePeriod(timeRange: TimeRange, offset: Int, limit: Int): Seq[Span] = synchronized {
    def isInTimeRange(timestamp: Long): Boolean = {
      timestamp >= timeRange.startTime && timestamp <= timeRange.endTime
    }

    val found = all.filter(x ⇒ isInTimeRange(x.startTime)).toSeq
    val sorted = found.sortBy(_.startTime)
    val page = sorted.slice(offset - 1, offset - 1 + limit)
    page
  }

  private def clearOld() {
    if (all.size > maxSpanElements) {
      val (keep, throwAway) = all.splitAt(maxSpanElements - maxSpanElements / 10)
      all = keep
      for (each ← throwAway) {
        bySpanId.remove(each.id)
      }
    }
  }

  def clear(): Unit = synchronized {
    all = Nil
    bySpanId.clear()
  }
}

object LocalMemorySpanRepository extends MemorySpanRepository

