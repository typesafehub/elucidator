/**
 * Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.data

import com.typesafe.atmos.uuid.UUID
import scala.collection.immutable.Map
import scala.collection.immutable.Set

case class RecordStats(
  timeRange: TimeRange,
  node: Option[String] = None,
  actorSystem: Option[String] = None,
  metrics: RecordStatsMetrics = new RecordStatsMetrics(),
  id: UUID = new UUID())

object RecordStats extends Chunker[RecordStats] {
  def concatenate(stats: Iterable[RecordStats], timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): RecordStats = {
    val metrics = stats.map(_.metrics).foldLeft(RecordStatsMetrics()) {
      _ concatenate _
    }
    RecordStats(timeRange, node, actorSystem, metrics)
  }

  def chunk(minNumberOfChunks: Int,
            maxNumberOfChunks: Int,
            stats: Iterable[RecordStats],
            timeRange: TimeRange,
            node: Option[String],
            actorSystem: Option[String]): Seq[RecordStats] = {

    chunk(minNumberOfChunks, maxNumberOfChunks, stats, timeRange,
      s ⇒ s.timeRange,
      t ⇒ RecordStats(t, node),
      (t, s) ⇒ RecordStats.concatenate(s, t, node, actorSystem))
  }
}

case class RecordStatsMetrics(groups: Map[String, Group] = Map.empty) {
  def concatenate(other: RecordStatsMetrics): RecordStatsMetrics = {
    // if there is a group with the same name concatenate it else just concatenate with an empty group
    val updatedGroups = groups ++ other.groups.map { case (k, v) ⇒ k -> v.concatenate(groups.getOrElse(k, Group())) }
    RecordStatsMetrics(groups = updatedGroups)
  }
}

case class Group(counter: Int = 0, members: Map[String, Int] = Map.empty) {
  def concatenate(other: Group): Group = {
    val concatenatedMembers = members ++ other.members.map { case (k, v) ⇒ k -> (v + members.getOrElse(k, 0)) }
    Group(counter + other.counter, concatenatedMembers)
  }
}
