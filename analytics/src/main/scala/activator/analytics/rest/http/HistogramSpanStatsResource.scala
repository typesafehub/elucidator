/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ TimeRange, SpanType, Scope, HistogramSpanStats }
import activator.analytics.metrics.HistogramMetric
import activator.analytics.repository.HistogramSpanStatsRepository
import GatewayActor._
import HistogramSpanStatsResource.QueryBuilder
import java.io.StringWriter
import java.io.Writer
import scala.concurrent.duration._
import spray.http.{ StatusCodes, HttpRequest, HttpResponse }
import activator.analytics.AnalyticsExtension

class HistogramSpanStatsResource(repository: HistogramSpanStatsRepository) extends RestResourceActor {

  val queryBuilder = new QueryBuilder
  def jsonRepresentation(request: HttpRequest) =
    new HistogramSpanStatsJsonRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString, context.system) match {
      case Right(query) ⇒
        val stats = repository.findWithinTimePeriod(query.timeRange, query.scope, query.spanTypeName)
        val result = HistogramSpanStats.concatenate(stats, query.timeRange, query.scope, query.spanTypeName)(context.system)
        val convertedResult = result.redistribute(query.bucketBoundaries)
        HttpResponse(entity = jsonRepresentation(req).toJson(convertedResult), headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }
}

object HistogramSpanStatsResource {

  case class Query(
    timeRange: TimeRange,
    scope: Scope,
    spanTypeName: String,
    bucketBoundaries: IndexedSeq[Long])

  class QueryBuilder extends TimeRangeQueryBuilder with ScopeQueryBuilder {

    def build(url: String, queryParams: String, system: ActorSystem): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒ extractSpanTypeName(url) match {
          case Left(message) ⇒ Left(message)
          case Right(spanTypeName) ⇒
            extractScope(url, queryParams) match {
              case Left(message) ⇒ Left(message)
              case Right(scope) ⇒
                val bucketBoundaries = extractBucketBoundaries(queryParams, scope, spanTypeName, system)
                Right(Query(timeRange, scope, spanTypeName, bucketBoundaries))
            }
        }
      }
    }

    def extractBucketBoundaries(path: String, scope: Scope, spanTypeName: String, system: ActorSystem): IndexedSeq[Long] = {
      path match {
        case QueryBuilder.BucketBoundariesPattern(queryParm) ⇒
          HistogramMetric.bucketBoundaries(queryParm).map(micros ⇒ Duration(micros, MICROSECONDS).toNanos)
        case _ ⇒
          HistogramSpanStats.configBucketBoundaries(spanTypeName, "presentation")(system)
      }
    }
  }

  object QueryBuilder {
    val BucketBoundariesPattern = """^.*bucketBoundaries=([\d\,]+)&?.*?""".r
  }

}

class HistogramSpanStatsJsonRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {

  def toJson(histogramSpanStats: HistogramSpanStats): String = {
    val writer = new StringWriter
    writeJson(histogramSpanStats, writer)
    writer.toString
  }

  def writeJson(histogramSpanStats: HistogramSpanStats, writer: Writer) {
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    gen.writeStartObject()

    writeJson(histogramSpanStats.timeRange, gen)
    writeJson(histogramSpanStats.scope, gen)
    gen.writeStringField("spanTypeName", SpanType.formatSpanTypeName(histogramSpanStats.spanTypeName))

    gen.writeArrayFieldStart("bucketBoundaries")
    for (x ← histogramSpanStats.bucketBoundaries) {
      gen.writeDuration(x)
    }
    gen.writeEndArray()
    gen.writeDurationTimeUnitField("bucketBoundariesUnit")
    gen.writeArrayFieldStart("buckets")
    for (x ← histogramSpanStats.buckets) {
      gen.writeNumber(x)
    }
    gen.writeEndArray()

    gen.writeEndObject()
    gen.flush()
  }

}
