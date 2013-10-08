/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.analyzer.Analyzer.SimpleAck
import activator.analytics.data._
import activator.analytics.data.BasicTypes.Timestamp
import activator.analytics.data.TimeRange._
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.{ Map ⇒ MutableMap }
import scala.concurrent.forkjoin.ThreadLocalRandom

trait ActorAnalyzerHelpers { this: StatsAnalyzer ⇒
  def actorInfo(event: TraceEvent): Option[ActorInfo] = event.annotation match {
    case _: ActorTold | _: ActorAsked | _: RemoteMessageSent | _: EventStreamAnnotation ⇒
      findSendFromActor(event).map(_.annotation.asInstanceOf[ActorAnnotation].info)
    case x: ActorAnnotation         ⇒ Some(x.info)
    case x: SysMsgAnnotation        ⇒ Some(x.info)
    case x: RemoteMessageAnnotation ⇒ Some(x.info)
    case _                          ⇒ None
  }

  /**
   * Returns the ActorReceived event, looking back in the trace.
   */
  def findSendFromActor(sendEvent: TraceEvent): Option[TraceEvent] = {
    @tailrec
    def find(event: TraceEvent, counter: Int): Option[TraceEvent] = {
      traceRepository.event(event.parent) match {
        case None ⇒ None
        case Some(parent) if parent.annotation.isInstanceOf[RemoteMessageReceived] ⇒ None
        case Some(parent) if parent.annotation.isInstanceOf[ActorReceived] ⇒ Some(parent)
        case Some(parent) if counter > StatsAnalyzer.MaxEventsInTrace ⇒ None
        case Some(parent) ⇒ find(parent, counter + 1)
      }
    }

    find(sendEvent, 0)
  }

  /**
   * Checks if the ActorTold event was generated due to ActorAsked, looking back in the trace.
   */
  def isToldPartOfAsk(toldEvent: TraceEvent): Boolean = {
    @tailrec
    def find(event: TraceEvent, counter: Int): Boolean = {
      traceRepository.event(event.parent) match {
        case None ⇒ false
        case Some(parent) if parent.annotation.isInstanceOf[ActorAsked] ⇒ true
        case Some(parent) if parent.annotation.isInstanceOf[ActorReceived] ⇒ false
        case Some(parent) if counter > StatsAnalyzer.MaxEventsInTrace ⇒ false
        case Some(parent) ⇒ find(parent, counter + 1)
      }
    }

    find(toldEvent, 0)
  }

  def isTempActor(event: TraceEvent): Boolean = {
    actorInfo(event).exists(isTempActor(_))
  }

  def isTempActor(info: ActorInfo): Boolean = info.path.contains("/temp/$")
}

/**
 * There are a lot of similarities between analyzers and this
 * trait extract the common parts in template method pattern, i.e.
 * subclass implement only specific parts. Depending on if the
 * statistics is based on TraceEvent or Span there are more concrete
 * traits for the two types, SpanStatsAnalyzer and EventStatsAnalyzer.
 */
trait StatsAnalyzer extends Actor with ActorLogging {
  type STATS
  type GROUP
  type BUF <: StatsBuffer

  def traceRepository: TraceRetrievalRepository

  def statsRepository: StatsRepository

  def alertDispatcher: Option[ActorRef]

  def statsName: String

  implicit def system: ActorSystem = context.system

  lazy val statsStoreWorker = context.actorOf(Props(new StatsStoreWorker(statsRepository)).
    withDispatcher(StatsAnalyzer.StatsStoreWorkerDispatcherId), name = "statsStoreWorker")

  var nextStoreTimestamp: Timestamp = 0L
  val actorPathLevelTimeRanges = AnalyzeExtension(system).ActorPathTimeRanges
  val storeTimeIntervalMillis = AnalyzeExtension(system).StoreTimeInterval
  val storeLimit = AnalyzeExtension(system).StoreLimit
  val useAllTime = AnalyzeExtension(system).StoreUseAllTime

  protected def updateNextStoreTimestamp(eventTimestamp: Timestamp): Unit = {
    nextStoreTimestamp = eventTimestamp + storeTimeIntervalMillis
  }

  val stats = MutableMap[GROUP, STATS]()
  val statsBuffers = MutableMap[GROUP, BUF]()
  private val currentlyTouched = MutableMap[GROUP, Long]()
  private var currentlyTouchedSize = 0
  private val previouslyTouched = MutableMap[GROUP, Long]()

