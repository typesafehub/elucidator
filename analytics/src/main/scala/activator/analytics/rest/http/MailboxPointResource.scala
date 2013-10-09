/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ MailboxTimeSeriesPoint, MailboxTimeSeries }
import activator.analytics.repository.MailboxTimeSeriesRepository
import GatewayActor._
import java.io.{ StringWriter, Writer }
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import activator.analytics.AnalyticsExtension

class MailboxPointResource(mailboxTimeSeriesRepository: MailboxTimeSeriesRepository) extends RestResourceActor with LatestPoint {

  import MailboxPointResource._

  val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = new MailboxPointRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    val path = req.uri.path.toString
    URLDecoder.decode(path) match {
      case MailboxPointPattern(actorPath) ⇒
        parse(actorPath, req.uri.query.toString) match {
          case Right(point)  ⇒ HttpResponse(entity = jsonRepresentation(req).toJson(actorPath, point)).asJson
          case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
        }
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = "Incorrect path [%s]".format(path)).asJson
    }
  }

  def parse(actorPath: String, queryParams: String): Either[String, MailboxTimeSeriesPoint] = {
    queryBuilder.build(queryParams) match {
      case Left(msg) ⇒ Left(msg)
      case Right(pointInMillis) ⇒
        val result = mailboxTimeSeriesRepository.findLatest(pointInMillis, actorPath)
        getLatestPoint[MailboxTimeSeries, MailboxTimeSeriesPoint](pointInMillis, result) match {
          case Some(point) ⇒ Right(point)
          case None        ⇒ Left("Could not find any matching point")
        }
    }
  }
}

object MailboxPointResource {
  import GatewayActor._
  import GatewayActor.PatternChars._
  val MailboxPointPattern = ("""^.*""" + MailboxPointUri + "/(" + PathChars + ")").r

  class QueryBuilder extends PointInTimeQueryBuilder {
    def build(queryParams: String): Either[String, Long] = {
      extractPointInTime(queryParams)
    }
  }
}

class MailboxPointRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  val pointRepresentation = new MailboxTimeSeriesJsonRepresentation(formatTimestamps, system)

  def toJson(actorPath: String, point: MailboxTimeSeriesPoint): String = {
    val writer = new StringWriter
    writeJson(actorPath, point, writer)
    writer.toString
  }

  def writeJson(actorPath: String, point: MailboxTimeSeriesPoint, writer: Writer) = {
    val generator = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    generator.writeStartObject()
    generator.writeStringField("actorPath", actorPath)
    pointRepresentation.writeJson(point, generator)
    generator.writeEndObject()
    generator.flush()
  }
}
