/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import activator.analytics.data._
import activator.analytics.data.BasicTypes.Timestamp
import activator.analytics.data.TimeRange.minuteRange
import activator.analytics.metrics.PairMetric
import activator.analytics.repository.MailboxTimeSeriesRepository
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

class MailboxTimeSeriesAnalyzer(
  mailboxTimeSeriesRepository: MailboxTimeSeriesRepository,
  val traceRepository: TraceRetrievalRepository,
  val alertDispatcher: Option[ActorRef])
  extends EventStatsAnalyzer {

  type STATS = MailboxTimeSeries
  type GROUP = GroupBy
  override type BUF = MailboxTimeSeriesBuffer
  val statsName: String = "MailboxTimeSeries"

  // this should not be configurable, a new point is created for each "store"
  override val storeTimeIntervalMillis = 1000L
  override val useRandomStoreInterval = false
  var lastStoredTimestamp: Timestamp = 0L

  val statsRepository: StatsRepository = new StatsRepository {
    def save(stats: Iterable[MailboxTimeSeries]): Unit = {
      mailboxTimeSeriesRepository.save(stats)
    }

    def findBy(group: GroupBy): Option[MailboxTimeSeries] = {
      mailboxTimeSeriesRepository.findBy(group.timeRange, group.path)
    }
  }

  def create(group: GroupBy): MailboxTimeSeries = {
    MailboxTimeSeries(group.timeRange, group.path)
  }

  def createStatsBuffer(stats: MailboxTimeSeries): MailboxTimeSeriesBuffer = {
    val previousTimeRange = TimeRange.rangeFor(stats.timeRange.startTime - 1, stats.timeRange.rangeType)
    val previous = statsBuffers.get(GroupBy(previousTimeRange, stats.path))
    new MailboxTimeSeriesBuffer(stats, previous)
  }

  def isInteresting(event: TraceEvent): Boolean = event.annotation match {
    case x: ActorCreated   ⇒ true
    case x: ActorReceived  ⇒ true
    case x: ActorTold      ⇒ true
    case x: SystemStarted  ⇒ true
    case x: SystemShutdown ⇒ true
    case _                 ⇒ false
  }

  def allGroups(event: TraceEvent): Seq[GroupBy] = {
    event.annotation match {
      case x: ActorAnnotation ⇒
        val minute = minuteRange(event.timestamp)
        // FIXME #377 this should perhaps be grouped by node/as/path
        List(GroupBy(minute, x.info.path))
      case _ ⇒ Nil
    }
  }

  override def produceStatistics(events: Seq[TraceEvent]) {
    val startTime = System.currentTimeMillis

    events.foreach(produceStatistics(_))

    log.debug("Produced [{}] from [{}] trace events. It took [{}] ms",
      statsName, events.size, System.currentTimeMillis - startTime)
  }

  override def produceStatistics(event: TraceEvent): Boolean = {
    val result = super.produceStatistics(event)

    if (lastStoredTimestamp == 0L) {
      lastStoredTimestamp = event.timestamp
    }

    event.annotation match {
      case x: ActorReceived ⇒
        if (event.timestamp - lastStoredTimestamp >= storeTimeIntervalMillis) {
          storeStats()
          clearState()
          lastStoredTimestamp = event.timestamp
        }
      case _ ⇒
    }

    result
  }

  class MailboxTimeSeriesBuffer(initial: MailboxTimeSeries, var previous: Option[MailboxTimeSeriesBuffer]) extends EventStatsBuffer {

    var current = initial
    var timestamp: Timestamp = 0L

    // important to only keep reference to the needed values, not the whole MailboxTimeSeriesBuffer
    // we need previous metrics values when initializing new PairMetric
    def previousPairMetricsHistory: Map[MailboxKey, Iterable[PairMetric.Single]] = {
      previous match {
        case None ⇒ Map.empty
        case Some(buf) ⇒
          for ((path, metrics) ← buf.mailbox.toMap) yield (path, metrics.snapshot)
      }
    }

    val mailbox = new Mailbox(previousPairMetricsHistory)
    // previous MailboxTimeSeriesBuffer can now be garbage collected
    previous = None

    def +=(event: TraceEvent): Unit = event.annotation match {
      case x: ActorCreated ⇒
        mailbox.reset(MailboxKey(event.node, event.actorSystem, x.info.path))
        timestamp = event.timestamp
      case x: ActorTold ⇒
        mailbox.pairMetricsFor(MailboxKey(event.node, event.actorSystem, x.info.path)).add(
          PairMetric.Single(event.id, expectedFirst = true, event.timestamp, event.nanoTime, event.sampled))
      case x: ActorReceived ⇒
        mailbox.pairMetricsFor(MailboxKey(event.node, event.actorSystem, x.info.path)).add(
          PairMetric.Single(event.parent, expectedFirst = false, event.timestamp, event.nanoTime, event.sampled))
        timestamp = event.timestamp
      case _: SystemStarted | _: SystemShutdown ⇒
        mailbox.resetAll(event.node, event.actorSystem)
      case _ ⇒
    }

    def toStats = {
      mailbox.clearOld(timestamp)

      val points =
        if (timestamp == 0L) {
          current.points
        } else {
          val point = MailboxTimeSeriesPoint(
            timestamp,
            size = mailbox.mailboxSize,
            waitTime = mailbox.timeInMailbox)
          (current.points :+ point).sortBy(_.timestamp)
        }

      current = current.copy(points = points)
      current
    }

  }

  case class GroupBy(timeRange: TimeRange, path: String)

}

