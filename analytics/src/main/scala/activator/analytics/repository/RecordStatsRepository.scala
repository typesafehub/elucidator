/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.repository

import activator.analytics.data.{ TimeRange, RecordStats, BasicTypes }

trait RecordStatsRepository {
  def save(records: Iterable[RecordStats]): Unit
  def findBy(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Option[RecordStats]
  def findWithinTimePeriod(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Seq[RecordStats]
  def findLatest(timestamp: BasicTypes.Timestamp, node: Option[String], actorSystem: Option[String]): Option[RecordStats]
}

case class RecordStatsScope(node: Option[String], actorSystem: Option[String])

class MemoryRecordStatsRepository extends BasicMemoryStatsRepository[RecordStats, RecordStatsScope] with RecordStatsRepository {

  override val customizer = new BasicMemoryStatsCustomizer[RecordStats, RecordStatsScope] {
    override def scope(stat: RecordStats): RecordStatsScope = RecordStatsScope(stat.node, stat.actorSystem)
    override def timeRange(stat: RecordStats): TimeRange = stat.timeRange
    override def anonymous(stat: RecordStats): Boolean = false
  }

  def findBy(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Option[RecordStats] = {
    findBy(timeRange, RecordStatsScope(node, actorSystem))
  }

  def findWithinTimePeriod(timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): Seq[RecordStats] = {
    findWithinTimePeriod(timeRange, RecordStatsScope(node, actorSystem))
  }

  def findLatest(timestamp: BasicTypes.Timestamp, node: Option[String], actorSystem: Option[String]): Option[RecordStats] = {
    val allScopedTimeSeries = withByScopeByTimeRange(_.get(RecordStatsScope(node, actorSystem)).toSeq.flatMap(_.values))
    allScopedTimeSeries.sortBy(_.timeRange.startTime).reverse.find(_.timeRange.startTime <= timestamp)
  }
}

object LocalMemoryRecordStatsRepository extends MemoryRecordStatsRepository
