/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.gracefulStop
import com.typesafe.trace.Annotation
import com.typesafe.trace.TraceEvent
import java.net.{ InetAddress, UnknownHostException }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.WordSpecLike
import scala.concurrent.Await
import activator.analytics.TimeoutHandler
import scala.concurrent.duration._

trait AnalyzeTest extends BeforeAndAfterEach { this: WordSpecLike ⇒

  val HostName: String = try {
    InetAddress.getLocalHost.getCanonicalHostName
  } catch {
    case e: UnknownHostException ⇒ "unknown"
  }

  val DefaultNode = "default@" + HostName

  def system: ActorSystem

  def awaitStop(actor: ActorRef): Unit = awaitStop(Seq(actor))

  private val handler = TimeoutHandler(system.settings.config.getInt("activator.test.time-factor"))

  def awaitStop(actors: Seq[ActorRef]): Unit = {
    for (a ← actors) {
      Await.ready(gracefulStop(a, handler.duration), handler.duration)
      // additional stop to ensure that it is removed from system namespace before returning
      system.stop(a)
    }
  }

  def event(annotation: Annotation): TraceEvent = TraceEvent(annotation, system.name)
}
