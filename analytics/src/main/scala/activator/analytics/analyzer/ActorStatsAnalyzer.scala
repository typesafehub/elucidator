/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */

package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.data.BasicTypes._
import activator.analytics.metrics.{ RateMetric, PairMetric }
import activator.analytics.repository.ActorStatsRepository
import com.typesafe.trace._
import com.typesafe.trace.store.TraceRetrievalRepository

import java.util.concurrent.TimeUnit._
import scala.collection.immutable.Queue
import activator.analytics.AnalyticsExtension

class ActorStatsAnalyzer(
  pathLevel: Option[Boolean],
  actorStatsRepository: ActorStatsRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends EventStatsAnalyzer with EventStatsGrouping {

  type STATS = ActorStats
  type GROUP = GroupBy
  override type BUF = ActorStatsBuffer
  val statsName: String = "ActorStats" + (pathLevel match {
    case None        ⇒ ""
    case Some(true)  ⇒ "PathLevel"
    case Some(false) ⇒ "AggregatedLevel"
  })

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[ActorStats]): Unit = actorStatsRepository.save(stats)
    def findBy(group: GroupBy): Option[ActorStats] = actorStatsRepository.findBy(group.timeRange, group.scope)
  }

  def create(group: GroupBy): ActorStats = {
    ActorStats(group.timeRange, group.scope)
  }

  def createStatsBuffer(stats: ActorStats): ActorStatsBuffer = {
    val previousTimeRange = TimeRange.rangeFor(stats.timeRange.startTime - 1, stats.timeRange.rangeType)
    val previous = statsBuffers.get(GroupBy(previousTimeRange, stats.scope)).collect { case b: ActorStatsBufferImpl ⇒ b }
    new ActorStatsBufferImpl(stats, previous)
  }

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case _: ActorAnnotation       ⇒ true
    case _: SysMsgCompleted       ⇒ true
    case _: RemoteMessageSent     ⇒ true
    case _: RemoteMessageReceived ⇒ true
    case _: EventStreamAnnotation ⇒ true
    case _                        ⇒ false
  }

  override def produceStatistics(event: TraceEvent): Boolean = {
    if (super.produceStatistics(event)) {
      for (group ← actorSendTargetGroups(event)) {
        statsBufferFor(group).addActorSendTarget(event)
        addCurrentlyTouched(group, event.timestamp)
      }
      true
    } else {
      false
    }
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = {
    val info = actorInfo(event)
    groupsFor(event, info)
  }

  def actorSendTargetGroups(event: TraceEvent): Seq[GroupBy] = {
    val info = event.annotation match {
      case x: ActorTold if x.info.remote          ⇒ None
      case x: ActorTold if x.info.router          ⇒ None
      case x: ActorTold if (!isTempActor(x.info)) ⇒ Some(x.info)
      case _                                      ⇒ None
    }

    groupsFor(event, info)
  }

  def groupsFor(event: TraceEvent, info: Option[ActorInfo]): Seq[GroupBy] = {
    if (info.isEmpty) {
      Nil
    } else {
      val node = Some(event.node)
      val actorSystem = Some(event.actorSystem)
      val path = info.map(_.path)
      val dispatcher = info.flatMap(_.dispatcher)
      val tags = info.map(_.tags).getOrElse(Set.empty[String])
      val timeRanges = allTimeRanges(event.timestamp)

      val combinations = groupCombinations(timeRanges, path, tags, node, dispatcher, actorSystem, None, None)
      pathLevel match {
        case None      ⇒ combinations
        case Some(use) ⇒ combinations filter { _.scope.path.isDefined == use }
      }
    }
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
    GroupBy(timeRange, Scope(path, tag, node, dispatcher, actorSystem))

  trait ActorStatsBuffer extends EventStatsBuffer {
    def addActorSendTarget(event: TraceEvent): Unit
  }

  class ActorStatsBufferImpl(initial: ActorStats, var previous: Option[ActorStatsBufferImpl])
    extends ActorStatsBuffer {

    var latestTraceEventTimestamp = initial.metrics.latestTraceEventTimestamp
    var latestMessageTimestamp = initial.metrics.latestMessageTimestamp
    var createdCount = initial.metrics.counts.createdCount
    var stoppedCount = initial.metrics.counts.stoppedCount
    var processedMessagesCount = initial.metrics.counts.processedMessagesCount
    var tellMessagesCount = initial.metrics.counts.tellMessagesCount
    var askMessagesCount = initial.metrics.counts.askMessagesCount
    var restartCount = initial.metrics.counts.restartCount
    var failedCount = initial.metrics.counts.failedCount
    var mailboxSizeSum = initial.metrics.mailbox.mailboxSizeSum
    var timeInMailboxSum = initial.metrics.mailbox.timeInMailboxSum
    var bytesRead = initial.metrics.bytesRead
    var bytesWritten = initial.metrics.bytesWritten
    var errorCount = initial.metrics.counts.errorCount
    var warningCount = initial.metrics.counts.warningCount
    var deadLetterCount = initial.metrics.counts.deadLetterCount
    var unhandledMessageCount = initial.metrics.counts.unhandledMessageCount
    var errorDeviations = Queue[DeviationDetail]()
    var warningDeviations = Queue[DeviationDetail]()
    var deadLetterDeviations = Queue[DeviationDetail]()
    var lockedThreads = Set[String]()
    var unhandledMessageDeviations = Queue[DeviationDetail]()

    def rateMetricMaxHistory = initial.scope.path match {
      case None    ⇒ RateMetric.DefaultMaxHistory
      case Some(_) ⇒ RateMetric.SmallMaxHistory
    }

    // initialize the history with values from the previous buffer
    val totalMessageRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.totalMessageRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.totalMessageRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.totalMessageRateTimestamp,
      maxHistory = rateMetricMaxHistory)
    val receiveRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.receiveRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.receiveRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.receiveRateTimestamp,
      maxHistory = rateMetricMaxHistory)
    val tellRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.tellRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.tellRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.tellRateTimestamp,
      maxHistory = rateMetricMaxHistory)
    val askRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.askRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.askRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.askRateTimestamp,
      maxHistory = rateMetricMaxHistory)
    val remoteSendRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.remoteSendRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.remoteSendRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.remoteSendRateTimestamp,
      maxHistory = rateMetricMaxHistory)
    val remoteReceiveRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.remoteReceiveRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.remoteReceiveRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.remoteReceiveRateTimestamp,
      maxHistory = rateMetricMaxHistory)
    val bytesWrittenRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.bytesWrittenRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.bytesWrittenRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.bytesWrittenRateTimestamp,
      maxHistory = rateMetricMaxHistory)
    val bytesReadRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.bytesReadRate.historyWindow).flatten,
      initialPeakRate = initial.metrics.peakMessageRateMetrics.bytesReadRate,
      initialPeakRateTimestamp = initial.metrics.peakMessageRateMetrics.bytesReadRateTimestamp,
      maxHistory = rateMetricMaxHistory)

    // important to only keep reference to the needed values, not the whole ActorStatsBuffer
    // we need previous metrics values when initializing new PairMetric
    def previousPairMetricsHistory: Map[MailboxKey, Iterable[PairMetric.Single]] = {
      previous match {
        case None ⇒ Map.empty
        case Some(buf) ⇒
          for ((key, metrics) ← buf.mailbox.toMap) yield (key, metrics.snapshot)
      }
    }
    val mailbox = new Mailbox(previousPairMetricsHistory)
    // previous ActorStatsBuffer can now be garbage collected
    previous = None

    def +=(event: TraceEvent): Unit = {
      latestTraceEventTimestamp = math.max(latestTraceEventTimestamp, event.timestamp)
      event.annotation match {
        case x: ActorCreated ⇒
          createdCount += 1
          mailbox.reset(MailboxKey(event.node, event.actorSystem, x.info.path))
        case x: ActorReceived ⇒
          processedMessagesCount += event.sampled
          latestMessageTimestamp = math.max(latestMessageTimestamp, event.timestamp)
          totalMessageRate += (event.timestamp, event.sampled)
          receiveRate += (event.timestamp, event.sampled)
          val mailboxMetrics = mailbox.pairMetricsFor(MailboxKey(event.node, event.actorSystem, x.info.path))
          mailboxSizeSum += mailboxMetrics.count
          mailboxMetrics.add(
            PairMetric.Single(event.parent, expectedFirst = false, event.timestamp, event.nanoTime, event.sampled))
          // TODO not correct to use previousDuration when there is no match, i.e. ActorReceived arrives before ActorTold
          timeInMailboxSum += MICROSECONDS.convert(mailboxMetrics.previousDuration, NANOSECONDS)
        case x: ActorTold if !isToldPartOfAsk(event) && !x.info.router ⇒
          tellMessagesCount += event.sampled
          tellRate += (event.timestamp, event.sampled)
          totalMessageRate += (event.timestamp, event.sampled)
        case x: ActorAsked ⇒
          askMessagesCount += event.sampled
          askRate += (event.timestamp, event.sampled)
          totalMessageRate += (event.timestamp, event.sampled)
        case SysMsgCompleted(info, r: RecreateSysMsg) ⇒
          restartCount += 1
        case SysMsgCompleted(info, TerminateSysMsg) ⇒
          stoppedCount += 1
        case x: ActorFailed ⇒
          failedCount += 1
        case x: RemoteMessageSent ⇒
          val b = x.messageSize * event.sampled
          bytesWritten += b
          bytesWrittenRate += (event.timestamp, b)
          remoteSendRate += (event.timestamp, event.sampled)
        case x: RemoteMessageReceived ⇒
          val b = x.messageSize * event.sampled
          bytesRead += b
          bytesReadRate += (event.timestamp, b)
          remoteReceiveRate += (event.timestamp, event.sampled)
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
    }

    def appendErrorDeviation(deviation: DeviationDetail) = {
      if (errorDeviations.size >= AnalyticsExtension(context.system).MaxErrorDeviations) {
        errorDeviations = errorDeviations.dropRight(1)
      }

      errorDeviations = deviation +: errorDeviations
    }

    def appendWarningDeviation(deviation: DeviationDetail) = {
      if (warningDeviations.size >= AnalyticsExtension(context.system).MaxWarningDeviations) {
        warningDeviations = warningDeviations.dropRight(1)
      }

      warningDeviations = deviation +: warningDeviations
    }

    def appendDeadLetterDeviation(deviation: DeviationDetail) = {
      if (deadLetterDeviations.size >= AnalyticsExtension(context.system).MaxDeadLetterDeviations) {
        deadLetterDeviations = deadLetterDeviations.dropRight(1)
      }

      deadLetterDeviations = deviation +: deadLetterDeviations
    }

    def appendUnhandledMessageDeviation(deviation: DeviationDetail) = {
      if (unhandledMessageDeviations.size >= AnalyticsExtension(context.system).MaxUnhandledMessageDeviations) {
        unhandledMessageDeviations = unhandledMessageDeviations.dropRight(1)
      }

      unhandledMessageDeviations = deviation +: unhandledMessageDeviations
    }

    def addActorSendTarget(event: TraceEvent): Unit = event.annotation match {
      case x: ActorTold ⇒
        val mailboxMetrics = mailbox.pairMetricsFor(MailboxKey(event.node, event.actorSystem, x.info.path))
        mailboxMetrics.add(
          PairMetric.Single(event.id, expectedFirst = true, event.timestamp, event.nanoTime, event.sampled))
      case _ ⇒
    }

    def maxMailboxSize: (Int, Timestamp, MailboxKey) = {
      val metricsMax: (Int, Timestamp, MailboxKey) = mailbox.maxMailboxSize

      if (metricsMax._1 > initial.metrics.mailbox.maxMailboxSize) {
        metricsMax
      } else {
        (initial.metrics.mailbox.maxMailboxSize,
          timestampOrTimeRangeStart(initial.metrics.mailbox.maxMailboxSizeTimestamp),
          initial.metrics.mailbox.maxMailboxSizeAddress)
      }
    }

    def maxTimeInMailbox: (DurationNanos, Timestamp, MailboxKey) = {
      val metricsMax: (DurationNanos, Timestamp, MailboxKey) = mailbox.maxTimeInMailbox

      if (metricsMax._1 > initial.metrics.mailbox.maxTimeInMailbox) {
        metricsMax
      } else {
        (initial.metrics.mailbox.maxTimeInMailbox,
          timestampOrTimeRangeStart(initial.metrics.mailbox.maxTimeInMailboxTimestamp),
          initial.metrics.mailbox.maxTimeInMailboxAddress)
      }
    }

    def timestampOrTimeRangeStart(timestamp: Timestamp): Timestamp = {
      if (timestamp == 0L) initial.timeRange.startTime else timestamp
    }

    def toStats = {

      mailbox.clearOld(latestMessageTimestamp)

      val messageRateMetrics = MessageRateMetrics(
        totalMessageRate = totalMessageRate.rate,
        receiveRate = receiveRate.rate,
        tellRate = tellRate.rate,
        askRate = askRate.rate,
        remoteSendRate = remoteSendRate.rate,
        remoteReceiveRate = remoteReceiveRate.rate,
        bytesWrittenRate = bytesWrittenRate.rate,
        bytesReadRate = bytesReadRate.rate)

      val peakMessageRateMetrics = PeakMessageRateMetrics(
        totalMessageRate = totalMessageRate.peakRate,
        totalMessageRateTimestamp = totalMessageRate.peakRateTimestamp,
        receiveRate = receiveRate.peakRate,
        receiveRateTimestamp = receiveRate.peakRateTimestamp,
        tellRate = tellRate.peakRate,
        tellRateTimestamp = tellRate.peakRateTimestamp,
        askRate = askRate.peakRate,
        askRateTimestamp = askRate.peakRateTimestamp,
        remoteSendRate = remoteSendRate.peakRate,
        remoteSendRateTimestamp = remoteSendRate.peakRateTimestamp,
        remoteReceiveRate = remoteReceiveRate.peakRate,
        remoteReceiveRateTimestamp = remoteReceiveRate.peakRateTimestamp,
        bytesWrittenRate = bytesWrittenRate.peakRate,
        bytesWrittenRateTimestamp = bytesWrittenRate.peakRateTimestamp,
        bytesReadRate = bytesReadRate.peakRate,
        bytesReadRateTimestamp = bytesReadRate.peakRateTimestamp)

      val counts = ActorStatsCounts(
        restartCount = restartCount,
        stoppedCount = stoppedCount,
        failedCount = failedCount,
        createdCount = createdCount,
        processedMessagesCount = processedMessagesCount,
        tellMessagesCount = tellMessagesCount,
        askMessagesCount = askMessagesCount,
        errorCount = errorCount,
        warningCount = warningCount,
        deadLetterCount = deadLetterCount,
        unhandledMessageCount = unhandledMessageCount)

      val mailboxStats = MailboxStats(
        mailboxSizeSum = mailboxSizeSum,
        timeInMailboxSum = timeInMailboxSum,
        maxMailboxSize = maxMailboxSize._1,
        maxMailboxSizeTimestamp = maxMailboxSize._2,
        maxMailboxSizeAddress = maxMailboxSize._3,
        maxTimeInMailbox = maxTimeInMailbox._1,
        maxTimeInMailboxTimestamp = maxTimeInMailbox._2,
        maxTimeInMailboxAddress = maxTimeInMailbox._3)

      val deviationDetails = DeviationDetails(
        errors = errorDeviations.toList,
        warnings = warningDeviations.toList,
        deadLetters = deadLetterDeviations.toList,
        unhandledMessages = unhandledMessageDeviations.toList)

      val actorStatsMetrics = ActorStatsMetrics(
        latestTraceEventTimestamp = latestTraceEventTimestamp,
        latestMessageTimestamp = latestMessageTimestamp,
        bytesWritten = bytesWritten,
        bytesRead = bytesRead,
        counts = counts,
        mailbox = mailboxStats,
        messageRateMetrics = messageRateMetrics,
        peakMessageRateMetrics = peakMessageRateMetrics,
        deviationDetails = deviationDetails)

      ActorStats(
        initial.timeRange,
        initial.scope,
        actorStatsMetrics,
        initial.id)
    }

  }

  case class GroupBy(timeRange: TimeRange, scope: Scope)

}
