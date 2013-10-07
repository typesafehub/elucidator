/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ Actor, ActorRef, Cancellable, ActorLogging }
import activator.analytics.data._
import activator.analytics.data.BasicTypes._
import activator.analytics.repository.PlayTraceTreeRepository
import com.typesafe.atmos.trace._
import com.typesafe.atmos.uuid.UUID
import java.util.concurrent.TimeUnit._
import scala.collection.mutable.{ HashMap, Queue, ArrayBuffer }
import scala.concurrent.duration._

object TraceTreeStore {
  case object Flush
  case object Purge
  case object ForceFlushAndExit

  case class QueueEntry(uuid: UUID, timestamp: Timestamp)
}

class TraceTreeStore(flushAge: Long,
                     traceTreePurgeInterval: Long,
                     traceTreeRepository: PlayTraceTreeRepository,
                     playStatsAnalyzerActor: ActorRef) extends Actor with ActorLogging {
  import TraceTreeStore._

  val accumulators: HashMap[UUID, ArrayBuffer[TraceEvent]] = new HashMap[UUID, ArrayBuffer[TraceEvent]]
  val ageQueue: Queue[QueueEntry] = new Queue[QueueEntry]
  var scheduler: Cancellable = null
  var purgeScheduler: Cancellable = null

  def initScheduler(): Unit = {
    cleanupScheduler()
    scheduler = context.system.scheduler.schedule(flushAge.milliseconds, flushAge.milliseconds, self, Flush)(context.system.dispatcher)
    purgeScheduler = context.system.scheduler.schedule(traceTreePurgeInterval.milliseconds, traceTreePurgeInterval.milliseconds, self, Purge)(context.system.dispatcher)
  }

  def cleanupScheduler(): Unit = {
    if (scheduler != null) {
      scheduler.cancel()
      scheduler = null
    }
    if (purgeScheduler != null) {
      purgeScheduler.cancel()
      purgeScheduler = null
    }
  }

  override def preStart(): Unit =
    initScheduler()

  override def postStop(): Unit =
    cleanup()

  def cleanup(): Unit = {
    cleanupScheduler()
    flush(true)
    purge()
  }

  def addEvents(tes: Seq[TraceEvent]): Unit = {
    val uuid = tes.head.trace
    accumulators.get(uuid).map { b ⇒
      b.append(tes: _*)
      ageQueue.dequeueFirst(_.uuid == uuid).foreach { qe ⇒ ageQueue.enqueue(qe.copy(timestamp = System.currentTimeMillis)) }
    } getOrElse {
      val b = new ArrayBuffer[TraceEvent]
      b.append(tes: _*)
      accumulators.put(uuid, b)
      ageQueue.enqueue(QueueEntry(uuid, System.currentTimeMillis))
    }
  }

  def updateTraceTree(uuid: UUID, partialTrace: Seq[TraceEvent]): Seq[TraceEvent] = {
    val toSave = traceTreeRepository.find(uuid).map(_ ++ partialTrace).getOrElse(partialTrace)
    traceTreeRepository.save(uuid, toSave)
    toSave
  }

  def flush(all: Boolean = false): Unit = {
    if (!all) {
      val threshold = System.currentTimeMillis - flushAge
      ageQueue.dequeueAll(_.timestamp < threshold).foreach { qe ⇒
        accumulators.remove(qe.uuid).foreach { b ⇒
          if (!b.isEmpty) {
            playStatsAnalyzerActor ! TraceEvents(updateTraceTree(qe.uuid, b.toSeq))
          }
        }
      }
    } else {
      accumulators.foreach {
        case (uuid, b) ⇒
          if (!b.isEmpty) {
            playStatsAnalyzerActor ! TraceEvents(updateTraceTree(uuid, b.toSeq))
          }
      }
      accumulators.clear()
      ageQueue.clear()
    }
  }

  def purge(): Unit = {
    traceTreeRepository.purgeOld()
  }

  def receive = {
    case Purge ⇒
      purge()
    case Flush ⇒
      flush()
    case ForceFlushAndExit ⇒
      flush(true)
      context stop self
    case TraceEvents(tes) ⇒
      if (!tes.isEmpty) addEvents(tes)
  }

}
