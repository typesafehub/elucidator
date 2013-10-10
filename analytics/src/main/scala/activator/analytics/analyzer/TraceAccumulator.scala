/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ Actor, ActorRef, Cancellable, ActorLogging }
import activator.analytics.data._
import activator.analytics.data.BasicTypes._
import activator.analytics.analyzer.PlayTraceTreeHelpers._
import com.typesafe.trace._
import com.typesafe.trace.uuid.UUID
import java.util.concurrent.TimeUnit._
import scala.collection.mutable.{ HashMap, Queue, ArrayBuffer }
import scala.concurrent.duration._

object TraceAccumulator {
  case object Flush
  case object ForceFlush
  case object ForceFlushAndExit

  case class QueueEntry(uuid: UUID, timestamp: Timestamp)
}

class TraceAccumulator(flushAge: Long, traceTreeStoreActor: ActorRef) extends Actor with ActorLogging {
  import TraceAccumulator._

  val accumulators: HashMap[UUID, ArrayBuffer[TraceEvent]] = new HashMap[UUID, ArrayBuffer[TraceEvent]]
  val ageQueue: Queue[QueueEntry] = new Queue[QueueEntry]
  var scheduler: Cancellable = null

  def initScheduler(): Unit = {
    cleanupScheduler()
    scheduler = context.system.scheduler.schedule(flushAge.milliseconds, flushAge.milliseconds, self, Flush)(context.system.dispatcher)
  }

  def cleanupScheduler(): Unit =
    if (scheduler != null) {
      scheduler.cancel()
      scheduler = null
    }

  override def preStart(): Unit =
    initScheduler()

  override def postStop(): Unit =
    cleanup()

  def cleanup(): Unit = {
    cleanupScheduler()
    flush(true)
  }

  def addEvents(tes: Seq[TraceEvent]): Unit = {
    tes.foreach(e ⇒ addEvent(e))
  }

  def addEvent(te: TraceEvent): Unit = {
    val uuid = te.trace
    accumulators.get(uuid).map { b ⇒
      b.append(te)
      ageQueue.dequeueFirst(_.uuid == uuid).foreach { qe ⇒ ageQueue.enqueue(qe.copy(timestamp = System.currentTimeMillis)) }
    } getOrElse {
      val b = new ArrayBuffer[TraceEvent]
      b.append(te)
      accumulators.put(uuid, b)
      ageQueue.enqueue(QueueEntry(uuid, System.currentTimeMillis))
    }
  }

  def flush(all: Boolean = false): Unit = {
    if (!all) {
      val threshold = System.currentTimeMillis - flushAge
      ageQueue.dequeueAll(_.timestamp < threshold).view
        .map(qe ⇒ accumulators.remove(qe.uuid))
        .flatten
        .filterNot(_.isEmpty)
        .toSeq
        .sortBy(_.traceOfEarliestEvent.nanoTime)
        .foreach { b ⇒
          traceTreeStoreActor ! TraceEvents(b.toSeq)
        }
    } else {
      accumulators.values
        .filterNot(_.isEmpty).toSeq
        .sortBy(_.traceOfEarliestEvent.nanoTime)
        .foreach { b ⇒
          traceTreeStoreActor ! TraceEvents(b.toSeq)
        }
      accumulators.clear()
      ageQueue.clear()
    }
  }

  def receive = {
    case Flush ⇒
      flush()
    case ForceFlush ⇒
      flush(true)
    case ForceFlushAndExit ⇒
      flush(true)
      context stop self
    case TraceEvents(es) ⇒
      addEvents(es)
  }

}
