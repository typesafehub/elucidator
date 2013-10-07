/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data._
import activator.analytics.rest.RestExtension
import activator.analytics.repository.SummarySpanStatsRepository
import GatewayActor._
import java.io.StringWriter
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpRequest }
import scala.Left
import scala.Right
import spray.http.HttpResponse

class SummarySpanStatsResource(repository: SummarySpanStatsRepository) extends RestResourceActor {
  import SummarySpanStatsResource._

  final val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = new SummarySpanStatsJsonRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val stats = repository.findWithinTimePeriod(query.timeRange, query.scope, query.spanTypeName)
        val headers = HeadersBuilder.headers(query.timeRange.endTime)
        val representation = query.chunks match {
          case None ⇒
            val result = SummarySpanStats.concatenate(stats, query.timeRange, query.scope, query.spanTypeName)
            jsonRepresentation(req).toJson(result)
          case Some((min, max)) ⇒
            val result = SummarySpanStats.chunk(min, max, stats, query.timeRange, query.scope, query.spanTypeName)
            jsonRepresentation(req).toJson(result)
        }
        HttpResponse(entity = representation, headers = headers).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
      case _             ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = "Could not handle request: " + req.uri.path.toString + " with parameters " + req.uri.query.toString).asJson
    }
  }
}

object SummarySpanStatsResource {

  case class Query(
    timeRange: TimeRange,
    scope: Scope,
    spanTypeName: String,
    chunks: Option[(Int, Int)])

  class QueryBuilder extends TimeRangeQueryBuilder with ScopeQueryBuilder with ChunkQueryBuilder {

    def build(url: String, queryParams: String): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒ extractSpanTypeName(url) match {
          case Left(message) ⇒ Left(message)
          case Right(spanTypeName) ⇒
            extractScope(url, queryParams) match {
              case Left(message) ⇒ Left(message)
              case Right(scope) ⇒
                val chunks = extractNumberOfChunks(queryParams)
                Right(Query(timeRange, scope, spanTypeName, chunks))
            }
        }
      }
    }
  }

}

class SummarySpanStatsJsonRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {

  def toJson(stats: SummarySpanStats): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(stats, gen)
    gen.flush()
    writer.toString
  }

  def toJson(stats: Iterable[SummarySpanStats]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartArray()
    for (s ← stats) writeJson(s, gen)
    gen.writeEndArray()
    gen.flush()
    writer.toString
  }

  def writeJson(stats: SummarySpanStats, gen: JsonGenerator) {
    gen.writeStartObject()

    writeJson(stats.timeRange, gen)
    writeJson(stats.scope, gen)
    gen.writeStringField("spanTypeName", SpanType.formatSpanTypeName(stats.spanTypeName))

    gen.writeNumberField("n", stats.n)
    gen.writeDurationField("totalDuration", stats.totalDuration, MILLISECONDS)
    if (stats.minDuration >= 0L) gen.writeDurationField("minDuration", stats.minDuration)
    if (stats.maxDuration >= 0L) gen.writeDurationField("maxDuration", stats.maxDuration)
    if (stats.meanDuration >= 0.0) gen.writeDurationField("meanDuration", stats.meanDuration.toLong)

    gen.writeEndObject()
  }
}
