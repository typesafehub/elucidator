/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data.{ TimeRange, SystemMetricsTimeSeriesPoint, SystemMetricsTimeSeries }
import activator.analytics.repository.SystemMetricsTimeSeriesRepository
import com.typesafe.atmos.trace.ActorCreated
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import com.typesafe.atmos.trace.SysMsgCompleted
import com.typesafe.atmos.trace.SystemMetrics
import com.typesafe.atmos.trace.SystemShutdown
import com.typesafe.atmos.trace.SystemStarted
import com.typesafe.atmos.trace.TempActorCreated
import com.typesafe.atmos.trace.TempActorStopped
import com.typesafe.atmos.trace.TerminateSysMsg
import com.typesafe.atmos.trace.TraceEvent

import scala.collection.mutable.ArrayBuffer
import TimeRange.minuteRange

class SystemMetricsTimeSeriesAnalyzer(
  repository: SystemMetricsTimeSeriesRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef]) extends EventStatsAnalyzer {

  type BUF = SystemMetricsTimeSeriesBuffer
  type STATS = SystemMetricsTimeSeries
  type GROUP = GroupBy

  def statsName = "SystemMetricsTimeSeries"

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[SystemMetricsTimeSeries]): Unit = {
      repository.save(stats)
    }

    def findBy(group: GroupBy): Option[SystemMetricsTimeSeries] = {
      repository.findBy(group.timeRange, group.node)
    }
  }

  def create(group: GroupBy) = SystemMetricsTimeSeries(group.timeRange, group.node)

  def createStatsBuffer(stats: SystemMetricsTimeSeries) = {
    val previousTimeRange = TimeRange.rangeFor(stats.timeRange.startTime - 1, stats.timeRange.rangeType)
    val previous = statsBuffers.get(GroupBy(previousTimeRange, stats.node))
    new SystemMetricsTimeSeriesBuffer(stats, previous)
  }

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case _: SystemMetrics ⇒ true
    case _: ActorCreated | _: SysMsgCompleted | _: SystemStarted | _: SystemShutdown ⇒ true
    case _: TempActorCreated | _: TempActorStopped ⇒ true
    case other ⇒ false
  }

  def allGroups(event: TraceEvent) = {
    val result = ArrayBuffer.empty[GroupBy]
    val minute = minuteRange(event.timestamp)
    val timeRanges = Seq(minute)

    for (timeRange ← timeRanges) {
      result += GroupBy(timeRange, event.node)
    }

    result.toList
  }

  case class GroupBy(timeRange: TimeRange, node: String)

  class SystemMetricsTimeSeriesBuffer(initial: SystemMetricsTimeSeries, var previous: Option[SystemMetricsTimeSeriesBuffer]) extends EventStatsBuffer {
    val points: ArrayBuffer[SystemMetricsTimeSeriesPoint] = ArrayBuffer() ++= initial.points
    // initialize runningActors count with value from stored value or the previous buffer
    var runningActors: Long =
      if (initial.points.isEmpty) previous.map(_.runningActors).getOrElse(0L)
      else initial.points.last.metrics.runningActors
    // previous SystemMetricsTimeSeriesBuffer can now be garbage collected
    previous = None

    def +=(event: TraceEvent) = event.annotation match {
      case systemMetrics: SystemMetrics ⇒
        points += SystemMetricsTimeSeriesPoint(
          timestamp = event.timestamp,
          metrics = systemMetrics.copy(runningActors = runningActors))
      case _: ActorCreated                      ⇒ runningActors += 1
      case SysMsgCompleted(_, TerminateSysMsg)  ⇒ runningActors -= 1
      case _: TempActorCreated                  ⇒ runningActors += 1
      case _: TempActorStopped                  ⇒ runningActors -= 1
      case _: SystemStarted | _: SystemShutdown ⇒ runningActors = 0
      case _                                    ⇒
    }

    def toStats =
      SystemMetricsTimeSeries(
        initial.timeRange,
        initial.node,
        points.sortBy(_.timestamp).toIndexedSeq,
        initial.id)
  }

}

