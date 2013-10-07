/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ DispatcherTimeSeriesPoint, DispatcherTimeSeries }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.DispatcherTimeSeriesRepository
import GatewayActor._
import java.io.{ StringWriter, Writer }
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }

class DispatcherPointResource(dispatcherTimeSeriesRepository: DispatcherTimeSeriesRepository) extends RestResourceActor with LatestPoint {

  import DispatcherPointResource._

  final val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = new DispatcherPointRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    val path = req.uri.path.toString
    path match {
      case DispatcherPointPattern(node, actorSystem, dispatcher) ⇒
        handleQuery(node, actorSystem, dispatcher, req.uri.query.toString) match {
          case Right(result) ⇒ HttpResponse(entity = jsonRepresentation(req).toJson(node, actorSystem, dispatcher, result))
          case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
        }
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = "Invalid URI: [%s]".format(path)).asJson
    }
  }

  def handleQuery(node: String, actorSystem: String, dispatcher: String, queryParams: String): Either[String, DispatcherTimeSeriesPoint] = {
    queryBuilder.build(queryParams) match {
      case Left(msg) ⇒ Left(msg)
      case Right(pointInMillis) ⇒
        val result = dispatcherTimeSeriesRepository.findLatest(pointInMillis, node, actorSystem, dispatcher)
        getLatestPoint[DispatcherTimeSeries, DispatcherTimeSeriesPoint](pointInMillis, result) match {
          case Some(point) ⇒ Right(point)
          case None        ⇒ Left("Could not find any matching point")
        }
    }
  }
}

object DispatcherPointResource {
  import GatewayActor.PatternChars._
  val DispatcherPointPattern =
    ("""^.*""" + DispatcherPointUri + "/(" + NodeChars + ")/(" + ActorSystemChars + ")/(" + DispatcherChars + ")").r

  class QueryBuilder extends PointInTimeQueryBuilder {
    def build(queryParams: String): Either[String, Long] = {
      extractPointInTime(queryParams)
    }
  }

}

class DispatcherPointRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  val pointRepresentation = new DispatcherTimeSeriesPointRepresentation(formatTimestamps, system)

  def toJson(node: String, actorSystem: String, dispatcher: String, point: DispatcherTimeSeriesPoint): String = {
    val writer = new StringWriter
    writeJson(node, actorSystem, dispatcher, point, writer)
    writer.toString
  }

  def writeJson(node: String, actorSystem: String, dispatcher: String, point: DispatcherTimeSeriesPoint, writer: Writer) = {
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    generator.writeStartObject()
    generator.writeStringField("node", node)
    generator.writeStringField("actorSystem", actorSystem)
    generator.writeStringField("dispatcher", dispatcher)
    pointRepresentation.writeJson(point, generator)
    generator.writeEndObject()
    generator.flush()
  }
}
