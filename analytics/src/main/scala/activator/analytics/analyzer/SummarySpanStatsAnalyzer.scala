/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import collection.mutable.ArrayBuffer
import activator.analytics.data._
import activator.analytics.metrics.SummaryMetrics
import activator.analytics.repository.DuplicatesRepository
import activator.analytics.repository.SummarySpanStatsRepository
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

class SummarySpanStatsAnalyzer(
  pathLevel: Option[Boolean],
  summarySpanStatsRepository: SummarySpanStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val duplicatesRepository: DuplicatesRepository,
  val alertDispatcher: Option[ActorRef])
  extends SpanStatsAnalyzer with SpanStatsGrouping with ActorAnalyzerHelpers {

  type STATS = SummarySpanStats
  type GROUP = GroupBy
  val statsName: String = "SummarySpanStats" + (pathLevel match {
    case None        ⇒ ""
    case Some(true)  ⇒ "PathLevel"
    case Some(false) ⇒ "AggregatedLevel"
  })

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[SummarySpanStats]): Unit = {
      summarySpanStatsRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[SummarySpanStats] = {
      summarySpanStatsRepository.findBy(group.timeRange, group.scope, group.spanTypeName)
    }

  }

  def create(group: GroupBy): SummarySpanStats = {
    SummarySpanStats(group.timeRange, group.scope, group.spanTypeName)
  }

  def createStatsBuffer(stats: SummarySpanStats): SpanStatsBuffer = {
    if (metadataOnlyGroups.contains(GroupBy(stats.timeRange, stats.scope, stats.spanTypeName))) new MetadataOnlyStatsBufferImpl(stats)
    else new SummarySpanStatsBufferImpl(stats)
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

  override def groupCombinations(
    timeRanges: Seq[TimeRange],
    spanTypeName: String,
    path: Option[String],
    tags: Set[String],
    node: Option[String],
    dispatcher: Option[String],
    actorSystem: Option[String]): Seq[GroupBy] = {

    val initialCombinations = super.groupCombinations(timeRanges, spanTypeName, path, tags, node, dispatcher, actorSystem)

    val additionalCombinations = ArrayBuffer.empty[GroupBy]
    for (t ← timeRanges; tag ← tags) {
      val someTag = Some(tag)
      if (actorPathLevelTimeRanges.contains(t.timeRangeValue) && path.isDefined)
        additionalCombinations += createGroup(t, spanTypeName, path = path, tag = someTag)
      if (node.isDefined)
        additionalCombinations += createGroup(t, spanTypeName, node = node, tag = someTag)
    }

    metadataOnlyGroups = additionalCombinations.toSet

    additionalCombinations.appendAll(initialCombinations)
    additionalCombinations
  }

  var metadataOnlyGroups: Set[GroupBy] = Set.empty[GroupBy]

  def createGroup(
    timeRange: TimeRange,
    spanTypeName: String,
    path: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None): GroupBy =
    GroupBy(timeRange, Scope(path, tag, node, dispatcher, actorSystem), spanTypeName)

  class MetadataOnlyStatsBufferImpl(initial: SummarySpanStats) extends SpanStatsBuffer {
    def +=(span: Span): Unit = {
      // no calculation
    }

    def toStats = initial
  }

  class SummarySpanStatsBufferImpl(initial: SummarySpanStats) extends SpanStatsBuffer {

    val metrics = new SummaryMetrics(initial.n, initial.totalDuration, initial.minDuration, initial.maxDuration)

    def +=(span: Span): Unit = {
      metrics += (span.duration.toNanos, span.sampled)
    }

    def toStats = SummarySpanStats(
      initial.timeRange,
      initial.scope,
      initial.spanTypeName,
      metrics.count,
      metrics.sum,
      metrics.min,
      metrics.max,
      metrics.mean,
      initial.id)

  }

  case class GroupBy(timeRange: TimeRange, scope: Scope, spanTypeName: String)

}

