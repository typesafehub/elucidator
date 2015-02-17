/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes._
import activator.analytics.data.TimeRangeType.AllTime
import activator.analytics.metrics.RateMetric
import com.typesafe.trace.uuid.UUID
import com.typesafe.trace.util.Uuid
import scala.collection.SeqView

/**
 * Aggregated statistics over the time period for the Play actions
 * that belong to the scope.
 */
case class PlayStats(
  timeRange: TimeRange,
  scope: Scope,
  metrics: PlayStatsMetrics = PlayStatsMetrics(),
  id: UUID = new UUID()) {

  /**
   * Average invocation rate over the time period.
   * N/A for TimeRange type AllTime.
   */
  def meanInvocationRate: Rate = meanRate(metrics.counts.invocationCount)

  /**
   * Average bytesRead rate (bytes/second) over the time period.
   * N/A for TimeRange type AllTime.
   */
  def meanBytesReadRate: BytesRate = meanRate(metrics.bytesRead)

  /**
   * Average bytesWritten rate (bytes/second) over the time period.
   * N/A for TimeRange type AllTime.
   */
  def meanBytesWrittenRate: BytesRate = meanRate(metrics.bytesWritten)

  def meanDuration: DurationNanos = metrics.processingStats.meanDuration(metrics.counts.invocationCount)

  def meanInputProcessingDuration: DurationNanos = metrics.processingStats.meanInputProcessingDuration(metrics.counts.invocationCount)

  def meanActionExecutionDuration: DurationNanos = metrics.processingStats.meanActionExecutionDuration(metrics.counts.invocationCount)

  def meanOutputProcessingDuration: DurationNanos = metrics.processingStats.meanOutputProcessingDuration(metrics.counts.invocationCount)

  private final def meanRate(count: Long): Double = {
    if (timeRange.rangeType == AllTime || metrics.latestTraceEventTimestamp == 0L) {
      0.0
    } else if (timeRange.endTime < System.currentTimeMillis) {
      RateMetric.rate(count, timeRange.startTime, timeRange.endTime)
    } else {
      // the time period is ongoing, use latestTraceEventTimestamp
      RateMetric.rate(count, timeRange.startTime, metrics.latestTraceEventTimestamp)
    }
  }
}

case class PlayStatsMetrics(
  latestTraceEventTimestamp: Timestamp = 0L,
  latestInvocationTimestamp: Timestamp = 0L,
  bytesWritten: Bytes = 0L,
  bytesRead: Bytes = 0L,
  processingStats: PlayProcessingStats = new PlayProcessingStats(),
  counts: PlayStatsCounts = PlayStatsCounts(),
  invocationRateMetrics: InvocationRateMetrics = InvocationRateMetrics(),
  peakInvocationRateMetrics: PeakInvocationRateMetrics = PeakInvocationRateMetrics()) {

  def concatenate(other: PlayStatsMetrics): PlayStatsMetrics = {

    PlayStatsMetrics(
      latestTraceEventTimestamp = math.max(latestTraceEventTimestamp, other.latestTraceEventTimestamp),
      latestInvocationTimestamp = math.max(latestInvocationTimestamp, other.latestInvocationTimestamp),
      bytesWritten = bytesWritten + other.bytesWritten,
      bytesRead = bytesRead + other.bytesRead,
      processingStats = processingStats.concatenate(other.processingStats),
      counts = counts.concatenate(other.counts),
      invocationRateMetrics = other.invocationRateMetrics,
      peakInvocationRateMetrics = peakInvocationRateMetrics.concatenate(other.peakInvocationRateMetrics))
  }
}

object PlayStats extends Chunker[PlayStats] {

  def concatenate(stats: Iterable[PlayStats], timeRange: TimeRange, scope: Scope): PlayStats = {
    val metrics = stats.map(_.metrics).foldLeft(PlayStatsMetrics()) { _ concatenate _ }
    PlayStats(timeRange, scope, metrics)
  }

  private[data] def max[T <% Ordered[T]](value1: T, timestamp1: Timestamp,
                                         value2: T, timestamp2: Timestamp): (T, Timestamp) = {
    if (value1 >= value2) (value1, timestamp1)
    else (value2, timestamp2)
  }

  def chunk(
    minNumberOfChunks: Int,
    maxNumberOfChunks: Int,
    stats: Iterable[PlayStats],
    timeRange: TimeRange,
    scope: Scope): Seq[PlayStats] = {

    chunk(minNumberOfChunks, maxNumberOfChunks, stats, timeRange,
      s ⇒ s.timeRange,
      t ⇒ PlayStats(t, scope),
      (t, s) ⇒ PlayStats.concatenate(s, t, scope))
  }
}

