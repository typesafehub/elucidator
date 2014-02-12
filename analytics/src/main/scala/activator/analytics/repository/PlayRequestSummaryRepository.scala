/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import activator.analytics.data.Sorting._
import com.typesafe.trace.uuid.UUID
import scala.collection.SeqView

trait PlayRequestSummaryRepository {
  def save(summary: PlayRequestSummary): Unit
  def find(trace: UUID): Option[PlayRequestSummary]
  def findRequestsWithinTimePeriod(
    startTime: Long,
    endTime: Long,
    offset: Int = 0,
    limit: Int = 100,
    sortOn: PlayStatsSort[_],
    sorting: SortDirection): Seq[PlayRequestSummary]
  def purgeOld(): Unit
}

class MemoryPlayRequestSummaryRepository(maxAge: Long) extends PlayRequestSummaryRepository with RepositoryLifecycle {
  import java.util.concurrent.ConcurrentHashMap
  import scala.collection.JavaConversions._

  LocalRepositoryLifecycleHandler.register(this)

  case class Value(summary: PlayRequestSummary, timestamp: Long = System.currentTimeMillis)

  private val internalMap = new ConcurrentHashMap[UUID, Value]

  def save(summary: PlayRequestSummary): Unit =
    internalMap.put(summary.traceId, Value(summary))

  def find(trace: UUID): Option[PlayRequestSummary] =
    Option(internalMap.get(trace)).map(_.summary)

  def findRequestsWithinTimePeriod(startTime: Long, endTime: Long, offset: Int, limit: Int, sortOn: PlayStatsSort[_], sorting: SortDirection): Seq[PlayRequestSummary] = {
    def isInTimeRange(timestamp: Long): Boolean = timestamp >= startTime && timestamp <= endTime
    val doSort = if (sorting == descendingSort) sortOn.dec(_) else sortOn.asc(_)
    val filtered = internalMap.values.toSeq.view.filter(x ⇒ isInTimeRange(x.summary.start.millis)).map(_.summary)
    doSort(filtered).slice(offset, offset + limit)
  }

  def clear(): Unit = internalMap.clear()

  def purgeOld(): Unit = {
    val top = System.currentTimeMillis - maxAge
    internalMap.entrySet.filter(e ⇒ e.getValue.timestamp <= top).foreach(e ⇒ internalMap.remove(e.getKey, e.getValue))
  }
}

object LocalMemoryPlayRequestSummaryRepository extends MemoryPlayRequestSummaryRepository(20 * 60 * 1000)
