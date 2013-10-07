/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import AckAggregator._
import akka.actor._
import akka.actor.SupervisorStrategy._
import Analyzer._
import activator.analytics.data.{ Spans, SpanType }
import com.typesafe.atmos.subscribe.Notifications
import com.typesafe.atmos.subscribe.SubscribeMessages.{ Ack, EmptyAck }
import com.typesafe.atmos.trace.TraceEvent
import com.typesafe.atmos.trace.TraceEvents
import java.lang.System.currentTimeMillis
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._

class Analyzer(boot: AnalyzerBoot) extends Actor with ActorLogging {

  val settings = AnalyzeExtension(context.system)
  implicit val system = context.system

  // Can't use AllForOneStrategy because it will cause all children to stop when AckAggregator is stopped
  override val supervisorStrategy = OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1.minute) {
    case _: ActorInitializationException ⇒ Escalate
    case _: ActorKilledException         ⇒ Escalate
    case e: Exception                    ⇒ Restart
    case _                               ⇒ Escalate
  }

  val extension = AnalyzeExtension(context.system)
  val alertDispatcher = None

  lazy val playAnalyzerConfig: Option[PlayAnalyzerConfiguration] =
    if (settings.UsePlayStatsAnalyzer) {
      import boot._
      Some(PlayAnalyzerConfiguration(context.system, context.actorOf(_, _), dispatcherId, settings.StoreFlushDelay, settings.AccumulatorFlushDelay,
        settings.PlayStatsFlushInterval, settings.PlayTraceTreePurgeInterval, settings.PlayRequestSummaryPurgeInterval, playStatsRepository, playRequestSummaryRepository,
        duplicatesRepository, traceRepository, playTraceTreeRepository))
    } else None

  val eventListeners: Seq[ActorRef] = {
    import boot._
    if (settings.Partition.startsWith("span")) {
      Nil
    } else {
      val buf = ArrayBuffer[ActorRef]()
      if (settings.UseActorStatsAnalyzer) {
        (1 to 2) foreach { n ⇒
          buf += context.actorOf(Props(new ActorStatsAnalyzer(Some(n == 1), actorStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
            withDispatcher(dispatcherId), "actorStatsAnalyzer" + n)
        }
      }
      if (settings.UseMessageRateTimeSeriesAnalyzer) {
        (1 to 2) foreach { n ⇒
          buf += context.actorOf(Props(new MessageRateTimeSeriesAnalyzer(Some(n == 1), messageRateTimeSeriesRepository, traceRepository, duplicatesRepository, alertDispatcher)).
            withDispatcher(dispatcherId), "messageRateTimeSeriesAnalyzer" + n)
        }
      }
      if (settings.UseRemoteStatusStatsAnalyzer) buf +=
        context.actorOf(Props(new RemoteStatusStatsAnalyzer(remoteStatusStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
          withDispatcher(dispatcherId), "remoteStatusStatsAnalyzer")
      if (settings.UseMailboxTimeSeriesAnalyzer) buf +=
        context.actorOf(Props(new MailboxTimeSeriesAnalyzer(mailboxTimeSeriesRepository, traceRepository, duplicatesRepository, alertDispatcher)).
          withDispatcher(dispatcherId), "mailboxTimeSeriesAnalyzer")
      if (settings.UseDispatcherTimeSeriesAnalyzer) buf +=
        context.actorOf(Props(new DispatcherTimeSeriesAnalyzer(dispatcherTimeSeriesRepository, traceRepository, duplicatesRepository, alertDispatcher)).
          withDispatcher(dispatcherId), "dispatcherTimeSeriesAnalyzer")
      if (settings.UseSystemMetricsTimeSeriesAnalyzer) buf +=
        context.actorOf(Props(new SystemMetricsTimeSeriesAnalyzer(systemMetricsTimeSeriesRepository, traceRepository, duplicatesRepository, alertDispatcher)).
          withDispatcher(dispatcherId), "systemMetricsTimeSeriesAnalyzer")
      if (settings.UseErrorStatsAnalyzer) buf +=
        context.actorOf(Props(new ErrorStatsAnalyzer(errorStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
          withDispatcher(dispatcherId), "errorStatsAnalyzer")
      if (settings.UseRecordStatsAnalyzer) buf +=
        context.actorOf(Props(new RecordStatsAnalyzer(recordStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
          withDispatcher(dispatcherId), "recordStatsAnalyzer")
      if (settings.UseMetadataStatsAnalyzer) buf +=
        context.actorOf(Props(new MetadataStatsAnalyzer(metadataStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
          withDispatcher(dispatcherId), "metadataStatsAnalyzer")

      playAnalyzerConfig.foreach { c ⇒ buf += c.eventFilter }

      buf.toIndexedSeq
    }
  }

  val spanListeners: Seq[ActorRef] = {
    import boot._
    if (settings.Partition.startsWith("event")) {
      Nil
    } else {
      val buf = ArrayBuffer[ActorRef]()
      if (settings.UseSummarySpanStatsAnalyzer) {
        (1 to 2) foreach { n ⇒
          buf += context.actorOf(Props(new SummarySpanStatsAnalyzer(Some(n == 1), summarySpanStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
            withDispatcher(dispatcherId), "summarySpanStatsAnalyzer" + n)
        }
      }
      if (settings.UseHistogramSpanStatsAnalyzer) {
        (1 to 2) foreach { n ⇒
          buf += context.actorOf(Props(new HistogramSpanStatsAnalyzer(Some(n == 1), histogramSpanStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
            withDispatcher(dispatcherId), "histogramSpanStatsAnalyzer" + n)
        }
      }
      if (settings.UsePercentilesSpanStatsAnalyzer) {
        (1 to 2) foreach { n ⇒
          buf += context.actorOf(Props(new PercentilesSpanStatsAnalyzer(Some(n == 1), percentilesSpanStatsRepository, traceRepository, duplicatesRepository, alertDispatcher)).
            withDispatcher(dispatcherId), "percentilesSpanStatsAnalyzer" + n)
        }
      }
      if (settings.UseSpanTimeSeriesAnalyzer) {
        (1 to 2) foreach { n ⇒
          buf += context.actorOf(Props(new SpanTimeSeriesAnalyzer(Some(n == 1), spanTimeSeriesRepository, traceRepository, duplicatesRepository, alertDispatcher)).
            withDispatcher(dispatcherId), "spanTimeSeriesAnalyzer" + n)
        }
      }

      buf.toIndexedSeq
    }
  }

  val spanBuilders =
    for {
      spanType ← SpanType.allSpanTypes
      if !SpanBuilder.isIgnored(spanType)
    } yield SpanBuilder(spanType, boot.traceRepository)

  def receive = {
    case notifications: Notifications ⇒
      handleNotifications(notifications)
    case RetryNotifications(notifications, retryCount) ⇒
      handleNotifications(notifications, retryCount)
    case traceEvents: TraceEvents ⇒
      handleTraceEvents(traceEvents)
    case RetryTraceEvents(traceEvents, retryCount) ⇒
      handleTraceEvents(traceEvents, retryCount)
    case SimpleAck           ⇒
    case IncreaseOutstanding ⇒
    case AllSent             ⇒

  }

  def handleTraceEvents(traceEvents: TraceEvents, retryCount: Int = 0) {
    if (traceEvents.events.isEmpty && retryCount == 0) {
      sender ! EmptyAck
    } else if (traceEvents.events.nonEmpty) {
      if (retryCount == 0) {
        val last = traceEvents.events.last
        val ack = Ack(last.timestamp, last.id)
        val ackAggregator = context.actorOf(Props(new AckAggregator(sender, ack)).withDispatcher(dispatcherId))
        notifyEventListeners(traceEvents, ackAggregator)
        produceSpans(traceEvents.events, ackAggregator)
        ackAggregator ! AllSent
      } else {
        // no ack when retry
        notifyEventListeners(traceEvents, self)
        produceSpans(traceEvents.events, self, retryCount)
      }
    }
  }

  def handleNotifications(notifications: Notifications, retryCount: Int = 0) {
    val startTime = System.currentTimeMillis
    val ids = notifications.events.map(_.uuid)
    val traceEvents = boot.traceRepository.events(ids)
    if (ids.size != traceEvents.size) {
      val foundIds = traceEvents.map(_.id).toSet
      val missing = notifications.events.filter(e ⇒ !foundIds.contains(e.uuid))
      sendRetry(Notifications(missing), retryCount)
    }

    log.debug("Retrieved [{}] trace events. It took [{}] ms", traceEvents.size, currentTimeMillis - startTime)
    handleTraceEvents(TraceEvents(traceEvents))
  }

  def notifyEventListeners(traceEvents: TraceEvents, ackAggregator: ActorRef) {
    for (listener ← eventListeners) {
      ackAggregator ! IncreaseOutstanding
      listener.tell(traceEvents, ackAggregator)
    }
  }

  def notifySpanListeners(spans: Spans, ackAggregator: ActorRef) {
    for (listener ← spanListeners) {
      ackAggregator ! IncreaseOutstanding
      listener.tell(spans, ackAggregator)
    }
  }

  def produceSpans(events: Seq[TraceEvent], ackAggregator: ActorRef, retryCount: Int = 0) {
    if (spanListeners.nonEmpty) {
      val startTime = currentTimeMillis

      val retryEvents = ArrayBuffer[TraceEvent]()

      for (event ← events; spanBuilder ← spanBuilders) {
        if (!spanBuilder.add(event))
          retryEvents += event
      }

      spanBuilders.foreach { drainSpans(_, ackAggregator) }

      if (retryEvents.nonEmpty) {
        sendRetry(TraceEvents(retryEvents.toIndexedSeq), retryCount)
      }

      log.debug("Produced spans from [{}] trace events. It took [{}] ms",
        events.size, currentTimeMillis - startTime)
    }
  }

  def sendRetry(traceEvents: TraceEvents, retryCount: Int) {
    if (traceEvents.events.nonEmpty && retryCount < settings.MaxRetryAttempts) {
      val retry = RetryTraceEvents(traceEvents, retryCount + 1)
      log.debug("Schedule retry attempt [{}] of [{}] trace events",
        retry.retryCount, traceEvents.events.size)
      context.system.scheduler.scheduleOnce(settings.RetryDelay, self, retry)(context.system.dispatcher)
    }
  }

  def sendRetry(notifications: Notifications, retryCount: Int) {
    if (notifications.events.nonEmpty && retryCount < settings.MaxRetryAttempts) {
      val retry = RetryNotifications(notifications, retryCount + 1)
      log.debug("Schedule retry attempt [{}] of [{}] trace event notifications",
        retry.retryCount, notifications.events.size)
      context.system.scheduler.scheduleOnce(settings.RetryDelay, self, retry)(context.system.dispatcher)
    }
  }

  def drainSpans(spanBuilder: SpanBuilder, ackAggregator: ActorRef) {
    if (spanBuilder.resultSize > 0) {
      val spans = spanBuilder.result

      if (settings.SaveSpans) {
        boot.spanRepository.save(spans)
      }

      notifySpanListeners(Spans(spans), ackAggregator)

      log.debug("Published [{}] Spans to listeners", spans.size)
      spanBuilder.clearResult()
    }
  }

}

object Analyzer {
  case object SimpleAck
  case class RetryTraceEvents(traceEvents: TraceEvents, retryCount: Int)
  case class RetryNotifications(notifications: Notifications, retryCount: Int)
  val dispatcherId = "atmos.analytics.dispatcher"
}

object AckAggregator {
  case object IncreaseOutstanding
  case object AllSent
}

class AckAggregator(replyTo: ActorRef, replyWith: Ack) extends Actor with ActorLogging {
  val startTime = System.currentTimeMillis

  var expectedAcks = 0
  var receivedAcks = 0
  var allSent = false

  def receive = {
    case IncreaseOutstanding ⇒
      expectedAcks += 1
    case AllSent ⇒
      allSent = true
      replyWhenDone()
    case SimpleAck ⇒
      receivedAcks += 1
      replyWhenDone()
  }

  def replyWhenDone() {
    if (allSent && receivedAcks == expectedAcks) {
      log.debug("Ack from [{}] after [{}] ms", expectedAcks, (System.currentTimeMillis - startTime))
      replyTo ! replyWith
      context.stop(self)
    }
  }
}
