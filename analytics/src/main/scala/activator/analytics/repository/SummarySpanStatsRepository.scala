/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.repository

import activator.analytics.data._

import scala.collection.mutable.{ Set ⇒ MutableSet }
import activator.analytics.analyzer.ActorClassification

trait SummarySpanStatsRepository {
  def save(stats: Iterable[SummarySpanStats]): Unit
  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[SummarySpanStats]
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[SummarySpanStats]
  def findMetadata(timeRange: TimeRange, scope: Scope, includeAnonymousActors: Boolean, includeTempActors: Boolean): MetadataStats
}

class MemorySummarySpanStatsRepository extends BasicMemoryStatsRepository[SummarySpanStats, SpanScope] with SummarySpanStatsRepository {

  override val customizer = new BasicStatsCustomizer[SummarySpanStats]()

  def findBy(timeRange: TimeRange, scope: Scope, spanTypeName: String): Option[SummarySpanStats] = {
    findBy(timeRange, SpanScope(scope, spanTypeName))
  }

  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope, spanTypeName: String): Seq[SummarySpanStats] = {
    findWithinTimePeriod(timeRange, SpanScope(scope, spanTypeName))
  }

  def findMetadata(timeRange: TimeRange, scope: Scope, includeAnonymousActors: Boolean, includeTempActors: Boolean): MetadataStats = {
    val stats = getAllStats
    val matchingStats = for {
      stat ← stats.toSet
      if timeRange.rangeType == stat.timeRange.rangeType
      if timeRange.startTime < stat.timeRange.endTime
      if timeRange.endTime > stat.timeRange.startTime

      // filter on parts of scope
      if scope.path.isEmpty || scope.path == stat.scope.path
      if scope.dispatcher.isEmpty || scope.dispatcher == stat.scope.dispatcher
      if scope.node.isEmpty || scope.node == stat.scope.node
      if scope.actorSystem.isEmpty || scope.actorSystem == stat.scope.actorSystem
      if scope.tag.isEmpty || scope.tag == stat.scope.tag
    } yield stat

    val pathSet = matchingStats.map(_.scope.path).flatten.toSet

    val tempFilteredPaths =
      if (includeTempActors) pathSet
      else ActorClassification.filterNotTemporary(pathSet)

    val filteredPaths =
      if (includeAnonymousActors) tempFilteredPaths
      else ActorClassification.filterNotAnonymous(tempFilteredPaths)

    new MetadataStats(
      timeRange = timeRange,
      scope = scope,
      metrics = MetadataStatsMetrics(
        spanTypes = matchingStats.map(_.spanTypeName).toSet,
        paths = filteredPaths,
        totalActorCount = Some(filteredPaths.size),
        dispatchers = matchingStats.map(_.scope.dispatcher).flatten.toSet,
        nodes = matchingStats.map(_.scope.node).flatten.toSet,
        actorSystems = matchingStats.map(_.scope.actorSystem).flatten.toSet,
        tags = matchingStats.map(_.scope.tag).flatten.toSet))
  }
}

object LocalMemorySummarySpanStatsRepository extends MemorySummarySpanStatsRepository