case class PlayStatsCounts(
  invocationCount: Long = 0L,
  simpleResultCount: Long = 0L,
  chunkedResultCount: Long = 0L,
  asyncResultCount: Long = 0L,
  countsByStatus: Map[String, Long] = Map.empty[String, Long], // Doing this because Salat does not support numeric types as the first type parameter
  exceptionCount: Long = 0L,
  handlerNotFoundCount: Long = 0L,
  badRequestCount: Long = 0L) {

  def errorCount: Long = exceptionCount + handlerNotFoundCount + badRequestCount

  def concatenate(other: PlayStatsCounts): PlayStatsCounts = {
    PlayStatsCounts(
      invocationCount = invocationCount + other.invocationCount,
      simpleResultCount = simpleResultCount + other.simpleResultCount,
      chunkedResultCount = chunkedResultCount + other.chunkedResultCount,
      asyncResultCount = asyncResultCount + other.asyncResultCount,
      countsByStatus = (countsByStatus.keySet ++ other.countsByStatus.keySet).foldLeft(Map.empty[String, Long]) {
        (s, v) ⇒ s + (v -> (countsByStatus.get(v).getOrElse(0L) + other.countsByStatus.get(v).getOrElse(0L)))
      },
      exceptionCount = exceptionCount + other.exceptionCount,
      handlerNotFoundCount = handlerNotFoundCount + other.handlerNotFoundCount,
      badRequestCount = badRequestCount + other.badRequestCount)
  }
}

case class PlayProcessingStats(
  durationSum: DurationNanos = 0L,
  inputProcessingDurationSum: DurationNanos = 0L,
  actionExecutionDurationSum: DurationNanos = 0L,
  outputProcessingDurationSum: DurationNanos = 0L,
  maxDurationTrace: UUID = Uuid.zero(),
  maxDurationTimestamp: Timestamp = 0L,
  maxDuration: DurationNanos = 0L,
  maxInputProcessingDurationTrace: UUID = Uuid.zero(),
  maxInputProcessingDurationTimestamp: Timestamp = 0L,
  maxInputProcessingDuration: DurationNanos = 0L,
  maxActionExecutionDurationTrace: UUID = Uuid.zero(),
  maxActionExecutionDurationTimestamp: Timestamp = 0L,
  maxActionExecutionDuration: DurationNanos = 0L,
  maxOutputProcessingDurationTrace: UUID = Uuid.zero(),
  maxOutputProcessingDurationTimestamp: Timestamp = 0L,
  maxOutputProcessingDuration: DurationNanos = 0L) {

  def concatenate(other: PlayProcessingStats): PlayProcessingStats = {

    val maxDurationTuple =
      if (maxDuration > other.maxDuration)
        (maxDuration, maxDurationTimestamp, maxDurationTrace)
      else
        (other.maxDuration, other.maxDurationTimestamp, other.maxDurationTrace)

    val maxInputProcessingDurationTuple =
      if (maxInputProcessingDuration > other.maxInputProcessingDuration)
        (maxInputProcessingDuration, maxInputProcessingDurationTimestamp, maxOutputProcessingDurationTrace)
      else
        (other.maxInputProcessingDuration, other.maxInputProcessingDurationTimestamp, other.maxOutputProcessingDurationTrace)

    val maxActionExecutionDurationTuple =
      if (maxActionExecutionDuration > other.maxActionExecutionDuration)
        (maxActionExecutionDuration, maxActionExecutionDurationTimestamp, maxActionExecutionDurationTrace)
      else
        (other.maxActionExecutionDuration, other.maxActionExecutionDurationTimestamp, other.maxActionExecutionDurationTrace)

    val maxOutputProcessingDurationTuple =
      if (maxOutputProcessingDuration > other.maxOutputProcessingDuration)
        (maxOutputProcessingDuration, maxOutputProcessingDurationTimestamp, maxOutputProcessingDurationTrace)
      else
        (other.maxOutputProcessingDuration, other.maxOutputProcessingDurationTimestamp, other.maxOutputProcessingDurationTrace)

    PlayProcessingStats(
      durationSum = durationSum + other.durationSum,
      inputProcessingDurationSum = inputProcessingDurationSum + other.inputProcessingDurationSum,
      actionExecutionDurationSum = actionExecutionDurationSum + other.actionExecutionDurationSum,
      outputProcessingDurationSum = outputProcessingDurationSum + other.outputProcessingDurationSum,
      maxDurationTrace = maxDurationTuple._3,
      maxDurationTimestamp = maxDurationTuple._2,
      maxDuration = maxDurationTuple._1,
      maxInputProcessingDurationTrace = maxInputProcessingDurationTuple._3,
      maxInputProcessingDurationTimestamp = maxInputProcessingDurationTuple._2,
      maxInputProcessingDuration = maxInputProcessingDurationTuple._1,
      maxActionExecutionDurationTrace = maxActionExecutionDurationTuple._3,
      maxActionExecutionDurationTimestamp = maxActionExecutionDurationTuple._2,
      maxActionExecutionDuration = maxActionExecutionDurationTuple._1,
      maxOutputProcessingDurationTrace = maxOutputProcessingDurationTuple._3,
      maxOutputProcessingDurationTimestamp = maxOutputProcessingDurationTuple._2,
      maxOutputProcessingDuration = maxOutputProcessingDurationTuple._1)
  }

  def meanTime(totalTime: DurationNanos, totalCount: Long): DurationNanos = {
    if (totalCount == 0L)
      0L
    else
      totalTime / totalCount
  }

  def meanDuration(totalCount: Long): DurationNanos =
    meanTime(durationSum, totalCount)

  def meanInputProcessingDuration(totalCount: Long): DurationNanos =
    meanTime(inputProcessingDurationSum, totalCount)

  def meanActionExecutionDuration(totalCount: Long): DurationNanos =
    meanTime(actionExecutionDurationSum, totalCount)

  def meanOutputProcessingDuration(totalCount: Long): DurationNanos =
    meanTime(outputProcessingDurationSum, totalCount)
}

