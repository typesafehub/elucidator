/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import akka.actor.ActorPath
import activator.analytics.data.{ TimeRange, MailboxTimeSeries, BasicTypes }

import scala.collection.mutable.{ Map ⇒ MutableMap }

trait MailboxTimeSeriesRepository {
  def save(spans: Iterable[MailboxTimeSeries]): Unit
  def findBy(timeRange: TimeRange, actorPath: String): Option[MailboxTimeSeries]
  def findWithinTimePeriod(timeRange: TimeRange, actorPath: String): Seq[MailboxTimeSeries]
  def findLatest(timestamp: BasicTypes.Timestamp, actorPath: String): Option[MailboxTimeSeries]
}

class MemoryMailboxTimeSeriesRepository extends BasicMemoryStatsRepository[MailboxTimeSeries, String] with MailboxTimeSeriesRepository {

  override val customizer = new BasicMemoryStatsCustomizer[MailboxTimeSeries, String] {
    override def scope(stat: MailboxTimeSeries): String = stat.path
    override def timeRange(stat: MailboxTimeSeries): TimeRange = stat.timeRange
    override def anonymous(stat: MailboxTimeSeries): Boolean = ActorPath.fromString(stat.path).name.startsWith("$")
  }

  def findLatest(timestamp: BasicTypes.Timestamp, actorPath: String): Option[MailboxTimeSeries] = {
    val allScopedTimeSeries = withByScopeByTimeRange(_.get(actorPath).toSeq.flatMap(_.values))
    allScopedTimeSeries.filterNot(_.points.isEmpty).sortBy(_.timeRange.startTime).reverse.find(_.timeRange.startTime <= timestamp)
  }
}

object LocalMemoryMailboxTimeSeriesRepository extends MemoryMailboxTimeSeriesRepository
