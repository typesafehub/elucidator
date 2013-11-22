/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import activator.analytics.data.Sorting._
import activator.analytics.analyzer.ActorClassification

trait ActorStatsRepository {
  def save(stats: Iterable[ActorStats])
  def findBy(timeRange: TimeRange, scope: Scope): Option[ActorStats]
  def findWithinTimePeriod(timeRange: TimeRange, scope: Scope): Seq[ActorStats]
  def findSorted(
    timeRange: TimeRange,
    scope: Scope,
    includeAnonymous: Boolean,
    includeTemp: Boolean,
    offset: Int = 0,
    limit: Int,
    sortOn: ActorStatsSort,
    sortDirection: SortDirection): ActorStatsSorted
}

object ActorStatsRepository {
  def collectSorted(
    stats: Seq[ActorStats],
    timeRange: TimeRange,
    scope: Scope,
    includeAnonymous: Boolean,
    includeTemp: Boolean,
    offset: Int,
    limit: Int,
    sortOn: ActorStatsSort,
    sortDirection: SortDirection): ActorStatsSorted = {

    val tempStats =
      if (includeTemp) stats
      else stats.filterNot(x ⇒ ActorClassification.containsTemporaryPath(x.scope))

    val filteredStats =
      if (includeAnonymous) tempStats
      else tempStats.filterNot(x ⇒ ActorClassification.containsAnonymousPath(x.scope))

    val groupedStats = (filteredStats.groupBy(_.scope.path) map {
      case (path, stats) ⇒
        val oneScope = stats.groupBy(_.timeRange).values.flatMap(ActorStats.mostGeneralScope)
        ActorStats.concatenate(oneScope, timeRange, scope.copy(path = path))
    }).toSeq

    def sortAnonymous(a: String, b: String) = (a, b) match {
      case (x, y) if x.contains("$") && y.contains("$") ⇒ a < b
      case (x, y) if x.contains("$") ⇒ false
      case (x, y) if y.contains("$") ⇒ true
      case _ ⇒ a < b
    }

    val descending = sortOn match {
      case ActorStatsSorts.DeviationsSort ⇒
        groupedStats.sortWith((a, b) ⇒ a.metrics.counts.deviationCount > b.metrics.counts.deviationCount)
      case ActorStatsSorts.ProcessedMessagesSort ⇒
        groupedStats.sortWith((a, b) ⇒ a.metrics.messageRateMetrics.totalMessageRate > b.metrics.messageRateMetrics.totalMessageRate)
      case ActorStatsSorts.MaxMailboxSizeSort ⇒
        groupedStats.sortWith((a, b) ⇒ a.metrics.mailbox.maxMailboxSize > b.metrics.mailbox.maxMailboxSize)
      case ActorStatsSorts.ActorPath ⇒
        groupedStats.sortWith((a, b) ⇒ {
          if (a.scope.path.isDefined && b.scope.path.isDefined) sortAnonymous(a.scope.path.get, b.scope.path.get)
          else true
        })
      case ActorStatsSorts.ActorName ⇒
        groupedStats.sortWith((a, b) ⇒ {
          if (a.scope.path.isDefined && b.scope.path.isDefined) {
            val aName = a.scope.path.get.substring(a.scope.path.get.lastIndexOf("/"))
            val bName = b.scope.path.get.substring(b.scope.path.get.lastIndexOf("/"))
            sortAnonymous(aName, bName)
          } else true
        })
      case _ ⇒
        groupedStats.sortWith((a, b) ⇒ a.metrics.mailbox.maxTimeInMailbox > b.metrics.mailbox.maxTimeInMailbox)
    }

    val sortedStats =
      if (sortDirection == descendingSort) descending
      else descending.reverse

    ActorStatsSorted(
      timeRange,
      scope,
      sortedStats.slice(offset, offset + limit),
      offset,
      limit,
      sortedStats.size)
  }
}

case class ActorStatsSorted(timeRange: TimeRange, scope: Scope, stats: Seq[ActorStats], offset: Int, limit: Int, total: Int)

class MemoryActorStatsRepository extends BasicMemoryStatsRepository[ActorStats, Scope] with ActorStatsRepository {
  override val customizer = new BasicMemoryStatsCustomizer[ActorStats, Scope]() {
    def scope(stat: ActorStats): Scope = stat.scope
    def timeRange(stat: ActorStats): TimeRange = stat.timeRange
    def anonymous(stat: ActorStats): Boolean = stat.scope.anonymous
  }

  def findSorted(
    timeRange: TimeRange,
    scope: Scope,
    includeAnonymous: Boolean,
    includeTemp: Boolean,
    offset: Int,
    limit: Int,
    sortOn: ActorStatsSort,
    sortDirection: SortDirection): ActorStatsSorted = {
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

      // We're only interested in ActorPaths here
      if stat.scope.path.isDefined
    } yield stat

    ActorStatsRepository.collectSorted(matchingStats, timeRange, scope, includeAnonymous, includeTemp, offset, limit, sortOn, sortDirection)
  }
}

object LocalMemoryActorStatsRepository extends MemoryActorStatsRepository
