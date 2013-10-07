/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.analyzer

import akka.actor.{ ActorRef, Cancellable }
import activator.analytics.data._
import activator.analytics.data.BasicTypes._
import activator.analytics.metrics.RateMetric
import activator.analytics.repository.{ PlayStatsRepository, DuplicatesRepository }
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import java.util.concurrent.TimeUnit._
import scala.collection.immutable.Queue
import scala.collection.mutable.HashMap
import scala.concurrent.duration._
import scala.unchecked
import scala.util.control.Exception._

object PlayStatsAnalyzer {
  case object Flush
}

class PlayStatsAnalyzer(
  autoFlushInterval: Long,
  playRequestSummaryStore: ActorRef,
  playStatsRepository: PlayStatsRepository,
  val duplicatesRepository: DuplicatesRepository,
  val traceRepository: TraceRetrievalRepository) extends StatsAnalyzer with EventStatsGrouping {
  import PlayTraceTreeHelpers._
  import PlayStatsAnalyzer._

  case class TraceRecord(traces: Seq[TraceEvent], success: Boolean, timestamp: Long = System.currentTimeMillis)

  type STATS = PlayStats
  type GROUP = GroupBy
  override type BUF = PlayStatsBuffer
  val statsName: String = "PlayStats"
  val alertDispatcher: Option[ActorRef] = None
  var scheduler: Cancellable = null
  // Keeping this code commented out deliberately.  Needed for debugging
  // val seenTraces: HashMap[UUID, ArrayBuffer[TraceRecord]] = new HashMap[UUID, ArrayBuffer[TraceRecord]]

  def initScheduler(): Unit = {
    cleanupScheduler()
    scheduler = context.system.scheduler.schedule(autoFlushInterval.milliseconds, autoFlushInterval.milliseconds, self, Flush)(context.system.dispatcher)
  }

  def cleanupScheduler(): Unit =
    if (scheduler != null) {
      scheduler.cancel()
      scheduler = null
    }

  override def preStart(): Unit =
    initScheduler()

  override def postStop(): Unit =
    cleanup()

  def cleanup(): Unit = {
    cleanupScheduler()
    flush()
  }

  def flush(): Unit = {
    storeStats()
    clearState()
  }

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[PlayStats]): Unit = playStatsRepository.save(stats)
    def findBy(group: GroupBy): Option[PlayStats] = playStatsRepository.findBy(group.timeRange, group.scope)
  }

  def create(group: GroupBy): PlayStats = {
    PlayStats(group.timeRange, group.scope)
  }

  def createStatsBuffer(stats: PlayStats): PlayStatsBuffer = {
    val previousTimeRange = TimeRange.rangeFor(stats.timeRange.startTime - 1, stats.timeRange.rangeType)
    val previous = statsBuffers.get(GroupBy(previousTimeRange, stats.scope)).collect { case b: PlayStatsBufferImpl ⇒ b }
    new PlayStatsBufferImpl(stats, previous)
  }

  // Keeping this code commented out deliberately.  Needed for debugging
  // def recordTrace(traces: Seq[TraceEvent], success: Boolean): Unit = {
  //   seenTraces.get(traces.traceId) match {
  //     case Some(e) ⇒ e += TraceRecord(traces, success)
  //     case None    ⇒ seenTraces += (traces.traceId -> ArrayBuffer(TraceRecord(traces, success)))
  //   }
  // }

  // def dumpSeenTraceSummary(): Unit = {
  //   println("**** traces seen: " + seenTraces.size)
  //   println("     failed:      " + seenTraces.count { case (_, v) ⇒ v.forall(x ⇒ !x.success) })
  //   println("     succeeded:   " + seenTraces.count { case (_, v) ⇒ v.exists(x ⇒ x.success) })
  // }

  // def dumpSeenTracesFor(traceId: UUID): Unit = {
  //   println(">>>> [%s]: ".format(traceId))
  //   for (ts ← seenTraces.get(traceId).map(_.sortBy(_.timestamp)); t ← ts) {
  //     if (!t.success) {
  //       println("      **failed** time: " + t.timestamp)
  //       // println("      events: ")
  //       // for (e ← t.traces) println("        " + e)
  //     } else {
  //       println("      success    time: " + t.timestamp)
  //     }
  //   }
  // }

  def produceStatistics(traces: Seq[TraceEvent]): Unit = {
    val startTime = System.currentTimeMillis

    val summary = for {
      nettyStart ← traces.traceOfNettyHttpReceivedStart
      traceId = nettyStart.trace
      node = nettyStart.node
      host = nettyStart.host
      summaryType = traces.summaryType
      errorMessage = traces.errorMessage
      stackTrace = traces.stackTrace
      requestInfo ← traces.requestInfo
      info ← traces.invocationInfo
      result ← traces.result
      resultGenerationStart ← traces.traceOfActionResultGenerationStart
      resultGenerationEnd ← traces.traceOfActionResultGenerationEnd
      resultTrace ← traces.traceOfActionResponseAnnotation
      lastBytesOut ← traces.lastTraceOfBytesOut
      bytesIn ← traces.bytesReceived
      bytesOut ← traces.bytesSent
    } yield (PlayRequestSummary(traceId,
      node,
      host,
      summaryType,
      requestInfo,
      TimeMark(nettyStart.timestamp, nettyStart.nanoTime),
      TimeMark(lastBytesOut.timestamp, lastBytesOut.nanoTime),
      lastBytesOut.nanoTime - nettyStart.nanoTime,
      info,
      result,
      traces.tracesForActionAsyncResult.sortBy(_.nanoTime).map(_.nanoTime),
      resultGenerationStart.nanoTime - nettyStart.nanoTime,
      TimeMark(resultGenerationStart.timestamp, resultGenerationStart.nanoTime),
      resultGenerationEnd.nanoTime - resultGenerationStart.nanoTime,
      TimeMark(resultTrace.timestamp, resultTrace.nanoTime),
      lastBytesOut.nanoTime - resultTrace.nanoTime,
      bytesIn,
      bytesOut,
      errorMessage,
      stackTrace), nettyStart)

    // Keeping this code commented out deliberately.  Needed for debugging
    // if (summary.isEmpty) recordTrace(traces, false)
    // else recordTrace(traces, true)

    // dumpSeenTraceSummary()
    // dumpSeenTracesFor(traces.traceId)

    summary.foreach {
      case (s, start) ⇒
        playRequestSummaryStore ! s
        for (group ← allGroups(start, Some(s.invocationInfo.pattern), Some(s.invocationInfo.controller + "." + s.invocationInfo.method))) {
          statsBufferFor(group) += (s, start.sampled)
          addCurrentlyTouched(group, start.timestamp)
        }
        store(start.timestamp)

        store()

        log.debug("Produced [{}] from [{}] trace events. It took [{}] ms",
          statsName, traces.size, System.currentTimeMillis - startTime)
    }
  }

  def receive = {
    case TraceEvents(events) ⇒
      produceStatistics(events)
    case Flush ⇒
      flush()
  }

  def allGroups(event: TraceEvent, playPattern: Option[String] = None, playController: Option[String] = None): Seq[GroupBy] = {
    groupsFor(event, playPattern, playController)
  }

  def groupsFor(event: TraceEvent, playPattern: Option[String] = None, playController: Option[String] = None): Seq[GroupBy] = {
    val node = Some(event.node)
    val timeRanges = allTimeRanges(event.timestamp)

    groupCombinations(timeRanges, None, Set.empty[String], node, None, None, playPattern, playController)
  }

  def createGroup(
    timeRange: TimeRange,
    path: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None,
    playPattern: Option[String] = None,
    playController: Option[String] = None): GroupBy =
    GroupBy(timeRange, Scope(path, tag, node, dispatcher, actorSystem, playPattern, playController))

  trait PlayStatsBuffer extends StatsBuffer {
    def +=(summary: PlayRequestSummary, sampled: Int): Unit
  }

  class PlayStatsBufferImpl(initial: PlayStats, var previous: Option[PlayStatsBufferImpl])
    extends PlayStatsBuffer {

    var latestTraceEventTimestamp = initial.metrics.latestTraceEventTimestamp
    var latestInvocationTimestamp = initial.metrics.latestInvocationTimestamp
    var invocationCount = initial.metrics.counts.invocationCount
    var simpleResultCount = initial.metrics.counts.simpleResultCount
    var chunkedResultCount = initial.metrics.counts.chunkedResultCount
    var asyncResultCount = initial.metrics.counts.asyncResultCount
    var bytesRead = initial.metrics.bytesRead
    var bytesWritten = initial.metrics.bytesWritten
    var exceptionCount = initial.metrics.counts.exceptionCount
    var handlerNotFoundCount = initial.metrics.counts.handlerNotFoundCount
    var badRequestCount = initial.metrics.counts.badRequestCount
    val countsByStatus: HashMap[String, Long] = HashMap(initial.metrics.counts.countsByStatus.toSeq: _*)
    var durationSum = initial.metrics.processingStats.durationSum
    var inputProcessingDurationSum = initial.metrics.processingStats.inputProcessingDurationSum
    var actionExecutionDurationSum = initial.metrics.processingStats.actionExecutionDurationSum
    var outputProcessingDurationSum = initial.metrics.processingStats.outputProcessingDurationSum
    var maxDurationTrace = initial.metrics.processingStats.maxDurationTrace
    var maxDurationTimestamp = initial.metrics.processingStats.maxDurationTimestamp
    var maxDuration = initial.metrics.processingStats.maxDuration
    var maxInputProcessingDurationTrace = initial.metrics.processingStats.maxInputProcessingDurationTrace
    var maxInputProcessingDurationTimestamp = initial.metrics.processingStats.maxInputProcessingDurationTimestamp
    var maxInputProcessingDuration = initial.metrics.processingStats.maxInputProcessingDuration
    var maxActionExecutionDurationTrace = initial.metrics.processingStats.maxActionExecutionDurationTrace
    var maxActionExecutionDurationTimestamp = initial.metrics.processingStats.maxActionExecutionDurationTimestamp
    var maxActionExecutionDuration = initial.metrics.processingStats.maxActionExecutionDuration
    var maxOutputProcessingDurationTrace = initial.metrics.processingStats.maxOutputProcessingDurationTrace
    var maxOutputProcessingDurationTimestamp = initial.metrics.processingStats.maxOutputProcessingDurationTimestamp
    var maxOutputProcessingDuration = initial.metrics.processingStats.maxOutputProcessingDuration

    // initialize the history with values from the previous buffer
    val totalInvocationRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.totalInvocationRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakInvocationRateMetrics.totalInvocationRate,
      initialPeakRateTimestamp = initial.metrics.peakInvocationRateMetrics.totalInvocationRateTimestamp,
      maxHistory = RateMetric.DefaultMaxHistory)
    val bytesWrittenRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.bytesWrittenRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakInvocationRateMetrics.bytesWrittenRate,
      initialPeakRateTimestamp = initial.metrics.peakInvocationRateMetrics.bytesWrittenRateTimestamp,
      maxHistory = RateMetric.DefaultMaxHistory)
    val bytesReadRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.bytesReadRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakInvocationRateMetrics.bytesReadRate,
      initialPeakRateTimestamp = initial.metrics.peakInvocationRateMetrics.bytesReadRateTimestamp,
      maxHistory = RateMetric.DefaultMaxHistory)

    previous = None

    def +=(summary: PlayRequestSummary, sampled: Int): Unit = {
      summary match {
        case PlayRequestSummary(traceId, node, host, summaryType, requestInfo, TimeMark(startMillis, startNanoTime), TimeMark(endMillis, endNanoTime), duration,
          invocationInfo, response, asyncResponseNanoTimes, inputProcessingDuration, TimeMark(actionExecutionMillis, actionExecutionNanoTime),
          actionExecutionDuration, TimeMark(outputProcessingMillis, outputProcessingNanoTime), outputProcessingDuration, bytesIn, bytesOut, errorMessage, stackTrace) ⇒
          latestTraceEventTimestamp = math.max(latestTraceEventTimestamp, startMillis)
          latestInvocationTimestamp = math.max(latestInvocationTimestamp, actionExecutionMillis)
          totalInvocationRate += (startMillis, sampled)
          invocationCount += 1
          asyncResultCount += asyncResponseNanoTimes.length
          bytesWritten += bytesOut
          bytesRead += bytesIn
          bytesWrittenRate += (startMillis, bytesOut.intValue)
          bytesReadRate += (startMillis, bytesIn.intValue)
          durationSum += duration
          inputProcessingDurationSum += inputProcessingDuration
          actionExecutionDurationSum += actionExecutionDuration
          outputProcessingDurationSum += outputProcessingDuration

          summaryType match {
            case RequestSummaryType.Normal          ⇒
            case RequestSummaryType.Exception       ⇒ exceptionCount += 1
            case RequestSummaryType.HandlerNotFound ⇒ handlerNotFoundCount += 1
            case RequestSummaryType.BadRequest      ⇒ badRequestCount += 1
          }

          val statusCode = response.resultInfo.httpResponseCode.toString
          countsByStatus.get(statusCode) match {
            case None    ⇒ countsByStatus += (statusCode -> 1L)
            case Some(v) ⇒ countsByStatus += (statusCode -> (v + 1L))
          }

          if (duration > maxDuration) {
            maxDurationTrace = traceId
            maxDurationTimestamp = startMillis
            maxDuration = duration
          }

          if (inputProcessingDuration > maxInputProcessingDuration) {
            maxInputProcessingDurationTrace = traceId
            maxInputProcessingDurationTimestamp = startMillis
            maxInputProcessingDuration = inputProcessingDuration
          }

          if (actionExecutionDuration > maxActionExecutionDuration) {
            maxActionExecutionDurationTrace = traceId
            maxActionExecutionDurationTimestamp = actionExecutionMillis
            maxActionExecutionDuration = actionExecutionDuration
          }

          if (outputProcessingDuration > maxOutputProcessingDuration) {
            maxOutputProcessingDurationTrace = traceId
            maxOutputProcessingDurationTimestamp = outputProcessingMillis
            maxOutputProcessingDuration = outputProcessingDuration
          }

          response match {
            case _: ActionSimpleResult  ⇒ simpleResultCount += 1
            case _: ActionChunkedResult ⇒ chunkedResultCount += 1
          }
        case _ ⇒
      }
    }

    def toStats: PlayStats = {

      val peakInvocationRateMetrics = PeakInvocationRateMetrics(
        totalInvocationRate.peakRate,
        totalInvocationRate.peakRateTimestamp,
        bytesWrittenRate.peakRate,
        bytesWrittenRate.peakRateTimestamp,
        bytesReadRate.peakRate,
        bytesReadRate.peakRateTimestamp)

      val invocationRateMetrics = InvocationRateMetrics(
        totalInvocationRate.rate,
        bytesWrittenRate.rate,
        bytesReadRate.rate)

      val playStatsCounts = PlayStatsCounts(
        invocationCount,
        simpleResultCount,
        chunkedResultCount,
        asyncResultCount,
        countsByStatus.toMap,
        exceptionCount,
        handlerNotFoundCount,
        badRequestCount)

      val playProcessingStats = PlayProcessingStats(
        durationSum,
        inputProcessingDurationSum,
        actionExecutionDurationSum,
        outputProcessingDurationSum,
        maxDurationTrace,
        maxDurationTimestamp,
        maxDuration,
        maxInputProcessingDurationTrace,
        maxInputProcessingDurationTimestamp,
        maxInputProcessingDuration,
        maxActionExecutionDurationTrace,
        maxActionExecutionDurationTimestamp,
        maxActionExecutionDuration,
        maxOutputProcessingDurationTrace,
        maxOutputProcessingDurationTimestamp,
        maxOutputProcessingDuration)

      val playStatsMetrics = PlayStatsMetrics(
        latestTraceEventTimestamp,
        latestInvocationTimestamp,
        bytesWritten,
        bytesRead,
        playProcessingStats,
        playStatsCounts,
        invocationRateMetrics,
        peakInvocationRateMetrics)

      PlayStats(
        initial.timeRange,
        initial.scope,
        playStatsMetrics,
        initial.id)
    }
  }

  case class GroupBy(timeRange: TimeRange, scope: Scope)

}
