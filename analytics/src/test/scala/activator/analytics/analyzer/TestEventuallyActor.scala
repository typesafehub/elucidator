/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ Actor, ActorRef, FSM }
import com.typesafe.trace.{ TraceEvents, TraceEvent }
import scala.concurrent._
import scala.concurrent.duration._

object TestEventuallyActor {
  case object RunTest

  sealed trait State
  case object WaitingForData extends State
  case object WaitingForTimeout extends State
  case object Timeout extends State
  case object Success extends State
}

class TestEventuallyActor(within: Int, trigger: Promise[Boolean], test: Seq[TraceEvent] ⇒ Boolean) extends Actor with FSM[TestEventuallyActor.State, Seq[TraceEvent]] {
  import TestEventuallyActor._
  import FSM._

  override def preStart(): Unit = {
    super.preStart()
    setTimer("tick", RunTest, within.milliseconds, true)
  }

  def fulfillPromise(result: Boolean): Unit =
    if (!trigger.isCompleted) trigger.success(result)

  startWith(WaitingForData, Seq.empty[TraceEvent])

  when(WaitingForData) {
    case Event(RunTest, e) ⇒
      if (test(e)) goto(Success) using Seq.empty[TraceEvent]
      else goto(WaitingForTimeout) using e
    case Event(TraceEvents(e), _) ⇒
      if (test(e)) goto(Success) using Seq.empty[TraceEvent]
      else stay using e
  }

  when(WaitingForTimeout) {
    case Event(RunTest, e) ⇒
      if (test(e)) goto(Success) using Seq.empty[TraceEvent]
      else goto(Timeout) using e
    case Event(TraceEvents(e), _) ⇒
      if (test(e)) goto(Success) using Seq.empty[TraceEvent]
      else goto(WaitingForData) using e
  }

  when(Success) {
    case Event(_, _) ⇒
      stay using Seq.empty[TraceEvent]
  }

  when(Timeout) {
    case Event(_, _) ⇒
      stay using Seq.empty[TraceEvent]
  }

  onTransition {
    case _ -> Success ⇒
      fulfillPromise(true)
      stop()
    case _ -> Timeout ⇒
      fulfillPromise(false)
      stop()
  }

  onTermination {
    case StopEvent(Normal, s, _) ⇒
      cancelTimer("tick")
    case StopEvent(_, s, e) ⇒
      cancelTimer("tick")
      fulfillPromise(test(e))
  }
}