  def addCurrentlyTouched(group: GROUP, timestamp: Timestamp): Unit = {
    if (!currentlyTouched.isDefinedAt(group))
      currentlyTouchedSize += 1
    currentlyTouched(group) = timestamp
  }

  def clearCurrentlyTouched(): Unit = {
    currentlyTouched.clear()
    currentlyTouchedSize = 0
  }

  def clearState() {
    previouslyTouched ++= currentlyTouched
    clearCurrentlyTouched()

    if (!previouslyTouched.isEmpty) {
      val latest = previouslyTouched.values.max
      val purgeTimestamp = latest - purgeUnusedMillis
      for ((group, timestamp) ← previouslyTouched if timestamp < purgeTimestamp) {
        stats -= group
        statsBuffers -= group
        previouslyTouched -= group
      }

    }
  }

  def purgeUnusedMillis: Long = StatsAnalyzer.PurgeUnusedMillis

  def statsFor(group: GROUP): STATS = {
    stats.getOrElseUpdate(group, loadStat(group))
  }

  def loadStat(group: GROUP): STATS = {
    statsRepository.findBy(group).getOrElse(create(group))
  }

  def create(group: GROUP): STATS

  def statsBufferFor(group: GROUP): BUF = {
    statsBuffers.getOrElseUpdate(group, createStatsBuffer(statsFor(group)))
  }

  def createStatsBuffer(stats: STATS): BUF

  def allTimeRanges(time: Long): Seq[TimeRange] = {
    val minute = minuteRange(time)
    val hour = hourRange(time)
    val day = dayRange(time)
    val month = monthRange(time)
    if (useAllTime) Seq(minute, hour, day, month, TimeRange())
    else Seq(minute, hour, day, month)
  }

  def store(): Unit = if (storeTimeIntervalMillis == 0L || currentlyTouchedSize >= storeLimit) {
    storeStats()
    clearState()
  }

  def store(timestamp: Timestamp): Unit = if (storeTimeIntervalMillis != 0L) {
    if (nextStoreTimestamp == 0L) updateNextStoreTimestamp(timestamp)
    if (timestamp >= nextStoreTimestamp) {
      storeStats()
      clearState()
      updateNextStoreTimestamp(timestamp)
    }
  }

  def storeStats() {
    if (storeTimeIntervalMillis == 0L) {
      // Sync save stats in repository
      val changedStats = currentlyTouched.keys.map(group ⇒ statsBufferFor(group).toStats)
      statsRepository.save(changedStats)
    } else {
      // Async save stats in repository
      val changedStats: Map[GROUP, STATS] = currentlyTouched.map {
        case (group, _) ⇒ (group -> statsBufferFor(group).toStats)
      }.toMap
      statsStoreWorker ! StatsStoreWorkerJob(changedStats)
    }
  }

  trait StatsBuffer {
    def toStats: STATS
  }

  trait StatsRepository {
    def save(stats: Iterable[STATS]): Unit

    def findBy(group: GROUP): Option[STATS]
  }

  class StatsStoreWorker(statsRepository: StatsRepository) extends Actor with ActorLogging {
    import context.dispatcher
    val flushDelay = AnalyzeExtension(system).StoreFlushDelay
    var latest = Map.empty[GROUP, STATS]

    override def preStart(): Unit =
      context.system.scheduler.scheduleOnce(flushDelay, self, FlushStatsStoreWorker)

    // override postRestart so we don't call preStart and schedule a new flush message
    override def postRestart(reason: Throwable): Unit = ()

    def receive = {
      case StatsStoreWorkerJob(stats) ⇒
        latest ++= stats

      case FlushStatsStoreWorker ⇒
        if (latest.nonEmpty) {
          val startTime = System.currentTimeMillis
          statsRepository.save(latest.values)
          for (actor ← alertDispatcher) actor ! latest.values.toList
          if (log.isDebugEnabled)
            log.debug("Stored [{}] [{}], it took [{}] ms ", latest.size, statsName,
              (System.currentTimeMillis - startTime))
          latest = Map.empty[GROUP, STATS]
        }
        context.system.scheduler.scheduleOnce(flushDelay, self, FlushStatsStoreWorker)

    }
  }

  case class StatsStoreWorkerJob(stats: Map[GROUP, STATS])
}

case object FlushStatsStoreWorker

object StatsAnalyzer {
  final val PurgeUnusedMillis = TimeUnit.MILLISECONDS.convert(3, TimeUnit.MINUTES)
  val MaxEventsInTrace = SpanBuilder.MaxEventsInTrace
  val StatsStoreWorkerDispatcherId = "atmos.analytics.store-dispatcher"
}

