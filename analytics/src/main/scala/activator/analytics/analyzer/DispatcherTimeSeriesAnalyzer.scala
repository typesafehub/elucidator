/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data.{ TimeRange, DispatcherTimeSeriesPoint, DispatcherTimeSeries }
import activator.analytics.repository.DispatcherTimeSeriesRepository
import com.typesafe.trace.DispatcherStatus
import com.typesafe.trace.store.TraceRetrievalRepository
import com.typesafe.trace.TraceEvent

import scala.collection.mutable.ArrayBuffer
import TimeRange.minuteRange

class DispatcherTimeSeriesAnalyzer(
  repository: DispatcherTimeSeriesRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef]) extends EventStatsAnalyzer {

  type STATS = DispatcherTimeSeries
  type GROUP = GroupBy
  type BUF = DispatcherTimeSeriesBuffer
  val statsName: String = "DispatcherTimeSeries"

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[DispatcherTimeSeries]): Unit = {
      repository.save(stats)
    }

    def findBy(group: GroupBy): Option[DispatcherTimeSeries] = {
      repository.findBy(group.timeRange, group.node, group.actorSystem, group.dispatcher)
    }
  }

  def create(group: GroupBy): DispatcherTimeSeries = {
    DispatcherTimeSeries(group.timeRange, group.node, group.actorSystem, group.dispatcher, group.dispatcherType)
  }

  def createStatsBuffer(dispatcherTimeSeries: DispatcherTimeSeries): DispatcherTimeSeriesBuffer = {
    new DispatcherTimeSeriesBuffer(dispatcherTimeSeries)
  }

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case x: DispatcherStatus ⇒ true
    case other               ⇒ false
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = {
    val result = ArrayBuffer.empty[GroupBy]
    val minute = minuteRange(event.timestamp)
    val timeRanges = Seq(minute)

    event.annotation match {
      case x: DispatcherStatus ⇒
        for (timeRange ← timeRanges) {
          result += GroupBy(timeRange, event.node, event.actorSystem, x.dispatcher, x.dispatcherType)
        }
      case _ ⇒
    }

    result.toList
  }

  case class GroupBy(timeRange: TimeRange, node: String, actorSystem: String, dispatcher: String, dispatcherType: String)

  class DispatcherTimeSeriesBuffer(initial: DispatcherTimeSeries) extends EventStatsBuffer {
    val points: ArrayBuffer[DispatcherTimeSeriesPoint] = ArrayBuffer() ++= initial.points

    def +=(event: TraceEvent): Unit = event.annotation match {
      case dispatcherStatus: DispatcherStatus ⇒
        points += DispatcherTimeSeriesPoint(
          timestamp = event.timestamp,
          metrics = dispatcherStatus.metrics)
      case _ ⇒

    }

    def toStats = {
      DispatcherTimeSeries(
        initial.timeRange,
        initial.node,
        initial.actorSystem,
        initial.dispatcher,
        initial.dispatcherType,
        points.sortBy(_.timestamp).toIndexedSeq,
        initial.id)
    }
  }
}

