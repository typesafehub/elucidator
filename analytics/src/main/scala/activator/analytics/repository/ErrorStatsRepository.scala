/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.repository

import activator.analytics.data.{ TimeRange, ErrorStats, BasicTypes }

trait ErrorStatsRepository {
  def save(errors: Iterable[ErrorStats]): Unit
  def findBy(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Option[ErrorStats]
  def findWithinTimePeriod(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Seq[ErrorStats]
  def findLatest(timestamp: BasicTypes.Timestamp, node: Option[String], actorSystem: Option[String]): Option[ErrorStats]
}

case class ErrorStatsScope(node: Option[String], actorSystem: Option[String])

class MemoryErrorStatsRepository extends BasicMemoryStatsRepository[ErrorStats, ErrorStatsScope] with ErrorStatsRepository {

  override val customizer = new BasicMemoryStatsCustomizer[ErrorStats, ErrorStatsScope] {
    override def scope(stat: ErrorStats): ErrorStatsScope = ErrorStatsScope(stat.node, stat.actorSystem)
    override def timeRange(stat: ErrorStats): TimeRange = stat.timeRange
    override def anonymous(stat: ErrorStats): Boolean = false
  }

  def findBy(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Option[ErrorStats] = {
    findBy(timeRange, ErrorStatsScope(node, actorSystem))
  }

  def findWithinTimePeriod(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Seq[ErrorStats] = {
    findWithinTimePeriod(timeRange, ErrorStatsScope(node, actorSystem))
  }

  def findLatest(timestamp: BasicTypes.Timestamp, node: Option[String], actorSystem: Option[String]): Option[ErrorStats] = {
    val allScopedTimeSeries = withByScopeByTimeRange(_.get(ErrorStatsScope(node, actorSystem)).toSeq.flatMap(_.values))
    allScopedTimeSeries.sortBy(_.timeRange.startTime).reverse.find(_.timeRange.startTime <= timestamp)
  }
}

object LocalMemoryErrorStatsRepository extends MemoryErrorStatsRepository
