/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import com.typesafe.atmos.uuid.UUID

trait PlayRequestSummaryRepository {
  def save(summary: PlayRequestSummary): Unit
  def find(trace: UUID): Option[PlayRequestSummary]
  def findRequestsWithinTimePeriod(startTime: Long, endTime: Long, offset: Int = 1, limit: Int = 100): Seq[PlayRequestSummary]
  def findLatestRequestsWithinTimePeriod(startTime: Long, endTime: Long, limit: Int = 100): Seq[PlayRequestSummary]
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

  def findRequestsWithinTimePeriod(startTime: Long, endTime: Long, offset: Int, limit: Int): Seq[PlayRequestSummary] = {
    def isInTimeRange(timestamp: Long): Boolean = {
      timestamp >= startTime && timestamp <= endTime
    }

    val found = internalMap.values.filter(x ⇒ isInTimeRange(x.summary.start.millis)).map(_.summary).toSeq
    val sorted = PlayRequestSummary.sortByTime(found)
    sorted.slice(offset - 1, offset - 1 + limit)
  }

  def findLatestRequestsWithinTimePeriod(startTime: Long, endTime: Long, limit: Int = 100): Seq[PlayRequestSummary] = {
    def isInTimeRange(timestamp: Long): Boolean = {
      timestamp >= startTime && timestamp <= endTime
    }

    val found = internalMap.values.filter(x ⇒ isInTimeRange(x.summary.start.millis)).map(_.summary).toSeq
    val sorted = PlayRequestSummary.sortByTime(found)
    sorted.slice(sorted.length - limit - 1, sorted.length - 1).reverse
  }

  def clear(): Unit = internalMap.clear()

  def purgeOld(): Unit = {
    val top = System.currentTimeMillis - maxAge
    internalMap.entrySet.filter(e ⇒ e.getValue.timestamp <= top).foreach(e ⇒ internalMap.remove(e.getKey, e.getValue))
  }
}

object LocalMemoryPlayRequestSummaryRepository extends MemoryPlayRequestSummaryRepository(20 * 60 * 1000)