trait SpanStatsAnalyzer extends StatsAnalyzer {
  type BUF = SpanStatsBuffer
  lazy val duplicateAnalyzerName = self.path.name

  def receive = {
    case Spans(spans) ⇒
      produceStatistics(spans)
      sender ! SimpleAck
  }

  def produceStatistics(spans: Seq[Span]) {
    val startTime = System.currentTimeMillis

    spans.foreach { span ⇒
      produceStatistics(span)
      store(span.startTime)
    }

    store()

    log.debug("Produced [{}] from [{}] spans. It took [{}] ms",
      statsName, spans.size, System.currentTimeMillis - startTime)
  }

  def produceStatistics(span: Span) {
    for (group ← allGroups(span)) {
      statsBufferFor(group) += span
      addCurrentlyTouched(group, span.startTime)
    }
  }

  def allGroups(span: Span): Seq[GROUP]

  trait SpanStatsBuffer extends StatsBuffer {
    def +=(span: Span): Unit
  }

}

trait SpanStatsGrouping { self: SpanStatsAnalyzer ⇒
  def groupCombinations(
    timeRanges: Seq[TimeRange],
    spanTypeName: String,
    path: Option[String],
    tags: Set[String],
    node: Option[String],
    dispatcher: Option[String],
    actorSystem: Option[String]): Seq[GROUP] = {

    val result = ArrayBuffer.empty[GROUP]
    for (t ← timeRanges) {
      result += createGroup(t, spanTypeName)
      result += createGroup(t, spanTypeName, node = node)
      result += createGroup(t, spanTypeName, actorSystem = actorSystem)
      result += createGroup(t, spanTypeName, node = node, actorSystem = actorSystem)
      for (x ← path; if actorPathLevelTimeRanges.contains(t.timeRangeValue)) {
        result += createGroup(t, spanTypeName, path = Some(x))
        result += createGroup(t, spanTypeName, path = Some(x), node = node)
        result += createGroup(t, spanTypeName, path = Some(x), actorSystem = actorSystem)
        result += createGroup(t, spanTypeName, path = Some(x), node = node, actorSystem = actorSystem)
      }
      for (x ← dispatcher) {
        result += createGroup(t, spanTypeName, dispatcher = dispatcher, node = node, actorSystem = actorSystem)
      }
      for (tag ← tags) {
        result += createGroup(t, spanTypeName, tag = Some(tag))
      }
    }
    result.toList
  }

  def createGroup(
    timeRange: TimeRange,
    spanTypeName: String,
    path: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None): GROUP
}

trait EventStatsAnalyzerBase extends StatsAnalyzer {

  type BUF <: EventStatsBuffer

  lazy val duplicateAnalyzerName = self.path.name

  def receive = {
    case TraceEvents(events) ⇒
      produceStatistics(events)
      sender ! SimpleAck
  }

  def produceStatistics(events: Seq[TraceEvent]) {
    val startTime = System.currentTimeMillis

    events.foreach { event ⇒
      produceStatistics(event)
      store(event.timestamp)
    }

    store()

    log.debug("Produced [{}] from [{}] trace events. It took [{}] ms",
      statsName, events.size, System.currentTimeMillis - startTime)
  }

  def produceStatistics(event: TraceEvent): Boolean

  def isInteresting(event: TraceEvent): Boolean

  trait EventStatsBuffer extends StatsBuffer {
    def +=(event: TraceEvent): Unit
  }

}

trait EventsWithPlayScope extends ActorAnalyzerHelpers { this: EventStatsAnalyzerBase ⇒
  def allGroups(event: TraceEvent, playPattern: Option[String] = None, playController: Option[String] = None): Seq[GROUP]

  /**
   * Returns true if the event is of interest and not processed before.
   */
  def produceStatistics(event: TraceEvent): Boolean = {
    if (isInteresting(event) && !isTempActor(event)) {
      for (group ← allGroups(event)) {
        statsBufferFor(group) += event
        addCurrentlyTouched(group, event.timestamp)
      }
      true
    } else {
      false
    }
  }

}

trait EventsNoPlayScope extends ActorAnalyzerHelpers { this: EventStatsAnalyzerBase ⇒
  def allGroups(event: TraceEvent): Seq[GROUP]

