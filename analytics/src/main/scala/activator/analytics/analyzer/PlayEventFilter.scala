/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import activator.analytics.repository.DuplicatesRepository
import activator.analytics.repository.DuplicatesTraceKey
import com.typesafe.atmos.trace._
import akka.actor.{ Actor, ActorRef, ActorLogging }

object PlayEventFilter {
  def isInteresting(event: TraceEvent): Boolean = {
    event.annotation match {
      case _: NettyAnnotation  ⇒ true
      case _: ActionAnnotation ⇒ true
      case _                   ⇒ false
    }
  }
}

class PlayEventFilter(
  traceAccumulatorActor: ActorRef,
  val duplicatesRepository: DuplicatesRepository) extends Actor with ActorLogging with DuplicateDetection {
  import PlayEventFilter._

  lazy val duplicateAnalyzerName = self.path.name

  def receive = {
    case TraceEvents(events) ⇒
      filterEvents(events)
      sender ! Analyzer.SimpleAck
  }

  def filterEvents(events: Seq[TraceEvent]): Unit = {
    val startTime = System.currentTimeMillis

    val filteredEvents = events.filter { event ⇒
      if (isInteresting(event)) {
        val duplicatesTraceKey = DuplicatesTraceKey(duplicateAnalyzerName, event.id)
        if (!exists(duplicatesTraceKey)) {
          addDuplicate(duplicatesTraceKey)
          true
        } else false
      } else false
    }

    traceAccumulatorActor ! TraceEvents(filteredEvents)

    log.debug("Filtered {} trace events. It took {} ms",
      events.size, System.currentTimeMillis - startTime)
  }

}
