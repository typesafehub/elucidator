/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.data.BasicTypes.Timestamp
import activator.analytics.data.TimeRange.minuteRange
import activator.analytics.metrics.RateMetric
import activator.analytics.repository.MessageRateTimeSeriesRepository
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

class MessageRateTimeSeriesAnalyzer(
  pathLevel: Option[Boolean],
  messageRateTimeSeriesRepository: MessageRateTimeSeriesRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends EventStatsAnalyzer with EventStatsGrouping {

  type STATS = MessageRateTimeSeries
  type GROUP = GroupBy
  override type BUF = MessageRateTimeSeriesBuffer
  val statsName: String = "MessageRateTimeSeries" + (pathLevel match {
    case None        ⇒ ""
    case Some(true)  ⇒ "PathLevel"
    case Some(false) ⇒ "AggregatedLevel"
  })

  // this should not be configurable, a new point is created for each "store"
  override val storeTimeIntervalMillis = 1000L

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[MessageRateTimeSeries]): Unit = {
      messageRateTimeSeriesRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[MessageRateTimeSeries] = {
      messageRateTimeSeriesRepository.findBy(group.timeRange, group.scope)
    }
  }

  def create(group: GroupBy): MessageRateTimeSeries = {
    MessageRateTimeSeries(group.timeRange, group.scope)
  }

  def createStatsBuffer(stats: MessageRateTimeSeries): MessageRateTimeSeriesBuffer = {
    val previousTimeRange = TimeRange.rangeFor(stats.timeRange.startTime - 1, stats.timeRange.rangeType)
    val previous = statsBuffers.get(GroupBy(previousTimeRange, stats.scope))
    new MessageRateTimeSeriesBuffer(stats, previous)
  }

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case _: ActorReceived         ⇒ true
    case _: ActorTold             ⇒ true
    case _: ActorAsked            ⇒ true
    case _: RemoteMessageSent     ⇒ true
    case _: RemoteMessageReceived ⇒ true
    case _                        ⇒ false
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = {
    val info = actorInfo(event)

    if (info.isEmpty) {
      Nil
    } else {
      val node = Some(event.node)
      val actorSystem = Some(event.actorSystem)
      val path = info.map(_.path)
      val dispatcher = info.flatMap(_.dispatcher)
      val tags = info.map(_.tags).getOrElse(Set.empty[String])

      val minute = minuteRange(event.timestamp)
      val timeRanges = Seq(minute)

      val combinations = groupCombinations(timeRanges, path, tags, node, dispatcher, actorSystem, None, None)
      pathLevel match {
        case None      ⇒ combinations
        case Some(use) ⇒ combinations filter { _.scope.path.isDefined == use }
      }
    }
  }

  def createGroup(
    timeRange: TimeRange,
    actorPath: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None,
    playPattern: Option[String] = None,
    playController: Option[String] = None): GroupBy =
    GroupBy(timeRange, Scope(actorPath, tag, node, dispatcher, actorSystem))

  class MessageRateTimeSeriesBuffer(initial: MessageRateTimeSeries, var previous: Option[MessageRateTimeSeriesBuffer]) extends EventStatsBuffer {

    def rateMetricMaxHistory = initial.scope.path match {
      case None    ⇒ RateMetric.DefaultMaxHistory
      case Some(_) ⇒ RateMetric.SmallMaxHistory
    }

    // initialize the history with values from the previous buffer
    var current = initial
    var timestamp: Timestamp = 0L
    val totalMessageRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.totalMessageRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)
    val receiveRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.receiveRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)
    val tellRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.tellRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)
    val askRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.askRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)
    val remoteSendRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.remoteSendRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)
    val remoteReceiveRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.remoteReceiveRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)
    val bytesWrittenRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.bytesWrittenRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)
    val bytesReadRate: RateMetric = new RateMetric(
      initialHistory = previous.toSeq.map(_.bytesReadRate.historyWindow).flatten,
      calculatePeakRate = false,
      maxHistory = rateMetricMaxHistory)

    // previous ActorStatsBuffer can now be garbage collected
    previous = None

    def +=(event: TraceEvent): Unit = event.annotation match {
      case x: ActorReceived ⇒
        totalMessageRate += (event.timestamp, event.sampled)
        receiveRate += (event.timestamp, event.sampled)
        timestamp = event.timestamp
      case x: ActorTold if !isToldPartOfAsk(event) ⇒
        tellRate += (event.timestamp, event.sampled)
        totalMessageRate += (event.timestamp, event.sampled)
        timestamp = event.timestamp
      case x: ActorAsked ⇒
        askRate += (event.timestamp, event.sampled)
        totalMessageRate += (event.timestamp, event.sampled)
        timestamp = event.timestamp
      case x: RemoteMessageSent ⇒
        val b = x.messageSize * event.sampled
        bytesWrittenRate += (event.timestamp, b)
        remoteSendRate += (event.timestamp, event.sampled)
        timestamp = event.timestamp
      case x: RemoteMessageReceived ⇒
        val b = x.messageSize * event.sampled
        bytesReadRate += (event.timestamp, b)
        remoteReceiveRate += (event.timestamp, event.sampled)
        timestamp = event.timestamp
      case _ ⇒
    }

    def toStats = {
      val currentRates = MessageRateMetrics(
        totalMessageRate = totalMessageRate.rate,
        receiveRate = receiveRate.rate,
        tellRate = tellRate.rate,
        askRate = askRate.rate,
        remoteSendRate = remoteSendRate.rate,
        remoteReceiveRate = remoteReceiveRate.rate,
        bytesWrittenRate = bytesWrittenRate.rate,
        bytesReadRate = bytesReadRate.rate)

      val points =
        if (timestamp == 0L) {
          current.points
        } else {
          val point = MessageRateTimeSeriesPoint(timestamp, currentRates)
          (current.points :+ point).sortBy(_.timestamp)
        }
      current = current.copy(points = points)
      current
    }

  }

  case class GroupBy(timeRange: TimeRange, scope: Scope)

}