  /**
   * Returns true if the event is of interest and not processed before.
   */
  def produceStatistics(event: TraceEvent): Boolean = {
    if (isInteresting(event) && !isTempActor(event)) {
      for (group ← allGroups(event)) {
        statsBufferFor(group) += event
        addCurrentlyTouched(group, event.timestamp)
      }
      true
    } else {
      false
    }
  }
}

trait EventStatsWithPlayScopeAnalyzer extends EventStatsAnalyzerBase with EventsWithPlayScope

trait EventStatsAnalyzer extends EventStatsAnalyzerBase with EventsNoPlayScope

trait EventStatsGrouping { self: StatsAnalyzer ⇒

  def groupCombinations(
    timeRanges: Seq[TimeRange],
    path: Option[String],
    tags: Set[String],
    node: Option[String],
    dispatcher: Option[String],
    actorSystem: Option[String],
    playPattern: Option[String],
    playController: Option[String]): Seq[GROUP] = {

    val result = ArrayBuffer.empty[GROUP]

    def playScopeCombinations(force: Boolean, body: (Option[String], Option[String]) ⇒ Unit): Unit = {
      if (playPattern.isDefined && playController.isDefined) body(playPattern, playController)
      if (playPattern.isDefined) body(playPattern, None)
      if (playController.isDefined) body(None, playController)
      if (force || playPattern.isDefined || playController.isDefined) body(None, None)
    }

    def groupCombinationsWithPlayScopes(playPattern: Option[String], playController: Option[String], force: Boolean = false): Unit = {
      for (t ← timeRanges) {
        playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, playPattern = pp, playController = pc))
        if (force || node.isDefined) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, node = node, playPattern = pp, playController = pc))
        if (force || (node.isDefined && actorSystem.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, node = node, actorSystem = actorSystem, playPattern = pp, playController = pc))
        if (force || (node.isDefined && actorSystem.isDefined && dispatcher.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, node = node, actorSystem = actorSystem, dispatcher = dispatcher, playPattern = pp, playController = pc))

        if (actorPathLevelTimeRanges.contains(t.timeRangeValue) && path.isDefined) {
          playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, playPattern = pp, playController = pc))
          if (force || node.isDefined) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, node = node, playPattern = pp, playController = pc))
          if (force || (node.isDefined && actorSystem.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, node = node, actorSystem = actorSystem, playPattern = pp, playController = pc))
          if (force || (node.isDefined && actorSystem.isDefined && dispatcher.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, node = node, actorSystem = actorSystem, dispatcher = dispatcher, playPattern = pp, playController = pc))
          for (tag ← tags) {
            playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, tag = Some(tag), playPattern = pp, playController = pc))
            if (force || node.isDefined) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, tag = Some(tag), node = node, playPattern = pp, playController = pc))
            if (force || (node.isDefined && actorSystem.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, tag = Some(tag), node = node, actorSystem = actorSystem, playPattern = pp, playController = pc))
            if (force || (node.isDefined && actorSystem.isDefined && dispatcher.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, path = path, tag = Some(tag), node = node, actorSystem = actorSystem, dispatcher = dispatcher, playPattern = pp, playController = pc))
          }
        }

        for (tag ← tags) {
          playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, tag = Some(tag), playPattern = pp, playController = pc))
          if (force || node.isDefined) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, tag = Some(tag), node = node, playPattern = pp, playController = pc))
          if (force || (node.isDefined && actorSystem.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, tag = Some(tag), node = node, actorSystem = actorSystem, playPattern = pp, playController = pc))
          if (force || (node.isDefined && actorSystem.isDefined && dispatcher.isDefined)) playScopeCombinations(force, (pp, pc) ⇒ result += createGroup(t, tag = Some(tag), node = node, actorSystem = actorSystem, dispatcher = dispatcher, playPattern = pp, playController = pc))
        }
      }
    }

    // This assumes that play data will be backfilled
    (playPattern, playController) match {
      case (pp @ Some(_), None)         ⇒ groupCombinationsWithPlayScopes(pp, None)
      case (None, pc @ Some(_))         ⇒ groupCombinationsWithPlayScopes(None, pc)
      case (pp @ Some(_), pc @ Some(_)) ⇒ groupCombinationsWithPlayScopes(pp, pc)
      case (_, _)                       ⇒ groupCombinationsWithPlayScopes(None, None, true)
    }

    result
  }

  def createGroup(
    timeRange: TimeRange,
    path: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None,
    playPattern: Option[String] = None,
    playController: Option[String] = None): GROUP
}
