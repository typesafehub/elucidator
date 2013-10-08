/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import activator.analytics.repository.MetadataStatsRepository
import activator.analytics.data.{ MetadataStatsMetrics, Scope, TimeRange, MetadataStats }
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.RemoteMessageSent
import com.typesafe.atmos.trace.SysMsgCompleted
import akka.actor.ActorRef
import scala.collection.mutable.ArrayBuffer

class MetadataStatsAnalyzer(
  metadataStatsRepository: MetadataStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends EventStatsAnalyzer with EventStatsGrouping {

  type STATS = MetadataStats
  type GROUP = GroupBy
  type BUF = MetadataStatsBufferImpl

  def statsName: String = "MetadataStats"

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[MetadataStats]): Unit = metadataStatsRepository.save(stats)
    def findBy(group: GroupBy): Option[MetadataStats] = metadataStatsRepository.findBy(group.timeRange, group.scope)
  }

  def createStatsBuffer(stats: MetadataStats): MetadataStatsBufferImpl = new MetadataStatsBufferImpl(stats)

  class MetadataStatsBufferImpl(initial: MetadataStats) extends EventStatsBuffer {
    var nodes = initial.metrics.nodes
    var actorSystems = initial.metrics.actorSystems
    var dispatchers = initial.metrics.dispatchers
    var paths = initial.metrics.paths
    var tags = initial.metrics.tags
    var playPatterns = initial.metrics.playPatterns
    var playControllers = initial.metrics.playControllers

    def +=(event: TraceEvent) {
      event.annotation match {
        case a: ActorAnnotation  ⇒ addStats(event, a.info)
        case a: SysMsgAnnotation ⇒ addStats(event, a.info)
        case a: ActionInvoked    ⇒ addPlayStats(event, a.invocationInfo)
        case _                   ⇒
      }

      def addStats(event: TraceEvent, info: ActorInfo): Unit = {
        nodes += event.node
        actorSystems += event.actorSystem
        for (dispatcher ← info.dispatcher) dispatchers += dispatcher
        paths += info.path
        tags = tags ++ info.tags
      }

      def addPlayStats(event: TraceEvent, info: ActionInvocationInfo): Unit = {
        playControllers += (info.controller + "." + info.method)
        playPatterns += info.pattern
      }
    }

    def toStats = {
      MetadataStats(
        initial.timeRange,
        initial.scope,
        MetadataStatsMetrics(
          nodes = nodes,
          actorSystems = actorSystems,
          dispatchers = dispatchers,
          paths = paths,
          tags = tags,
          playPatterns = playPatterns,
          playControllers = playControllers),
        initial.id)
    }
  }

  def create(group: GroupBy): MetadataStats = MetadataStats(group.timeRange, group.scope)

  def createGroup(
    timeRange: TimeRange,
    path: Option[String],
    tag: Option[String],
    node: Option[String],
    dispatcher: Option[String],
    actorSystem: Option[String],
    playPattern: Option[String],
    playController: Option[String]): GroupBy = GroupBy(timeRange, Scope(path, tag, node, dispatcher, actorSystem, playPattern, playController))

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case a: ActorAnnotation  ⇒ !a.info.remote
    case a: SysMsgAnnotation ⇒ !a.info.remote
    case a: ActionInvoked    ⇒ true
    case _                   ⇒ false
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = event.annotation match {
    case a: ActorAnnotation  ⇒ groupsFor(event, a.info)
    case a: SysMsgAnnotation ⇒ groupsFor(event, a.info)
    case a: ActionInvoked    ⇒ groupsForPlay(event, a.invocationInfo)
    case _                   ⇒ Seq.empty
  }

  def groupsFor(event: TraceEvent, info: ActorInfo): Seq[GroupBy] = {
    val timeRanges = allTimeRanges(event.timestamp)
    groupCombinations(timeRanges, None, info.tags, Some(event.node), info.dispatcher, Some(event.actorSystem), None, None)
  }

  def groupsForPlay(event: TraceEvent, info: ActionInvocationInfo): Seq[GroupBy] = {
    val timeRanges = allTimeRanges(event.timestamp)
    groupCombinations(timeRanges, None, Set.empty[String], Some(event.node), None, None, None, None)
  }
}

case class GroupBy(timeRange: TimeRange, scope: Scope)

