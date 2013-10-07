/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import spray.http._
import java.io.StringWriter
import akka.actor.{ Actor, ActorSystem }
import spray.http.HttpResponse
import com.typesafe.inkan.TypesafeLicense
import activator.analytics.rest.RestExtension
import activator.analytics.util.QueryHttpHeaders

class LicenseResource(licence: Option[TypesafeLicense]) extends Actor {

  def receive = {
    case JRequest(req) ⇒ sender ! handle(req)
  }

  def handle(req: HttpRequest): HttpResponse = {
    req.method match {
      case HttpMethods.GET ⇒
        HttpResponse(
          headers = List(new QueryHttpHeaders.AccessControlAllowOrigin("*")),
          entity = HttpEntity(ContentType(MediaTypes.`application/json`),
            new LicenseJsonRepresentation(context.system).toJson(licence)))
      case x ⇒
        HttpResponse(
          status = StatusCodes.BadRequest,
          entity = "Invalid HTTP method '" + x + "' for service Licence")
    }
  }
}

class LicenseJsonRepresentation(system: ActorSystem) extends JsonRepresentation {
  def toJson(licence: Option[TypesafeLicense]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartObject()
    licence match {
      case None ⇒
        gen.writeStringField("status", "unavailable")
      case Some(l) ⇒
        gen.writeStringField("status", "available")
        gen.writeStringField("name", l.name)
        gen.writeNumberField("servers", l.servers)
        gen.writeDateField("expiryDate", l.expiry)
        gen.writeTimestampField("expiry", l.expiry)
    }
    gen.writeEndObject()
    gen.flush()
    writer.toString
  }
}
