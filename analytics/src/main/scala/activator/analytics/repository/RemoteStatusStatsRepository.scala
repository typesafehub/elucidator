/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.repository

import activator.analytics.data.{ TimeRange, RemoteStatusStats, BasicTypes }

import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ Map ⇒ MutableMap }

trait RemoteStatusStatsRepository {
  def save(stats: Iterable[RemoteStatusStats]): Unit
  def findBy(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Option[RemoteStatusStats]
  def findWithinTimePeriod(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Seq[RemoteStatusStats]
}

case class RemoteStatusStatsScope(node: Option[String], actorSystem: Option[String])

class MemoryRemoteStatusStatsRepository extends BasicMemoryStatsRepository[RemoteStatusStats, RemoteStatusStatsScope] with RemoteStatusStatsRepository {
  override val customizer = new BasicMemoryStatsCustomizer[RemoteStatusStats, RemoteStatusStatsScope] {
    override def scope(stat: RemoteStatusStats): RemoteStatusStatsScope = RemoteStatusStatsScope(stat.node, stat.actorSystem)
    override def timeRange(stat: RemoteStatusStats): TimeRange = stat.timeRange
    override def anonymous(stat: RemoteStatusStats): Boolean = false
  }

  def findBy(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Option[RemoteStatusStats] = {
    findBy(timeRange, RemoteStatusStatsScope(node, actorSystem))
  }

  def findWithinTimePeriod(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Seq[RemoteStatusStats] = {
    findWithinTimePeriod(timeRange, RemoteStatusStatsScope(node, actorSystem))
  }

}

object LocalMemoryRemoteStatusStatsRepository extends MemoryRemoteStatusStatsRepository
