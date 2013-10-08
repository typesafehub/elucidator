/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.repository.SpanTimeSeriesRepository
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

import java.util.concurrent.TimeUnit
import scala.collection.mutable.ArrayBuffer
import TimeRange.minuteRange

class SpanTimeSeriesAnalyzer(
  pathLevel: Option[Boolean],
  spanTimeSeriesRepository: SpanTimeSeriesRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends SpanStatsAnalyzer with SpanStatsGrouping with ActorAnalyzerHelpers {

  type STATS = SpanTimeSeries
  type GROUP = GroupBy
  val statsName: String = "SpanTimeSeries" + (pathLevel match {
    case None        ⇒ ""
    case Some(true)  ⇒ "PathLevel"
    case Some(false) ⇒ "AggregatedLevel"
  })

  val ignoreSpanTimeSeries = AnalyzeExtension(system).IgnoreSpanTimeSeries

  val ignoreAggreagatedSpanTimeSeries = AnalyzeExtension(system).IgnoreAggregatedSpanTimeSeries

  override def purgeUnusedMillis: Long = TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES)

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[SpanTimeSeries]): Unit = {
      spanTimeSeriesRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[SpanTimeSeries] = {
      spanTimeSeriesRepository.findBy(group.timeRange, group.scope, group.spanTypeName)
    }

  }

  def create(group: GroupBy): SpanTimeSeries = {
    SpanTimeSeries(group.timeRange, group.scope, group.spanTypeName)
  }

  def createStatsBuffer(timeSeries: SpanTimeSeries): SpanStatsBuffer = {
    new SpanTimeSeriesBuffer(timeSeries)
  }

  def allGroups(span: Span): Seq[GroupBy] = {

    // note that the grouping is based on the endEvent,
    // the idea was that the durations are most related to the actor processing the message
    val endEventOption = traceRepository.event(span.endEvent)
    if (endEventOption.isEmpty || ignoreSpanTimeSeries.contains(span.formattedSpanTypeName)) {
      Nil
    } else {
      val endEvent = endEventOption.get
      val node = Some(endEvent.node)
      val actorSystem = Some(endEvent.actorSystem)
      val info = actorInfo(endEvent)
      val path = info.map(_.path)
      val dispatcher = info.flatMap(_.dispatcher)
      val tags = info.map(_.tags).getOrElse(Set.empty[String])

      val minute = minuteRange(endEvent.timestamp)
      val timeRanges = Seq(minute)

      val combinations = groupCombinations(timeRanges, span.spanTypeName, path, tags, node, dispatcher, actorSystem) filter { group ⇒
        group.scope.path.isDefined || !ignoreAggreagatedSpanTimeSeries.contains(span.formattedSpanTypeName)
      }
      pathLevel match {
        case None      ⇒ combinations
        case Some(use) ⇒ combinations filter { _.scope.path.isDefined == use }
      }
    }
  }

  def createGroup(
    timeRange: TimeRange,
    spanTypeName: String,
    actorPath: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None): GroupBy =
    GroupBy(timeRange, Scope(actorPath, tag, node, dispatcher, actorSystem), spanTypeName)

  class SpanTimeSeriesBuffer(initial: SpanTimeSeries) extends SpanStatsBuffer {

    val points: ArrayBuffer[SpanTimeSeriesPoint] = ArrayBuffer() ++= initial.points

    def +=(span: Span): Unit = {
      points += SpanTimeSeriesPoint(span.startTime, span.duration.toNanos, span.sampled)
    }

    def toStats = SpanTimeSeries(
      initial.timeRange,
      initial.scope,
      initial.spanTypeName,
      points.toIndexedSeq,
      initial.id)
  }

  case class GroupBy(timeRange: TimeRange, scope: Scope, spanTypeName: String)

}

