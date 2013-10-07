/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ Scope, MessageRateTimeSeriesPoint, MessageRateTimeSeries }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.MessageRateTimeSeriesRepository
import GatewayActor._
import java.io.{ StringWriter, Writer }
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }

class MessageRatePointResource(messageRateTimeSeriesRepository: MessageRateTimeSeriesRepository) extends RestResourceActor with LatestPoint {

  import MessageRatePointResource._

  val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) =
    new MessageRatePointRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    parse(req.uri.path.toString, req.uri.query.toString) match {
      case Right(tuple) ⇒
        HttpResponse(entity = jsonRepresentation(req).toJson(tuple._1, tuple._2)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  def parse(path: String, queryParams: String): Either[String, (Scope, MessageRateTimeSeriesPoint)] = {
    queryBuilder.build(path, queryParams) match {
      case Left(msg) ⇒ Left(msg)
      case Right(query) ⇒
        val result = messageRateTimeSeriesRepository.findLatest(query.timestamp, query.scope)
        getLatestPoint[MessageRateTimeSeries, MessageRateTimeSeriesPoint](query.timestamp, result) match {
          case Some(point) ⇒ Right(Tuple2(query.scope, point))
          case None        ⇒ Left("Could not find any matching point")
        }
    }
  }
}

object MessageRatePointResource {
  case class Query(timestamp: Long, scope: Scope)

  class QueryBuilder extends ScopeQueryBuilder with PointInTimeQueryBuilder {
    def build(path: String, queryParams: String): Either[String, Query] = {
      extractPointInTime(queryParams) match {
        case Left(msg) ⇒ Left(msg)
        case Right(timestamp) ⇒
          extractScope(path, queryParams) match {
            case Left(message) ⇒ Left(message)
            case Right(scope)  ⇒ Right(Query(timestamp, scope))
          }
      }
    }
  }

}

class MessageRatePointRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  val pointRepresentation = new MessageRateTimeSeriesJsonRepresentation(formatTimestamps, system)

  def toJson(scope: Scope, point: MessageRateTimeSeriesPoint): String = {
    val writer = new StringWriter
    writeJson(scope, point, writer)
    writer.toString
  }

  def writeJson(scope: Scope, point: MessageRateTimeSeriesPoint, writer: Writer) {
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    generator.writeStartObject()
    writeJson(scope, generator)
    pointRepresentation.writeJson(point, generator)
    generator.writeEndObject()
    generator.flush()
  }
}
