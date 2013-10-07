/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ SystemMetricsTimeSeriesPoint, SystemMetricsTimeSeries }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.SystemMetricsTimeSeriesRepository
import GatewayActor._
import java.io.{ StringWriter, Writer }
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }

class SystemMetricsPointResource(systemMetricsTimeSeriesRepository: SystemMetricsTimeSeriesRepository)
  extends RestResourceActor with LatestPoint {
  import SystemMetricsPointResource._

  final val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = new SystemMetricsPointRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    val path = req.uri.path.toString
    path match {
      case SystemMetricsPointPattern(node) ⇒
        parse(node, req.uri.query.toString) match {
          case Right(point)  ⇒ HttpResponse(entity = jsonRepresentation(req).toJson(node, point)).asJson
          case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
        }
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = "Incorrect path [%s]".format(path)).asJson
    }
  }

  def parse(node: String, queryParams: String): Either[String, SystemMetricsTimeSeriesPoint] = {
    queryBuilder.build(queryParams) match {
      case Right(pointInMillis) ⇒
        val result = systemMetricsTimeSeriesRepository.findLatest(pointInMillis, node)
        getLatestPoint[SystemMetricsTimeSeries, SystemMetricsTimeSeriesPoint](pointInMillis, result) match {
          case Some(point) ⇒ Right(point)
          case None        ⇒ Left("Could not find any matching point")
        }
      case Left(msg) ⇒ Left(msg)
    }
  }
}

object SystemMetricsPointResource {
  import GatewayActor.PatternChars._
  val SystemMetricsPointPattern = ("""^.*""" + SystemMetricsPointUri + "/(" + NodeChars + ")").r

  class QueryBuilder extends PointInTimeQueryBuilder {
    def build(queryParams: String): Either[String, Long] = {
      extractPointInTime(queryParams)
    }
  }
}

class SystemMetricsPointRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  val pointRepresentation = new SystemMetricsTimeSeriesPointRepresentation(formatTimestamps, system)

  def toJson(node: String, point: SystemMetricsTimeSeriesPoint) = {
    val writer = new StringWriter
    writeJson(node, point, writer)
    writer.toString
  }

  def writeJson(node: String, point: SystemMetricsTimeSeriesPoint, writer: Writer) = {
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    generator.writeStartObject()
    generator.writeStringField("node", node)
    pointRepresentation.writeJson(point, generator)
    generator.writeEndObject()
    generator.flush()
  }
}
