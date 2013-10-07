/**
 *  Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.data

import com.typesafe.atmos.uuid.UUID

case class ErrorStats(
  timeRange: TimeRange,
  node: Option[String] = None,
  actorSystem: Option[String] = None,
  metrics: ErrorStatsMetrics = new ErrorStatsMetrics(),
  id: UUID = new UUID()) {
}

object ErrorStats extends Chunker[ErrorStats] {
  def concatenate(stats: Iterable[ErrorStats], timeRange: TimeRange, node: Option[String], actorSystem: Option[String]): ErrorStats = {
    val metrics = stats.map(_.metrics).foldLeft(ErrorStatsMetrics()) { _ concatenate _ }
    ErrorStats(timeRange, node, actorSystem, metrics)
  }

  def chunk(
    minNumberOfChunks: Int,
    maxNumberOfChunks: Int,
    stats: Iterable[ErrorStats],
    timeRange: TimeRange,
    node: Option[String],
    actorSystem: Option[String]): Seq[ErrorStats] = {

    chunk(minNumberOfChunks, maxNumberOfChunks, stats, timeRange,
      s ⇒ s.timeRange,
      t ⇒ ErrorStats(t, node),
      (t, s) ⇒ ErrorStats.concatenate(s, t, node, actorSystem))
  }
}

case class ErrorStatsMetrics(
  counts: Counts = new Counts(),
  deviations: DeviationDetails = new DeviationDetails()) {

  def concatenate(other: ErrorStatsMetrics): ErrorStatsMetrics = {
    ErrorStatsMetrics(
      counts = counts.concatenate(other.counts),
      deviations = deviations.concatenate(other.deviations))
  }
}

case class Counts(errors: Int = 0, warnings: Int = 0, deadLetters: Int = 0, unhandledMessages: Int = 0, deadlocks: Int = 0) {
  def concatenate(other: Counts): Counts = {
    Counts(
      errors + other.errors,
      warnings + other.warnings,
      deadLetters + other.deadLetters,
      unhandledMessages + other.unhandledMessages,
      deadlocks + other.deadlocks)
  }

  def total: Int = errors + warnings + deadLetters + unhandledMessages + deadlocks
}

case class DeviationDetail(eventId: UUID, traceId: UUID, message: String, timestamp: Long)

case class DeviationDetails(
  errors: List[DeviationDetail] = List[DeviationDetail](),
  warnings: List[DeviationDetail] = List[DeviationDetail](),
  deadLetters: List[DeviationDetail] = List[DeviationDetail](),
  unhandledMessages: List[DeviationDetail] = List[DeviationDetail](),
  deadlockedThreads: List[DeviationDetail] = List[DeviationDetail]()) {

  def concatenate(other: DeviationDetails): DeviationDetails = {
    DeviationDetails(
      ((other.errors ++ errors) sortBy { _.timestamp }).reverse,
      ((other.warnings ++ warnings) sortBy { _.timestamp }).reverse,
      ((other.deadLetters ++ deadLetters) sortBy { _.timestamp }).reverse,
      ((other.unhandledMessages ++ unhandledMessages) sortBy { _.timestamp }).reverse,
      ((other.deadlockedThreads ++ deadlockedThreads) sortBy { _.timestamp }).reverse)
  }

  def truncateToMax(limit: Int): DeviationDetails = truncateToMax(limit, limit, limit, limit, limit)

  def truncateToMax(maxErrors: Int, maxWarnings: Int, maxDeadLetters: Int, maxUnhandledMessages: Int, maxDeadlocks: Int): DeviationDetails = {
    DeviationDetails(
      errors take maxErrors,
      warnings take maxWarnings,
      deadLetters take maxDeadLetters,
      unhandledMessages take maxUnhandledMessages,
      deadlockedThreads take maxDeadlocks)
  }

  def filterByTime(from: Long): DeviationDetails = {
    DeviationDetails(
      errors filter { p ⇒ p.timestamp > from },
      warnings filter { p ⇒ p.timestamp > from },
      deadLetters filter { p ⇒ p.timestamp > from },
      unhandledMessages filter { p ⇒ p.timestamp > from },
      deadlockedThreads filter { p ⇒ p.timestamp > from })
  }
}
