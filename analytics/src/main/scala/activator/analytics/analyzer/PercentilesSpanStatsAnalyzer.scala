/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.data.Span
import activator.analytics.metrics.{ LatestSample, UniformSample, PercentilesMetrics }
import activator.analytics.repository.PercentilesSpanStatsRepository
import com.typesafe.trace.store.TraceRetrievalRepository
import TimeRange.hourRange
import activator.analytics.AnalyticsExtension

class PercentilesSpanStatsAnalyzer(
  pathLevel: Option[Boolean],
  percentilesSpanStatsRepository: PercentilesSpanStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends SpanStatsAnalyzer with SpanStatsGrouping with ActorAnalyzerHelpers {

  type STATS = PercentilesSpanStats
  type GROUP = GroupBy
  val statsName: String = "PercentilesSpanStats" + (pathLevel match {
    case None        ⇒ ""
    case Some(true)  ⇒ "PathLevel"
    case Some(false) ⇒ "AggregatedLevel"
  })

  lazy val percentilesDoubles = AnalyticsExtension(system).Percentiles.map(_.toDouble / 100)

  override val storeTimeIntervalMillis = AnalyticsExtension(system).PercentilesStoreTimeInterval

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[PercentilesSpanStats]): Unit = {
      percentilesSpanStatsRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[PercentilesSpanStats] = {
      percentilesSpanStatsRepository.findBy(group.timeRange, group.scope, group.spanTypeName)
    }
  }

  def create(group: GroupBy): PercentilesSpanStats = {
    PercentilesSpanStats(group.timeRange, group.scope, group.spanTypeName)
  }

  def createStatsBuffer(stats: PercentilesSpanStats): SpanStatsBuffer = {
    new PercentilesSpanStatsBuffer(stats)
  }

  def reservoirSize(scope: Scope): Int = scope.path match {
    case None    ⇒ AnalyticsExtension(system).PercentilesSampleReservoirSize
    case Some(_) ⇒ AnalyticsExtension(system).PercentilesSampleReservoirSizeIndividualActor
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

      val hour = hourRange(endEvent.timestamp)
      val timeRanges =
        if (useAllTime) Seq(hour, TimeRange())
        else Seq(hour)

      val combinations = groupCombinations(timeRanges, span.spanTypeName, path, tags, node, dispatcher, actorSystem)

      pathLevel match {
        case None      ⇒ combinations filter (group ⇒ reservoirSize(group.scope) > 0)
        case Some(use) ⇒ combinations filter { group ⇒ group.scope.path.isDefined == use && reservoirSize(group.scope) > 0 }
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

  class PercentilesSpanStatsBuffer(initial: PercentilesSpanStats) extends SpanStatsBuffer {

    def sample = initial.timeRange.rangeType match {
      case TimeRangeType.AllTime ⇒ new LatestSample(reservoirSize(initial.scope))
      case _                     ⇒ new UniformSample(reservoirSize(initial.scope))
    }
    val metrics = new PercentilesMetrics(sample)

    def +=(span: Span): Unit = {
      metrics += (span.duration.toNanos, span.sampled)
    }

    def toStats = {
      val percentilesValues = metrics.percentiles(percentilesDoubles).map(_.toLong)
      val percentilesMap = Map() ++ AnalyticsExtension(system).Percentiles.toSeq.zip(percentilesValues)
      PercentilesSpanStats(
        initial.timeRange,
        initial.scope,
        initial.spanTypeName,
        metrics.count,
        Some(percentilesMap),
        initial.id)
    }

  }

  case class GroupBy(timeRange: TimeRange, scope: Scope, spanTypeName: String)

}

