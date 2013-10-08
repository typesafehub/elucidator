/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.data.{ TimeRange, Span, Scope, HistogramSpanStats }
import activator.analytics.metrics.HistogramMetric
import activator.analytics.repository.HistogramSpanStatsRepository
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

class HistogramSpanStatsAnalyzer(
  pathLevel: Option[Boolean],
  histogramSpanStatsRepository: HistogramSpanStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends SpanStatsAnalyzer with SpanStatsGrouping with ActorAnalyzerHelpers {

  type STATS = HistogramSpanStats
  type GROUP = GroupBy
  val statsName: String = "HistogramSpanStats" + (pathLevel match {
    case None        ⇒ ""
    case Some(true)  ⇒ "PathLevel"
    case Some(false) ⇒ "AggregatedLevel"
  })

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[HistogramSpanStats]): Unit = {
      histogramSpanStatsRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[HistogramSpanStats] = {
      histogramSpanStatsRepository.findBy(group.timeRange, group.scope, group.spanTypeName)
    }
  }

  def create(group: GroupBy): HistogramSpanStats = {
    HistogramSpanStats(group.timeRange, group.scope, group.spanTypeName)
  }

  def createStatsBuffer(stats: HistogramSpanStats): SpanStatsBuffer = {
    new HistogramSpanStatsBuffer(stats)
  }

  def allGroups(span: Span): Seq[GroupBy] = {

    // note that the grouping is based on the endEvent,
    // the idea was that the durations are most related to the actor processing the message
    val endEventOption = traceRepository.event(span.endEvent)
    if (endEventOption.isEmpty) {
      Nil
    } else {
      val endEvent = endEventOption.get
      val node = Some(endEvent.node)
      val actorSystem = Some(endEvent.actorSystem)
      val info = actorInfo(endEvent)
      val path = info.map(_.path)
      val dispatcher = info.flatMap(_.dispatcher)
      val tags = info.map(_.tags).getOrElse(Set.empty[String])

      val timeRanges = allTimeRanges(span.startTime)

      val combinations = groupCombinations(timeRanges, span.spanTypeName, path, tags, node, dispatcher, actorSystem)
      pathLevel match {
        case None      ⇒ combinations
        case Some(use) ⇒ combinations filter { _.scope.path.isDefined == use }
      }
    }
  }

  def createGroup(
    timeRange: TimeRange,
    spanTypeName: String,
    path: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None): GroupBy =
    GroupBy(timeRange, Scope(path, tag, node, dispatcher, actorSystem), spanTypeName)

  class HistogramSpanStatsBuffer(initial: HistogramSpanStats) extends SpanStatsBuffer {

    val metrics = new HistogramMetric(initial.bucketBoundaries, initial.buckets)

    def +=(span: Span): Unit = {
      metrics += (span.duration.toNanos, span.sampled)
    }

    def toStats = HistogramSpanStats(
      initial.timeRange,
      initial.scope,
      initial.spanTypeName,
      metrics.bucketBoundaries,
      metrics.buckets,
      initial.id)

  }

  case class GroupBy(timeRange: TimeRange, scope: Scope, spanTypeName: String)

}

