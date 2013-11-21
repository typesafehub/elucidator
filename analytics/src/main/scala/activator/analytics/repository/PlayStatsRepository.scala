/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._

trait PlayStatsRepository {
  def save(stats: Iterable[PlayStats]): Unit
  def findBy(timeRange: TimeRange, scope: Scope): Option[PlayStats]
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope): Seq[PlayStats]
  def findSorted(timeRange: TimeRange, scope: Scope, sortOn: PlayStatsSort[_], maxResult: Int): Seq[PlayStats]
}

class MemoryPlayStatsRepository extends BasicMemoryStatsRepository[PlayStats, Scope] with PlayStatsRepository {

  override val customizer = new BasicMemoryStatsCustomizer[PlayStats, Scope]() {
    def scope(stat: PlayStats): Scope = stat.scope
    def timeRange(stat: PlayStats): TimeRange = stat.timeRange
    def anonymous(stat: PlayStats): Boolean = stat.scope.anonymous
  }

  def findSorted(timeRange: TimeRange, scope: Scope, sortOn: PlayStatsSort[_], maxResult: Int): Seq[PlayStats] = {
    val stats = getAllStats

    val matchingStats = for {
      stat ← stats
      // filter on time
      if timeRange.rangeType == stat.timeRange.rangeType
      if timeRange.startTime < stat.timeRange.endTime
      if timeRange.endTime > stat.timeRange.startTime

      // filter on parts of scope
      if scope.tag.isEmpty || scope.tag == stat.scope.tag
      if scope.node.isEmpty || scope.node == stat.scope.node
      if scope.actorSystem.isEmpty || scope.actorSystem == stat.scope.actorSystem
      if scope.dispatcher.isEmpty || scope.dispatcher == stat.scope.dispatcher

      // We're only interested in Play traces here
      if stat.scope.playPattern.isDefined
    } yield stat

    val groupedStats = matchingStats.groupBy(_.scope.playPattern).values

    val sortedStats = sortOn match {
      case _ ⇒
        groupedStats.map(_.maxBy(_.metrics.counts.invocationCount)).
          toList.sortWith((a, b) ⇒ a.metrics.counts.invocationCount > b.metrics.counts.invocationCount).
          filter(_.metrics.counts.invocationCount > 0)
    }

    sortedStats.take(maxResult)
  }
}

object LocalMemoryPlayStatsRepository extends MemoryPlayStatsRepository
