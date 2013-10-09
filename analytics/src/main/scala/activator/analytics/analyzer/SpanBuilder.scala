/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorSystem
import activator.analytics.data._
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import com.typesafe.atmos.util.Uuid
import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import activator.analytics.AnalyticsExtension

object SpanBuilder {

  val MaxEventsInTrace = 1000

  def isIgnored(spanType: SpanType)(implicit system: ActorSystem): Boolean = AnalyticsExtension(system).IgnoreSpanTypes.toSet.contains(spanType.formattedName)

  def apply(spanType: SpanType, traceRepository: TraceRetrievalRepository)(implicit system: ActorSystem): SpanBuilder = spanType match {
    // The Span between ActorSend and ActorCompleted with different 'local' ids
    case MessageSpan ⇒
      new SpanBuilder(
        traceRepository,
        spanType,
        (lookAhead, event, backTrace) ⇒ isMessageSpanStarted(lookAhead, event, backTrace),
        event ⇒ event.annotation.isInstanceOf[ActorCompleted] && !isTempActor(event))

    // The Span between ActorSend and ActorReceived
    case MailboxSpan ⇒
      new SpanBuilder(
        traceRepository,
        spanType,
        (lookAhead, event, backTrace) ⇒ isMailboxSpanStarted(lookAhead, event, backTrace),
        event ⇒ event.annotation.isInstanceOf[ActorReceived] && !isTempActor(event))

    // The Span between ActorReceived and ActorCompleted
    case ReceiveSpan ⇒
      new SpanBuilder(
        traceRepository,
        spanType,
        (lookAhead, event, backTrace) ⇒ event.annotation.isInstanceOf[ActorReceived],
        event ⇒ event.annotation.isInstanceOf[ActorCompleted] && !isTempActor(event))

    case QuestionSpan ⇒
      new SpanBuilder(
        traceRepository,
        spanType,
        (lookAhead, event, backTrace) ⇒ isQuestionSpanStarted(lookAhead, event, backTrace),
        event ⇒ isQuestionSpanEnded(event))

    // The Span between RemoteMessageSent and RemoteMessageReceived
    case RemoteSpan ⇒
      new SpanBuilder(
        traceRepository,
        spanType,
        (lookAhead, event, backTrace) ⇒ isRemoteSpanStarted(lookAhead, event, backTrace),
        event ⇒ isRemoteSpanEnded(event))

    // The Span between MarkerStarted and MarkerEnded with the same 'name'
    case MarkerSpan ⇒
      new SpanBuilder(
        traceRepository,
        spanType,
        (lookAhead, event, backTrace) ⇒ isMarkerStarted(lookAhead, event, backTrace),
        event ⇒ isMarkerEnded(event))
  }

  def isMessageSpanStarted(lookAhead: IndexedSeq[TraceEvent], event: TraceEvent, backTrace: IndexedSeq[TraceEvent]) = {
    event.annotation match {
      case send: ActorTold ⇒
        val inRemoteReceive = !lookAhead.isEmpty && (lookAhead.last.annotation match {
          case r: RemoteMessageReceived ⇒ r.info.path == send.info.path
          case _                        ⇒ false
        })
        event.local != backTrace.head.local && !inRemoteReceive
      case _ ⇒ false
    }
  }

  def isMailboxSpanStarted(lookAhead: IndexedSeq[TraceEvent], event: TraceEvent, backTrace: IndexedSeq[TraceEvent]) = {
    event.annotation match {
      case send: ActorTold ⇒ true
      case _               ⇒ false
    }
  }

  def isQuestionSpanStarted(lookAhead: IndexedSeq[TraceEvent], event: TraceEvent, backTrace: IndexedSeq[TraceEvent]) = {
    event.annotation match {
      case send: ActorAsked if event.local != backTrace.head.local ⇒ true
      case _ ⇒ false
    }
  }

  def isQuestionSpanEnded(event: TraceEvent) = {
    event.annotation match {
      case x: FutureSucceeded     ⇒ true
      case x: FutureFailed        ⇒ true
      case x: FutureAwaitTimedOut ⇒ true
      case _                      ⇒ false
    }
  }

  def isRemoteSpanStarted(lookAhead: IndexedSeq[TraceEvent], event: TraceEvent, backTrace: IndexedSeq[TraceEvent]) = {
    event.annotation match {
      case sent: RemoteMessageSent ⇒ true
      case _                       ⇒ false
    }
  }

  def isRemoteSpanEnded(event: TraceEvent) = {
    event.annotation match {
      case delivered: RemoteMessageReceived ⇒ true
      case _                                ⇒ false
    }
  }

