/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import activator.analytics.repository.PlayRequestSummaryRepository
import activator.analytics.data.PlayRequestSummary
import akka.actor.{ Actor, Cancellable, ActorLogging }
import java.util.concurrent.TimeUnit._
import scala.concurrent.duration._

object PlayRequestSummaryStore {
  case object Purge
}

class PlayRequestSummaryStore(playRequestSummaryPurgeInterval: Long, playRequestSummaryRepository: PlayRequestSummaryRepository) extends Actor with ActorLogging {
  import PlayRequestSummaryStore._

  var scheduler: Cancellable = null

  def initScheduler(): Unit = {
    cleanupScheduler()
    scheduler = context.system.scheduler.schedule(playRequestSummaryPurgeInterval.milliseconds, playRequestSummaryPurgeInterval.milliseconds, self, Purge)(context.system.dispatcher)
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
    purge()
  }

  def purge(): Unit = {
    playRequestSummaryRepository.purgeOld()
  }

  def receive = {
    case v: PlayRequestSummary ⇒
      playRequestSummaryRepository.save(v)
    case Purge ⇒ purge()
  }
}
