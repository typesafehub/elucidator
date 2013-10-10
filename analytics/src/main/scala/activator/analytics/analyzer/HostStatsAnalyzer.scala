/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import activator.analytics.repository.HostStatsRepository
import activator.analytics.data.HostStats
import akka.actor.{ Actor, ActorLogging }
import com.typesafe.trace.{ SystemStarted, TraceEvents }

class HostStatsAnalyzer(hostStatsRepository: HostStatsRepository) extends Actor with ActorLogging {
  def receive = {
    case TraceEvents(events) ⇒
      events foreach { e ⇒
        e.annotation match {
          case SystemStarted(s) ⇒ hostStatsRepository.save(
            HostStats(
              host = e.host,
              node = e.node,
              actorSystem = e.actorSystem))
          case _ ⇒
        }
      }
  }
}