  def isMarkerEnded(event: TraceEvent)(implicit system: ActorSystem) = {
    event.annotation match {
      case MarkerEnded(name) if !AnalyticsExtension(system).IgnoreSpanTypes.exists(_ == name) ⇒ true
      case _ ⇒ false
    }
  }

  def isMarkerStarted(lookAhead: IndexedSeq[TraceEvent], event: TraceEvent, backTrace: IndexedSeq[TraceEvent])(implicit system: ActorSystem) = {
    (event.annotation, backTrace.head.annotation) match {
      case (MarkerStarted(name1), MarkerEnded(name2)) if name1 == name2 && !(AnalyticsExtension(system).IgnoreSpanTypes.exists(_ == name1)) ⇒ true
      case _ ⇒ false
    }
  }

  def isTempActor(event: TraceEvent): Boolean = event.annotation match {
    case a: ActorAnnotation ⇒ isTempActor(a.info)
    case _                  ⇒ false
  }

  def isTempActor(info: ActorInfo): Boolean = info.path.contains("/temp/$")
}

class SpanBuilder(
  traceRepository: TraceRetrievalRepository,
  spanType: SpanType,
  isStartEvent: (IndexedSeq[TraceEvent], TraceEvent, IndexedSeq[TraceEvent]) ⇒ Boolean,
  isEndEvent: TraceEvent ⇒ Boolean) {

  import SpanBuilder._

  type TraceSeq = IndexedSeq[TraceEvent]

  private val spans = ArrayBuffer[Span]()

  /**
   * Tries to create span for the event.
   * Returns false if expected start event was not available in traceRepository, otherwise true.
   */
  def add(event: TraceEvent)(implicit system: ActorSystem): Boolean = {
    if (isEndEvent(event)) {
      val s = createSpan(event)
      spans ++= s
      s.isDefined
    } else {
      true
    }
  }

  private def createSpan(end: TraceEvent)(implicit system: ActorSystem): Option[Span] = {

    def durationBetween(start: TraceEvent, end: TraceEvent): Duration = {
      // durations must not be less than 0, can happen when clock is wrong or changed
      def ensurePositive(value: Long): Long = if (value < 0L) 0L else value

      if (AnalyticsExtension(system).UseNanoTimeCrossNodes || start.node == end.node) {
        ensurePositive(end.nanoTime - start.nanoTime).nanos
      } else {
        ensurePositive(end.timestamp - start.timestamp).millis
      }
    }

    // creates a look-ahead to the beginning of the current local span
    @tailrec
    def localTrace(event: TraceEvent)(collected: TraceSeq = IndexedSeq(event)): TraceSeq = {
      traceRepository.event(event.parent) match {
        case None ⇒ collected
        case Some(parent) ⇒
          if (parent.local == event.local) localTrace(parent)(collected :+ parent)
          else collected
      }
    }

    // get the next local span if it exists
    def forwardTrace(event: TraceEvent): TraceSeq = {
      traceRepository.event(event.parent) match {
        case None         ⇒ IndexedSeq.empty
        case Some(parent) ⇒ localTrace(parent)()
      }
    }

    // collect the span checking for the start event with look-ahead and back-trace
    @tailrec
    def collectSpan(forward: TraceSeq, backward: TraceSeq): Option[TraceSeq] = {
      if (backward.size > MaxEventsInTrace) return None
      val upcoming = if (forward.isEmpty) forwardTrace(backward.last) else forward
      if (upcoming.isEmpty) return None
      val event = upcoming.head
      val ahead = upcoming.tail
      if (isStartEvent(ahead, event, backward)) Some(backward :+ event)
      else collectSpan(ahead, backward :+ event)
    }

    val events = collectSpan(IndexedSeq.empty, IndexedSeq(end))

    events map { spanEvents ⇒
      val spanId = Uuid()

      val start = spanEvents.last
      val remote = isRemoteCall(spanEvents)
      val duration = durationBetween(start, end)

      val spanTypeName = end.annotation match {
        case MarkerEnded(name) ⇒ name
        case _                 ⇒ spanType.name
      }

      new Span(spanId, start.trace, spanTypeName, start.timestamp, duration, start.sampled, remote, start.id, end.id)
    }

  }

  private def isRemoteCall(spanEvents: TraceSeq): Boolean = {
    @tailrec
    def containsRemote(events: List[TraceEvent]): Boolean = events match {
      case Nil ⇒ false
      case x :: xs ⇒ x.annotation match {
        case r: RemoteMessageSent                ⇒ true
        case a: ActorAnnotation if a.info.remote ⇒ true
        case _                                   ⇒ containsRemote(xs)
      }
    }

    containsRemote(spanEvents.reverse.toList)
  }

  def result(): IndexedSeq[Span] = {
    spans.toIndexedSeq
  }

  def resultSize() = {
    spans.length
  }

  def clearResult() {
    spans.clear
  }

}
