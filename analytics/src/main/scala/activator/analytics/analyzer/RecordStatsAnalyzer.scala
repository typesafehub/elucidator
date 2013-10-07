/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.data.{ TimeRange, Group, RecordStatsMetrics, RecordStats }
import activator.analytics.repository.{ DuplicatesRepository, RecordStatsRepository }
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

import scala.collection.mutable.ArrayBuffer

class RecordStatsAnalyzer(
  recordStatsRepository: RecordStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val duplicatesRepository: DuplicatesRepository,
  val alertDispatcher: Option[ActorRef])
  extends EventStatsAnalyzer {

  type STATS = RecordStats
  type GROUP = GroupBy
  override type BUF = RecordStatsBuffer
  val statsName: String = "RecordStats"

  case class GroupBy(timeRange: TimeRange, node: Option[String] = None, actorSystem: Option[String] = None)

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[RecordStats]): Unit = {
      recordStatsRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[RecordStats] = {
      recordStatsRepository.findBy(group.timeRange, group.node, group.actorSystem)
    }
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = {
    val result = ArrayBuffer.empty[GroupBy]
    val timeRanges = allTimeRanges(event.timestamp)

    for (timeRange ← timeRanges) {
      result += GroupBy(timeRange)
      result += GroupBy(timeRange, node = Some(event.node))
    }

    for (timeRange ← timeRanges) {
      result += GroupBy(timeRange)
      result += GroupBy(timeRange, node = Some(event.node))
      result += GroupBy(timeRange, actorSystem = Some(event.actorSystem))
      result += GroupBy(timeRange, node = Some(event.node), actorSystem = Some(event.actorSystem))
    }

    result.toList
  }

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case x: Marker ⇒ true
    case _         ⇒ false
  }

  def createStatsBuffer(stats: RecordStats): RecordStatsBuffer = new RecordStatsBuffer(stats)

  def create(group: GroupBy): RecordStats = {
    RecordStats(group.timeRange, group.node, group.actorSystem)
  }

  class RecordStatsBuffer(initial: RecordStats) extends EventStatsBuffer {
    var groups = scala.collection.mutable.Map(initial.metrics.groups.toSeq: _*)

    def +=(event: TraceEvent) = event.annotation match {
      case x: Marker ⇒
        groups += x.name -> groups.getOrElse(x.name, Group()).concatenate(
          Group(counter = 1, members = Map(x.data -> 1)))
      case _ ⇒
    }

    def toStats = {
      RecordStats(initial.timeRange,
        initial.node,
        initial.actorSystem,
        RecordStatsMetrics(groups.toMap),
        initial.id)
    }
  }
}
