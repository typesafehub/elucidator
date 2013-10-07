/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ Actor, ActorLogging }
import scala.language.implicitConversions
import spray.http._

trait RestResourceActor extends Actor with ActorLogging {

  def receive = {
    case JRequest(req) ⇒ sender ! handle(req)
  }

  def handle(req: HttpRequest): HttpResponse

  implicit def responseWrapper(response: HttpResponse) = new HttpResponseWrapper(response)
}
class HttpResponseWrapper(response: HttpResponse) {
  def asJson: HttpResponse = {
    response.copy(entity = HttpEntity(ContentType(MediaTypes.`application/json`), response.entity.asString))
  }
}

