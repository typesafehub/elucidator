/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes._
import activator.analytics.data.TimeRangeType.AllTime
import activator.analytics.metrics.RateMetric
import com.typesafe.atmos.uuid.UUID
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS

/**
 * Aggregated statistics over the time period for the actors
 * that belong to the scope.
 */
case class ActorStats(
  timeRange: TimeRange,
  scope: Scope,
  metrics: ActorStatsMetrics = ActorStatsMetrics(),
  id: UUID = new UUID()) {

  /**
   * Average message rate over the time period.
   * N/A for TimeRange type AllTime.
   */
  def meanProcessedMessageRate: Rate = meanRate(metrics.counts.processedMessagesCount)

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

  private def meanRate(count: Long): Double = {
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

case class ActorStatsMetrics(
  latestTraceEventTimestamp: Timestamp = 0L,
  latestMessageTimestamp: Timestamp = 0L,
  bytesWritten: Bytes = 0L,
  bytesRead: Bytes = 0L,
  counts: ActorStatsCounts = ActorStatsCounts(),
  mailbox: MailboxStats = MailboxStats(),
  messageRateMetrics: MessageRateMetrics = MessageRateMetrics(),
  peakMessageRateMetrics: PeakMessageRateMetrics = PeakMessageRateMetrics(),
  deviationDetails: DeviationDetails = DeviationDetails()) {

  def concatenate(other: ActorStatsMetrics): ActorStatsMetrics = {

    ActorStatsMetrics(
      latestTraceEventTimestamp = math.max(latestTraceEventTimestamp, other.latestTraceEventTimestamp),
      latestMessageTimestamp = math.max(latestMessageTimestamp, other.latestMessageTimestamp),
      bytesWritten = bytesWritten + other.bytesWritten,
      bytesRead = bytesRead + other.bytesRead,
      counts = counts.concatenate(other.counts),
      mailbox = mailbox.concatenate(other.mailbox),
      messageRateMetrics = other.messageRateMetrics,
      peakMessageRateMetrics = peakMessageRateMetrics.concatenate(other.peakMessageRateMetrics),
      deviationDetails = deviationDetails.concatenate(other.deviationDetails))
  }

  def meanMailboxSize: Double = mailbox.meanMailboxSize(counts.processedMessagesCount)

  def meanTimeInMailbox: DurationNanos = mailbox.meanTimeInMailbox(counts.processedMessagesCount)

}

object ActorStats extends Chunker[ActorStats] {

  def concatenate(stats: Iterable[ActorStats], timeRange: TimeRange, scope: Scope): ActorStats = {
    val metrics = stats.map(_.metrics).foldLeft(ActorStatsMetrics()) { _ concatenate _ }
    ActorStats(timeRange, scope, metrics)
  }

  private[data] def max[T <% Ordered[T]](value1: T, timestamp1: Timestamp,
                                         value2: T, timestamp2: Timestamp): (T, Timestamp) = {
    if (value1 >= value2) (value1, timestamp1)
    else (value2, timestamp2)
  }

  def chunk(
    minNumberOfChunks: Int,
    maxNumberOfChunks: Int,
    stats: Iterable[ActorStats],
    timeRange: TimeRange,
    scope: Scope): Seq[ActorStats] = {

    chunk(minNumberOfChunks, maxNumberOfChunks, stats, timeRange,
      s ⇒ s.timeRange,
      t ⇒ ActorStats(t, scope),
      (t, s) ⇒ ActorStats.concatenate(s, t, scope))
  }

  // count how many parts of a Scope have been defined
  def scopedness(scope: Scope): Int = {
    (scope.productIterator map {
      case true    ⇒ 1
      case Some(_) ⇒ 1
      case _       ⇒ 0
    }).sum
  }

  // select only the stats with the most general scope
  def mostGeneralScope(stats: Seq[ActorStats]): Seq[ActorStats] = {
    stats.sortBy(stat ⇒ scopedness(stat.scope)).take(1)
  }
}

case class ActorStatsCounts(
  createdCount: Long = 0L,
  stoppedCount: Long = 0L,
  failedCount: Long = 0L,
  restartCount: Long = 0L,
  processedMessagesCount: Long = 0L,
  tellMessagesCount: Long = 0L,
  askMessagesCount: Long = 0L,
  errorCount: Long = 0L,
  warningCount: Long = 0L,
  deadLetterCount: Long = 0L,
  unhandledMessageCount: Long = 0L) {

  def concatenate(other: ActorStatsCounts): ActorStatsCounts = {
    ActorStatsCounts(
      createdCount = createdCount + other.createdCount,
      stoppedCount = stoppedCount + other.stoppedCount,
      failedCount = failedCount + other.failedCount,
      restartCount = restartCount + other.restartCount,
      processedMessagesCount = processedMessagesCount + other.processedMessagesCount,
      tellMessagesCount = tellMessagesCount + other.tellMessagesCount,
      askMessagesCount = askMessagesCount + other.askMessagesCount,
      errorCount = errorCount + other.errorCount,
      warningCount = warningCount + other.warningCount,
      deadLetterCount = deadLetterCount + other.deadLetterCount,
      unhandledMessageCount = unhandledMessageCount + other.unhandledMessageCount)
  }

  def deviationCount: Long = errorCount + warningCount + deadLetterCount + unhandledMessageCount
}

case class MailboxStats(
  mailboxSizeSum: Long = 0L,
  timeInMailboxSum: DurationMicros = 0L,
  maxMailboxSize: Int = 0,
  maxMailboxSizeTimestamp: Timestamp = 0L,
  maxMailboxSizeAddress: MailboxKey = MailboxKey("", "", ""),
  maxTimeInMailbox: DurationNanos = 0L,
  maxTimeInMailboxTimestamp: Timestamp = 0L,
  maxTimeInMailboxAddress: MailboxKey = MailboxKey("", "", "")) {

  def concatenate(other: MailboxStats): MailboxStats = {

    val maxMailboxSizeTuple =
      if (maxMailboxSize > other.maxMailboxSize)
        (maxMailboxSize, maxMailboxSizeTimestamp, maxMailboxSizeAddress)
      else
        (other.maxMailboxSize, other.maxMailboxSizeTimestamp, other.maxMailboxSizeAddress)

    val maxTimeInMailboxTuple =
      if (maxTimeInMailbox > other.maxTimeInMailbox)
        (maxTimeInMailbox, maxTimeInMailboxTimestamp, maxTimeInMailboxAddress)
      else
        (other.maxTimeInMailbox, other.maxTimeInMailboxTimestamp, other.maxTimeInMailboxAddress)

    MailboxStats(
      mailboxSizeSum = mailboxSizeSum + other.mailboxSizeSum,
      timeInMailboxSum = timeInMailboxSum + other.timeInMailboxSum,
      maxMailboxSize = maxMailboxSizeTuple._1,
      maxMailboxSizeTimestamp = maxMailboxSizeTuple._2,
      maxMailboxSizeAddress = maxMailboxSizeTuple._3,
      maxTimeInMailbox = maxTimeInMailboxTuple._1,
      maxTimeInMailboxTimestamp = maxTimeInMailboxTuple._2,
      maxTimeInMailboxAddress = maxTimeInMailboxTuple._3)
  }

  def meanMailboxSize(totalCount: Long): Double = {
    if (totalCount == 0.0)
      0
    else
      mailboxSizeSum.toDouble / totalCount
  }

  def meanTimeInMailbox(totalCount: Long): DurationNanos = {
    if (totalCount == 0L)
      0L
    else
      NANOSECONDS.convert(timeInMailboxSum / totalCount, MICROSECONDS)
  }
}

/**
 * Monitored throughput, messages per second, measured over a short time window.
 */
case class MessageRateMetrics(
  totalMessageRate: Rate = 0.0,
  receiveRate: Rate = 0.0,
  tellRate: Rate = 0.0,
  askRate: Rate = 0.0,
  remoteSendRate: Rate = 0.0,
  remoteReceiveRate: Rate = 0.0,
  bytesWrittenRate: BytesRate = 0.0,
  bytesReadRate: BytesRate = 0.0)

/**
 * Peak throughput, messages per second, measured over a short time window.
 */
case class PeakMessageRateMetrics(
  totalMessageRate: Rate = 0.0,
  totalMessageRateTimestamp: Timestamp = 0L,
  receiveRate: Rate = 0.0,
  receiveRateTimestamp: Timestamp = 0L,
  tellRate: Rate = 0.0,
  tellRateTimestamp: Timestamp = 0L,
  askRate: Rate = 0.0,
  askRateTimestamp: Timestamp = 0L,
  remoteSendRate: Rate = 0.0,
  remoteSendRateTimestamp: Timestamp = 0L,
  remoteReceiveRate: Rate = 0.0,
  remoteReceiveRateTimestamp: Timestamp = 0L,
  bytesWrittenRate: Rate = 0.0,
  bytesWrittenRateTimestamp: Timestamp = 0L,
  bytesReadRate: Rate = 0.0,
  bytesReadRateTimestamp: Timestamp = 0L) {

  def concatenate(other: PeakMessageRateMetrics): PeakMessageRateMetrics = {

    import ActorStats.max
    val maxTotal = max(totalMessageRate, totalMessageRateTimestamp, other.totalMessageRate, other.totalMessageRateTimestamp)
    val maxRecive = max(receiveRate, receiveRateTimestamp, other.receiveRate, other.receiveRateTimestamp)
    val maxSendOne = max(tellRate, tellRateTimestamp, other.tellRate, other.tellRateTimestamp)
    val maxAsk = max(askRate, askRateTimestamp, other.askRate, other.askRateTimestamp)
    val maxRemoteSend = max(remoteSendRate, remoteSendRateTimestamp, other.remoteSendRate, other.remoteSendRateTimestamp)
    val maxRemoteReceive = max(remoteReceiveRate, remoteReceiveRateTimestamp, other.remoteReceiveRate, other.remoteReceiveRateTimestamp)
    val maxBytesWritten = max(bytesWrittenRate, bytesWrittenRateTimestamp, other.bytesWrittenRate, other.bytesWrittenRateTimestamp)
    val maxBytesRead = max(bytesReadRate, bytesReadRateTimestamp, other.bytesReadRate, other.bytesReadRateTimestamp)

    PeakMessageRateMetrics(
      totalMessageRate = maxTotal._1,
      totalMessageRateTimestamp = maxTotal._2,
      receiveRate = maxRecive._1,
      receiveRateTimestamp = maxRecive._2,
      tellRate = maxSendOne._1,
      tellRateTimestamp = maxSendOne._2,
      askRate = maxAsk._1,
      askRateTimestamp = maxAsk._2,
      remoteSendRate = maxRemoteSend._1,
      remoteSendRateTimestamp = maxRemoteSend._2,
      remoteReceiveRate = maxRemoteReceive._1,
      remoteReceiveRateTimestamp = maxRemoteReceive._2,
      bytesWrittenRate = maxBytesWritten._1,
      bytesWrittenRateTimestamp = maxBytesWritten._2,
      bytesReadRate = maxBytesRead._1,
      bytesReadRateTimestamp = maxBytesRead._2)

  }
}

trait ActorStatsSort

object ActorStatsSorts {
  case object DeviationsSort extends ActorStatsSort
  case object ProcessedMessagesSort extends ActorStatsSort
  case object MaxTimeInMailboxSort extends ActorStatsSort
  case object MaxMailboxSizeSort extends ActorStatsSort
  case object ActorPath extends ActorStatsSort
  case object ActorName extends ActorStatsSort
}
