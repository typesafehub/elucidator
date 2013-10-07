/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.repository.{ DuplicatesRepository, ErrorStatsRepository }
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import scala.collection.mutable.ArrayBuffer

class ErrorStatsAnalyzer(
  errorStatsRepository: ErrorStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val duplicatesRepository: DuplicatesRepository,
  val alertDispatcher: Option[ActorRef])
  extends EventStatsAnalyzer {

  type STATS = ErrorStats
  type GROUP = GroupBy
  override type BUF = ErrorStatsBuffer
  val statsName: String = "ErrorStats"

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[ErrorStats]): Unit = {
      errorStatsRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[ErrorStats] = {
      errorStatsRepository.findBy(group.timeRange, group.node, group.actorSystem)
    }
  }

  def createStatsBuffer(stats: ErrorStats): ErrorStatsBuffer = new ErrorStatsBuffer(stats)

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case x: DeadlockedThreads     ⇒ true
    case x: EventStreamAnnotation ⇒ true
    case _                        ⇒ false
  }

  def create(group: GroupBy): ErrorStats = {
    ErrorStats(group.timeRange, group.node, group.actorSystem)
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = {
    val result = ArrayBuffer.empty[GroupBy]
    val timeRanges = allTimeRanges(event.timestamp)

    for (timeRange ← timeRanges) {
      result += GroupBy(timeRange)
      result += GroupBy(timeRange, node = Some(event.node))
      result += GroupBy(timeRange, actorSystem = Some(event.actorSystem))
      result += GroupBy(timeRange, node = Some(event.node), actorSystem = Some(event.actorSystem))
    }

    result.toList
  }

  class ErrorStatsBuffer(initial: ErrorStats) extends EventStatsBuffer {
    var errorCount = initial.metrics.counts.errors
    var warningCount = initial.metrics.counts.warnings
    var deadLetterCount = initial.metrics.counts.deadLetters
    var unhandledMessageCount = initial.metrics.counts.unhandledMessages
    var deadlockCount = initial.metrics.counts.deadlocks
    var errorDeviations = initial.metrics.deviations.errors
    var warningDeviations = initial.metrics.deviations.warnings
    var deadLetterDeviations = initial.metrics.deviations.deadLetters
    var unhandledMessageDeviations = initial.metrics.deviations.unhandledMessages
    var deadlockedThreadsDeviations = initial.metrics.deviations.deadlockedThreads

    def +=(event: TraceEvent) = event.annotation match {
      case x: DeadlockedThreads ⇒
        deadlockCount += 1
        appendDeadlockedThreadsDeviation(DeviationDetail(event.id, event.trace, x.message, event.timestamp))
      case x: EventStreamError ⇒
        errorCount += 1
        appendErrorDeviation(DeviationDetail(event.id, event.trace, x.message, event.timestamp))
      case x: EventStreamWarning ⇒
        warningCount += 1
        appendWarningDeviation(DeviationDetail(event.id, event.trace, x.message, event.timestamp))
      case x: EventStreamDeadLetter ⇒
        deadLetterCount += 1
        appendDeadLetterDeviation(DeviationDetail(event.id, event.trace, x.message, event.timestamp))
      case x: EventStreamUnhandledMessage ⇒
        unhandledMessageCount += 1
        appendUnhandledMessageDeviation(DeviationDetail(event.id, event.trace, x.message, event.timestamp))
      case _ ⇒
    }

    def appendErrorDeviation(deviation: DeviationDetail) = {
      if (errorDeviations.size >= AnalyzeExtension(context.system).MaxErrorDeviations) {
        errorDeviations = errorDeviations.dropRight(1)
      }

      errorDeviations = deviation +: errorDeviations
    }

    def appendWarningDeviation(deviation: DeviationDetail) = {
      if (warningDeviations.size >= AnalyzeExtension(context.system).MaxWarningDeviations) {
        warningDeviations = warningDeviations.dropRight(1)
      }

      warningDeviations = deviation +: warningDeviations
    }

    def appendDeadLetterDeviation(deviation: DeviationDetail) = {
      if (deadLetterDeviations.size >= AnalyzeExtension(context.system).MaxDeadLetterDeviations) {
        deadLetterDeviations = deadLetterDeviations.dropRight(1)
      }

      deadLetterDeviations = deviation +: deadLetterDeviations
    }

    def appendUnhandledMessageDeviation(deviation: DeviationDetail) = {
      if (unhandledMessageDeviations.size >= AnalyzeExtension(context.system).MaxUnhandledMessageDeviations) {
        unhandledMessageDeviations = unhandledMessageDeviations.dropRight(1)
      }

      unhandledMessageDeviations = deviation +: unhandledMessageDeviations
    }

    def appendDeadlockedThreadsDeviation(deviation: DeviationDetail) = {
      if (deadlockedThreadsDeviations.size >= AnalyzeExtension(context.system).MaxDeadlockDeviations) {
        deadlockedThreadsDeviations = deadlockedThreadsDeviations.dropRight(1)
      }

      deadlockedThreadsDeviations = deviation +: deadlockedThreadsDeviations
    }

    def toStats = {
      val counts = Counts(errorCount, warningCount, deadLetterCount, unhandledMessageCount, deadlockCount)
      val deviations = DeviationDetails(errorDeviations, warningDeviations, deadLetterDeviations, unhandledMessageDeviations, deadlockedThreadsDeviations)
      val metrics = ErrorStatsMetrics(counts, deviations)
      ErrorStats(initial.timeRange,
        initial.node,
        initial.actorSystem,
        metrics,
        initial.id)
    }
  }

  case class GroupBy(timeRange: TimeRange, node: Option[String] = None, actorSystem: Option[String] = None)
}
