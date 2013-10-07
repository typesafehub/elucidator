/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import activator.analytics.data._
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.MemoryTraceEventListener
import com.typesafe.atmos.util.{ AtmosSpec, Uuid }
import com.typesafe.atmos.uuid.UUID
import scala.annotation.tailrec
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class SpanBuilderSpec extends AtmosSpec with AnalyzeTest {

  val traceReceiver = TraceReceiver(config)
  val traceRepository = MemoryTraceEventListener.getRepositoryFor(traceReceiver).getOrElse(
    throw new RuntimeException("Need to use MemoryTraceEventListener with " + system.name))

  override def beforeEach() {
    super.beforeEach()
    traceRepository.clear()
  }

  override def afterEach() {
    traceRepository.clear()
    super.afterEach()
  }

  override def afterAll() {
    traceReceiver.shutdown()
    super.afterAll()
  }

  def allEvents: Iterable[TraceEvent] = {
    traceRepository.allEvents.filter(_.annotation match {
      case _: SystemStarted ⇒ false
      case _                ⇒ true
    })
  }

  def allTraces = traceRepository.allTraces

  def orderedTraces = allTraces.toSeq.sortBy(t ⇒ t.map(_.nanoTime).min)

  def printTraces() = orderedTraces.map(PrettyTrace.show).foreach(println)

  def populateWithLocalTell() {
    val startTime = System.currentTimeMillis
    val nano = System.nanoTime
    val actorInfo = ActorInfo("akka://SpanBuilderSpec/user/actorA", Some("dispatcher"), false, false, Set.empty)
    val trace = Uuid()
    val told = event(ActorTold(actorInfo, "msg1", None)).copy(
      trace = trace, local = Uuid(),
      timestamp = startTime, nanoTime = nano)
    val received = event(ActorReceived(actorInfo, "msg1")).copy(
      trace = trace, parent = told.id, local = Uuid(),
      timestamp = startTime, nanoTime = nano + 3000)
    val completed = event(ActorCompleted(actorInfo, "msg1")).copy(
      trace = trace, parent = received.id, local = received.local,
      timestamp = startTime, nanoTime = nano + 9000)

    traceRepository.store(Batch(Seq(TraceEvents(Seq(told, received, completed)))))
  }

  "A SpanBuilder" must {

    "create message span for local tell" in {
      populateWithLocalTell()
      val spanBuilder = SpanBuilder(MessageSpan, traceRepository)
      allEvents foreach spanBuilder.add

      spanBuilder.resultSize must be(1)
      val span = spanBuilder.result.head
      // time between ActorTold and ActorCompleted
      span.duration must be(9000.nanoseconds)
      countEvents(span) must be(3)
    }

    "create mailbox span for local tell" in {
      populateWithLocalTell()
      val spanBuilder = SpanBuilder(MailboxSpan, traceRepository)
      allEvents foreach spanBuilder.add

      spanBuilder.resultSize must be(1)
      val span = spanBuilder.result.head
      // time between ActorTold and ActorReceived
      span.duration must be(3000.nanoseconds)
      countEvents(span) must be(2)
    }

    "create receive span for local tell" in {
      populateWithLocalTell()
      val spanBuilder = SpanBuilder(ReceiveSpan, traceRepository)
      allEvents foreach spanBuilder.add

      spanBuilder.resultSize must be(1)
      val span = spanBuilder.result.head
      // time between ActorReceived and ActorCompleted
      span.duration must be(6000.nanoseconds)
      countEvents(span) must be(2)
    }

    def populateWithRemoteTell() {
      val startTime = System.currentTimeMillis
      val nano = System.nanoTime
      val actorInfo = ActorInfo("akka://SpanBuilderSpec/user/actorA", Some("dispatcher"), false, false, Set.empty)
      val remoteActorInfo = actorInfo.copy(remote = true)
      val trace = Uuid()
      val told = event(ActorTold(remoteActorInfo, "msg1", None)).copy(
        trace = trace, local = Uuid(), node = "node1",
        timestamp = startTime, nanoTime = nano)
      val remoteSent = event(RemoteMessageSent(remoteActorInfo, "msg1", 12345)).copy(
        trace = trace, parent = told.id, local = Uuid(), node = "node1",
        timestamp = startTime + 1, nanoTime = nano + 10)
      val remoteReceived = event(RemoteMessageReceived(actorInfo, "msg1", 12345)).copy(
        trace = trace, parent = remoteSent.id, local = Uuid(), node = "node2",
        timestamp = startTime + 2, nanoTime = nano + 900)
      val deliveryTold = event(ActorTold(actorInfo, "msg1", None)).copy(
        trace = trace, parent = remoteReceived.id, local = remoteReceived.local, node = "node2",
        timestamp = startTime + 3, nanoTime = nano + 1000)
      val remoteCompleted = event(RemoteMessageCompleted(actorInfo, "msg1")).copy(
        trace = trace, parent = deliveryTold.id, local = remoteReceived.local, node = "node2",
        timestamp = startTime + 4, nanoTime = nano + 1100)
      val received = event(ActorReceived(actorInfo, "msg1")).copy(
        trace = trace, parent = deliveryTold.id, local = Uuid(), node = "node2",
        timestamp = startTime + 5, nanoTime = nano + 3000)
      val completed = event(ActorCompleted(actorInfo, "msg1")).copy(
        trace = trace, parent = received.id, local = received.local, node = "node2",
        timestamp = startTime + 6, nanoTime = nano + 9000)

      traceRepository.store(Batch(Seq(TraceEvents(Seq(told, remoteSent, remoteReceived, deliveryTold, remoteCompleted, received, completed)))))
    }

    "create mailbox span for remote tell" in {
      populateWithRemoteTell()
      val spanBuilder = SpanBuilder(MailboxSpan, traceRepository)
      allEvents foreach spanBuilder.add

      spanBuilder.resultSize must be(1)
      val span = spanBuilder.result.head
      // time between second ActorTold and ActorReceived
      span.duration must be(2000.nanoseconds)
      span.remote must be(false)
      countEvents(span) must be(2)
    }

    "create message span for remote tell" in {
      populateWithRemoteTell()
      val spanBuilder = SpanBuilder(MessageSpan, traceRepository)
      allEvents foreach spanBuilder.add

      spanBuilder.resultSize must be(1)
      val span = spanBuilder.result.head
      span.remote must be(true)
      // time between ActorTold and ActorCompleted, but for remote calls
      // we use millisecond timestamp, can't use nanoTime cross nodes
      span.duration must be(6.milliseconds)
      countEvents(span) must be(6)
    }

    "create remote span for remote tell" in {
      populateWithRemoteTell()
      val spanBuilder = SpanBuilder(RemoteSpan, traceRepository)
      allEvents foreach spanBuilder.add

      spanBuilder.resultSize must be(1)
      val span = spanBuilder.result.head
      span.remote must be(true)
      // time between RemoteMessageSent and RemoteMessageReceived, but for remote calls
      // we use millisecond timestamp, can't use nanoTime cross nodes
      span.duration must be(1.millis)
      countEvents(span) must be(2)
    }

    "detect incomplete span" in {
      val trace = Uuid()
      val endEvent = event(MarkerEnded("test1")).copy(trace = trace)
      val spanBuilder = SpanBuilder(MarkerSpan, traceRepository)
      val ok = spanBuilder.add(endEvent)
      ok must be(false)
      spanBuilder.resultSize must be(0)
    }

  }

  def countEvents(span: Span): Int = spanEvents(span).size

  def spanEvents(span: Span): List[TraceEvent] = {
    @tailrec
    def events(eventId: UUID, acc: List[TraceEvent]): List[TraceEvent] = {
      traceRepository.event(eventId) match {
        case None ⇒
          acc
        case Some(event) if event.id == span.startEvent ⇒
          event :: acc
        case Some(event) ⇒
          events(event.parent, event :: acc)
      }
    }

    events(span.endEvent, Nil)
  }

}
