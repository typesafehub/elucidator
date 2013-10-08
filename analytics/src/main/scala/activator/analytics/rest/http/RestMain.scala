/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ Props, ActorSystem }
import akka.io.IO
import spray.can.Http

object RestMain {
  implicit val system = ActorSystem("query")

  def main(args: Array[String]) {
    val i = getInterface(args)
    val p = getPort(args)
    val handler = system.actorOf(Props[GatewayActor], "gatewayActor")
    IO(Http) ! Http.Bind(handler, interface = i, port = p)
  }

  private def getInterface(args: Array[String]): String =
    try {
      if (args.length >= 1) args(0)
      else if (System.getProperty("query.http.interface") != null) System.getProperty("query.http.interface")
      else system.settings.config.getString("atmos.query.interface")
    } catch {
      case e: Exception ⇒ "localhost"
    }

  private def getPort(args: Array[String]): Int =
    try {
      if (args.length >= 2) args(1).toInt
      else if (System.getProperty("query.http.port") != null) System.getProperty("query.http.port").toInt
      else system.settings.config.getInt("atmos.query.port")
    } catch {
      case e: Exception ⇒ 9898
    }

  def stopServer() {
    system.shutdown()
  }
}
