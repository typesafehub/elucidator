/**
 * Copyright (C) 2011-2013 Typesafe, Inc <http://typesafe.com>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ TimeRange, RecordStats }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.RecordStatsRepository
import GatewayActor._
import java.io.StringWriter
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }

class RecordStatsResource(repository: RecordStatsRepository) extends RestResourceActor {
  import RecordStatsResource._

  final val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = new RecordStatsJsonRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val stats = repository.findWithinTimePeriod(query.timeRange, query.node, query.actorSystem)
        val representation = query.chunks match {
          case Some((min, max)) ⇒
            val result = RecordStats.chunk(min, max, stats, query.timeRange, query.node, query.actorSystem)
            jsonRepresentation(req).toJson(result)
          case None ⇒
            val result = RecordStats.concatenate(stats, query.timeRange, query.node, query.actorSystem)
            jsonRepresentation(req).toJson(result)
        }

        HttpResponse(entity = representation, headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message)
    }
  }
}

object RecordStatsResource {

  case class Query(timeRange: TimeRange,
                   node: Option[String],
                   actorSystem: Option[String],
                   chunks: Option[(Int, Int)])

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

class RecordStatsJsonRepresentation(override val baseUrl: Option[String], override val formatTimestamps: Boolean, system: ActorSystem)
  extends JsonRepresentation {

  def toJson(recordStats: RecordStats): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(recordStats, gen)
    gen.flush()
    writer.toString
  }

  def toJson(stats: Iterable[RecordStats]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartArray()
    for (s ← stats) writeJson(s, gen)
    gen.writeEndArray()
    gen.flush()
    writer.toString
  }

  def writeJson(recordStats: RecordStats, gen: JsonGenerator) {
    gen.writeStartObject()
    writeJson(recordStats.timeRange, gen)
    for (x ← recordStats.node) gen.writeStringField("node", x)
    for (x ← recordStats.actorSystem) gen.writeStringField("actorSystem", x)
    val groups = recordStats.metrics.groups
    gen.writeArrayFieldStart("records")
    for (key ← groups.keys) {
      val group = groups(key)
      gen.writeStartObject()
      gen.writeStringField(key, group.counter.toString)
      gen.writeArrayFieldStart("childrenRecords")
      for (memberKey ← group.members.keys) {
        gen.writeStartObject()
        gen.writeStringField(memberKey, group.members(memberKey).toString)
        gen.writeEndObject()
      }
      gen.writeEndArray()
      gen.writeEndObject()
    }
    gen.writeEndArray()
    gen.writeEndObject()
  }
}
