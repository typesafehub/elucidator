/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import activator.analytics.data.Sorting._
import com.typesafe.trace.uuid.UUID

trait PlayRequestSummaryRepository {
  def save(summary: PlayRequestSummary): Unit
  def find(trace: UUID): Option[PlayRequestSummary]
  def findRequestsWithinTimePeriod(
    startTime: Long,
    endTime: Long,
    offset: Int = 0,
    limit: Int = 100,
    sortOn: PlayStatsSort,
    sorting: SortDirection): Seq[PlayRequestSummary]
  def purgeOld(): Unit
}

class MemoryPlayRequestSummaryRepository(maxAge: Long) extends PlayRequestSummaryRepository {
  import java.util.concurrent.ConcurrentHashMap
  import scala.collection.JavaConversions._

  case class Value(summary: PlayRequestSummary, timestamp: Long = System.currentTimeMillis)

  private val internalMap = new ConcurrentHashMap[UUID, Value]

  def save(summary: PlayRequestSummary): Unit =
    internalMap.put(summary.traceId, Value(summary))

  def find(trace: UUID): Option[PlayRequestSummary] =
    Option(internalMap.get(trace)).map(_.summary)

  def findRequestsWithinTimePeriod(startTime: Long, endTime: Long, offset: Int, limit: Int, sortOn: PlayStatsSort, sorting: SortDirection): Seq[PlayRequestSummary] = {
    def isInTimeRange(timestamp: Long): Boolean = timestamp >= startTime && timestamp <= endTime
    val descending = sortDescending(internalMap.values.filter(x ⇒ isInTimeRange(x.summary.start.millis)).map(_.summary).toSeq, sortOn)
    val sorted =
      if (sorting == descendingSort) descending
      else descending.reverse
    sorted.slice(offset, offset + limit)
  }

  def clear(): Unit = internalMap.clear()

  def purgeOld(): Unit = {
    val top = System.currentTimeMillis - maxAge
    internalMap.entrySet.filter(e ⇒ e.getValue.timestamp <= top).foreach(e ⇒ internalMap.remove(e.getKey, e.getValue))
  }

  def sortDescending(found: Seq[PlayRequestSummary], sortOn: PlayStatsSort): Seq[PlayRequestSummary] = {
    def totalTimeMillis(prs: PlayRequestSummary): Long = (prs.end.nanoTime - prs.start.nanoTime) * 1000 * 1000
    sortOn match {
      case PlayStatsSorts.TimeSort ⇒
        found.sortWith((a, b) ⇒
          if (a.start.millis == b.start.millis) a.start.nanoTime > b.start.nanoTime
          else a.start.millis > b.start.millis)
      case PlayStatsSorts.ControllerSort ⇒
        found.sortWith((a, b) ⇒ a.invocationInfo.controller > b.invocationInfo.controller)
      case PlayStatsSorts.MethodSort ⇒
        found.sortWith((a, b) ⇒ a.invocationInfo.httpMethod > b.invocationInfo.httpMethod)
      case PlayStatsSorts.ResponseCodeSort ⇒
        found.sortWith((a, b) ⇒ a.response.resultInfo.httpResponseCode > b.response.resultInfo.httpResponseCode)
      case PlayStatsSorts.InvocationTimeSort ⇒
        found.sortWith((a, b) ⇒ totalTimeMillis(a) > totalTimeMillis(b))
    }
  }
}

object LocalMemoryPlayRequestSummaryRepository extends MemoryPlayRequestSummaryRepository(20 * 60 * 1000)
