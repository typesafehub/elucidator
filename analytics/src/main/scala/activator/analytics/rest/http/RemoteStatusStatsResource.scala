/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ TimeRange, RemoteStatusStats }
import activator.analytics.repository.RemoteStatusStatsRepository
import GatewayActor._
import java.io.StringWriter
import org.codehaus.jackson.JsonGenerator
import RemoteStatusStatsResource.QueryBuilder
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import activator.analytics.AnalyticsExtension

class RemoteStatusStatsResource(repository: RemoteStatusStatsRepository) extends RestResourceActor {

  final val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) =
    new RemoteStatusStatsJsonRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val stats = repository.findWithinTimePeriod(query.timeRange, query.node, query.actorSystem)
        val representation = query.chunks match {
          case None ⇒
            val result = RemoteStatusStats.concatenate(stats, query.timeRange, query.node, query.actorSystem)
            jsonRepresentation(req).toJson(result)
          case Some((min, max)) ⇒
            val result = RemoteStatusStats.chunk(min, max, stats, query.timeRange, query.node, query.actorSystem)
            jsonRepresentation(req).toJson(result)
        }

        HttpResponse(entity = representation, headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }
}

object RemoteStatusStatsResource {

  case class Query(timeRange: TimeRange, node: Option[String], actorSystem: Option[String], chunks: Option[(Int, Int)])

  class QueryBuilder extends TimeRangeQueryBuilder with ScopeQueryBuilder with ChunkQueryBuilder {

    def build(url: String, queryParams: String): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒
          extractNode(queryParams) match {
            case Left(message) ⇒ Left(message)
            case Right(node) ⇒
              extractActorSystem(queryParams) match {
                case Left(message) ⇒ Left(message)
                case Right(actorSystem) ⇒
                  val chunks = extractNumberOfChunks(queryParams)
                  Right(Query(timeRange, node, actorSystem, chunks))
              }
          }
      }
    }
  }
}

class RemoteStatusStatsJsonRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {

  def toJson(stats: RemoteStatusStats): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(stats, gen)
    gen.flush()
    writer.toString
  }

  def toJson(stats: Iterable[RemoteStatusStats]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    gen.writeStartArray()
    for (s ← stats) writeJson(s, gen)
    gen.writeEndArray()
    gen.flush()
    writer.toString
  }

  def writeJson(stats: RemoteStatusStats, gen: JsonGenerator) {
    gen.writeStartObject()

    writeJson(stats.timeRange, gen)
    for (x ← stats.node) gen.writeStringField("node", x)
    for (x ← stats.actorSystem) gen.writeStringField("actorSystem", x)

    for ((k, v) ← stats.metrics.counts.toSeq.sortBy(_._1)) {
      gen.writeNumberField(k, v)
    }

    gen.writeEndObject()
  }

}
