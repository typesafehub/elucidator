/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data.{ TimeRange, RemoteStatusStatsMetrics, RemoteStatusStats }
import activator.analytics.repository.RemoteStatusStatsRepository
import com.typesafe.atmos.trace.{ RemoteStatus, RemotingLifecycle }
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import com.typesafe.atmos.trace.TraceEvent

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{ Map ⇒ MutableMap }

class RemoteStatusStatsAnalyzer(
  remoteStatusStatsRepository: RemoteStatusStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends EventStatsAnalyzer {

  type STATS = RemoteStatusStats
  type GROUP = GroupBy
  override type BUF = RemoteStatusStatsBuffer
  val statsName: String = "RemoteStatusStats"

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[RemoteStatusStats]): Unit = {
      remoteStatusStatsRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[RemoteStatusStats] = {
      remoteStatusStatsRepository.findBy(group.timeRange, group.node, group.actorSystem)
    }

  }

  def create(group: GroupBy): RemoteStatusStats = {
    RemoteStatusStats(group.timeRange, group.node, group.actorSystem)
  }

  def createStatsBuffer(stats: RemoteStatusStats): RemoteStatusStatsBuffer = {
    new RemoteStatusStatsBuffer(stats)
  }

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case x: RemoteStatus      ⇒ true
    case x: RemotingLifecycle ⇒ true
    case _                    ⇒ false
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = {
    val node = Some(event.node)
    val actorSystem = Some(event.actorSystem)
    val timeRanges = allTimeRanges(event.timestamp)

    val result = ArrayBuffer.empty[GroupBy]
    for (t ← timeRanges) {
      result += GroupBy(t)
      result += GroupBy(t, node = node)
      result += GroupBy(t, actorSystem = actorSystem)
      result += GroupBy(t, node = node, actorSystem = actorSystem)
    }

    result.toList
  }

  class RemoteStatusStatsBuffer(initial: RemoteStatusStats)
    extends EventStatsBuffer {

    val counters = MutableMap[String, Long]()
    counters ++= initial.metrics.counts

    def +=(event: TraceEvent): Unit = event.annotation match {
      case x: RemoteStatus ⇒
        val key = x.statusType.toString
        counters(key) = counters.getOrElse(key, 0L) + 1
      case x: RemotingLifecycle ⇒
        val key = x.eventType.toString
        counters(key) = counters.getOrElse(key, 0L) + 1
      case _ ⇒
    }

    def toStats = {
      RemoteStatusStats(
        initial.timeRange,
        initial.node,
        initial.actorSystem,
        metrics = RemoteStatusStatsMetrics(counts = counters.toMap),
        initial.id)
    }

  }

  case class GroupBy(timeRange: TimeRange, node: Option[String] = None, actorSystem: Option[String] = None)

}

