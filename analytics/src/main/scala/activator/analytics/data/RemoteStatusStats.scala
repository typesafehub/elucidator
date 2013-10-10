/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import com.typesafe.trace.uuid.UUID

/**
 * Aggregated statistics over the time period  for a node
 * or all nodes
 */
case class RemoteStatusStats(
  timeRange: TimeRange,
  node: Option[String],
  actorSystem: Option[String],
  metrics: RemoteStatusStatsMetrics = RemoteStatusStatsMetrics(),
  id: UUID = new UUID())

case class RemoteStatusStatsMetrics(
  counts: Map[String, Long] = Map.empty) {

  def concatenate(other: RemoteStatusStatsMetrics): RemoteStatusStatsMetrics = {
    val keys: Set[String] = counts.keySet ++ other.counts.keySet
    val newEntries = for (k ← keys) yield {
      val sum = counts.getOrElse(k, 0L) + other.counts.getOrElse(k, 0L)
      (k, sum)
    }
    RemoteStatusStatsMetrics(counts = Map.empty ++ newEntries)
  }
}

object RemoteStatusStats extends Chunker[RemoteStatusStats] {
  def concatenate(stats: Iterable[RemoteStatusStats], timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): RemoteStatusStats = {
    val metrics = stats.map(_.metrics).foldLeft(RemoteStatusStatsMetrics()) { _ concatenate _ }
    RemoteStatusStats(timeRange, node, actorSystem, metrics)
  }

  def chunk(
    minNumberOfChunks: Int,
    maxNumberOfChunks: Int,
    stats: Iterable[RemoteStatusStats],
    timeRange: TimeRange,
    node: Option[String],
    actorSystem: Option[String]): Seq[RemoteStatusStats] = {

    chunk(minNumberOfChunks, maxNumberOfChunks, stats, timeRange,
      s ⇒ s.timeRange,
      t ⇒ RemoteStatusStats(t, node, actorSystem),
      (t, s) ⇒ RemoteStatusStats.concatenate(s, t, node, actorSystem))
  }
}
