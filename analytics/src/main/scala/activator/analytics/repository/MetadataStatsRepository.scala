/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data.{ MetadataStatsMetrics, MetadataStats, Scope, TimeRange }
import activator.analytics.analyzer.ActorClassification

trait MetadataStatsRepository {
  def save(stats: Iterable[MetadataStats])
  def findBy(timeRange: TimeRange, scope: Scope): Option[MetadataStats]
  def findFiltered(timeRange: TimeRange, scope: Scope, includeAnonymousActors: Boolean, includeTempActors: Boolean): MetadataStats
}

class MemoryMetadataStatsRepository extends BasicMemoryStatsRepository[MetadataStats, Scope] with MetadataStatsRepository {
  override val customizer = new BasicMemoryStatsCustomizer[MetadataStats, Scope]() {
    def scope(stat: MetadataStats): Scope = stat.scope
    def timeRange(stat: MetadataStats): TimeRange = stat.timeRange
    def anonymous(stat: MetadataStats): Boolean = stat.scope.anonymous
  }

  def findFiltered(timeRange: TimeRange, scope: Scope, includeAnonymousActors: Boolean, includeTempActors: Boolean): MetadataStats = {
    val stats = getAllStats
    val metricsStats = for {
      stat ← stats
      if timeRange.rangeType == stat.timeRange.rangeType
      if timeRange.startTime < stat.timeRange.endTime
      if timeRange.endTime > stat.timeRange.startTime

      // filter on parts of scope
      if scope.node.isEmpty || scope.node == stat.scope.node
      if scope.actorSystem.isEmpty || scope.actorSystem == stat.scope.actorSystem
      if scope.dispatcher.isEmpty || scope.dispatcher == stat.scope.dispatcher
      if scope.path.isEmpty || scope.path == stat.scope.path
      if scope.tag.isEmpty || scope.tag == stat.scope.tag
      if scope.playPattern.isEmpty || scope.playPattern == stat.scope.playPattern
      if scope.playController.isEmpty || scope.playController == stat.scope.playController
    } yield stat.metrics

    val pathSet = metricsStats.map(_.paths).flatten.toSet

    val tempFilteredPaths =
      if (includeTempActors) pathSet
      else ActorClassification.filterNotTemporary(pathSet)

    val filteredPaths =
      if (includeAnonymousActors) tempFilteredPaths
      else ActorClassification.filterNotAnonymous(tempFilteredPaths)

    MetadataStats(
      timeRange = timeRange,
      scope = scope,
      metrics = MetadataStatsMetrics(
        tags = metricsStats.map(_.tags).flatten.toSet,
        paths = filteredPaths,
        totalActorCount = Some(filteredPaths.size),
        dispatchers = metricsStats.map(_.dispatchers).flatten.toSet,
        actorSystems = metricsStats.map(_.actorSystems).flatten.toSet,
        nodes = metricsStats.map(_.nodes).flatten.toSet,
        playPatterns = metricsStats.map(_.playPatterns).flatten.toSet,
        playControllers = metricsStats.map(_.playControllers).flatten.toSet))
  }
}

object LocalMemoryMetadataStatsRepository extends MemoryMetadataStatsRepository