/**
 * Monitored throughput, invocations per second, measured over a short time window.
 */
case class InvocationRateMetrics(
  totalInvocationRate: Rate = 0.0,
  bytesWrittenRate: BytesRate = 0.0,
  bytesReadRate: BytesRate = 0.0)

/**
 * Peak throughput, invocations per second, measured over a short time window.
 */
case class PeakInvocationRateMetrics(
  totalInvocationRate: Rate = 0.0,
  totalInvocationRateTimestamp: Timestamp = 0L,
  bytesWrittenRate: Rate = 0.0,
  bytesWrittenRateTimestamp: Timestamp = 0L,
  bytesReadRate: Rate = 0.0,
  bytesReadRateTimestamp: Timestamp = 0L) {

  def concatenate(other: PeakInvocationRateMetrics): PeakInvocationRateMetrics = {

    import PlayStats.max
    val maxTotal = max(totalInvocationRate, totalInvocationRateTimestamp, other.totalInvocationRate, other.totalInvocationRateTimestamp)
    val maxBytesWritten = max(bytesWrittenRate, bytesWrittenRateTimestamp, other.bytesWrittenRate, other.bytesWrittenRateTimestamp)
    val maxBytesRead = max(bytesReadRate, bytesReadRateTimestamp, other.bytesReadRate, other.bytesReadRateTimestamp)

    PeakInvocationRateMetrics(
      totalInvocationRate = maxTotal._1,
      totalInvocationRateTimestamp = maxTotal._2,
      bytesWrittenRate = maxBytesWritten._1,
      bytesWrittenRateTimestamp = maxBytesWritten._2,
      bytesReadRate = maxBytesRead._1,
      bytesReadRateTimestamp = maxBytesRead._2)

  }
}

trait SortHelpers[T] {
  def extractor: PlayRequestSummary ⇒ T
  def lt: Ordering[T]
  private final val ltOrdering: Ordering[PlayRequestSummary] = lt.on(extractor)
  private final val gteOrdering: Ordering[PlayRequestSummary] = lt.reverse.on(extractor)
  def asc(in: SeqView[PlayRequestSummary, Seq[_]]): SeqView[PlayRequestSummary, Seq[_]] = in.sorted(ltOrdering)
  def dec(in: SeqView[PlayRequestSummary, Seq[_]]): SeqView[PlayRequestSummary, Seq[_]] = in.sorted(gteOrdering)
}

sealed class PlayStatsSort[T](val extractor: PlayRequestSummary ⇒ T)(implicit val lt: Ordering[T]) extends SortHelpers[T]
object PlayStatsSorts {
  private final def totalTimeMillis(prs: PlayRequestSummary): Long = (prs.end.nanoTime - prs.start.nanoTime) * 1000 * 1000
  case object TimeSort extends PlayStatsSort[(Long, Long)](x ⇒ (x.start.millis, x.start.nanoTime))
  case object ControllerSort extends PlayStatsSort[String](_.invocationInfo.controller)
  case object MethodSort extends PlayStatsSort[String](_.invocationInfo.httpMethod)
  case object ResponseCodeSort extends PlayStatsSort[Int](_.response.resultInfo.httpResponseCode)
  case object InvocationTimeSort extends PlayStatsSort[Long](totalTimeMillis)
  case object PathSort extends PlayStatsSort[String](_.invocationInfo.path)
}
