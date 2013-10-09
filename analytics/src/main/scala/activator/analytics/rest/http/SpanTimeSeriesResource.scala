/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data._
import activator.analytics.repository.SpanTimeSeriesRepository
import GatewayActor._
import java.io.StringWriter
import java.io.Writer
import SpanTimeSeriesResource.QueryBuilder
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import activator.analytics.AnalyticsExtension

class SpanTimeSeriesResource(repository: SpanTimeSeriesRepository) extends RestResourceActor with TimeSeriesSampling[SpanTimeSeriesPoint] {

  final val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) =
    new SpanTimeSeriesJsonRepresentation(formatTimestamps(request), context.system)

  override val configMaxPoints = AnalyticsExtension(context.system).MaxSpanTimeriesPoints

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val timeSeries = repository.findWithinTimePeriod(query.timeRange, query.scope, query.spanTypeName)
        val result = SpanTimeSeries.concatenate(timeSeries, query.timeRange, query.scope, query.spanTypeName)
        val sampledResult = sample(result, query.sampling, query.maxPoints)
        HttpResponse(entity = jsonRepresentation(req).toJson(sampledResult), headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson

    }
  }

  override def sampledPoint(point: SpanTimeSeriesPoint, adjustedSamplingFactor: Int): SpanTimeSeriesPoint =
    point.copy(sampled = point.sampled * adjustedSamplingFactor)

}

object SpanTimeSeriesResource {

  case class Query(
    timeRange: TimeRange,
    scope: Scope,
    spanTypeName: String,
    sampling: Option[Int],
    maxPoints: Option[Int])

  class QueryBuilder extends TimeRangeQueryBuilder with ScopeQueryBuilder with SamplingQueryBuilder {

    def build(url: String, queryParams: String): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒ extractSpanTypeName(url) match {
          case Left(message) ⇒ Left(message)
          case Right(spanTypeName) ⇒
            extractScope(url, queryParams) match {
              case Left(message) ⇒ Left(message)
              case Right(scope) ⇒
                val sampling = extractSampling(queryParams)
                val maxPoints = extractMaxPoints(queryParams)
                Right(Query(timeRange, scope, spanTypeName, sampling, maxPoints))
            }
        }
      }
    }

    // override to convert to always use minuteRange
    override def extractTime(path: String): Either[String, TimeRange] = {
      super.extractTime(path) match {
        case left @ Left(_)   ⇒ left
        case Right(timeRange) ⇒ Right(TimeRange.minuteRange(timeRange.startTime, timeRange.endTime))
      }
    }
  }
}

class SpanTimeSeriesJsonRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  def toJson(spanTimeSeries: SpanTimeSeries): String = {
    val writer = new StringWriter
    writeJson(spanTimeSeries, writer)
    writer.toString
  }

  def writeJson(spanTimeSeries: SpanTimeSeries, writer: Writer) {
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    gen.writeStartObject()

    writeJson(spanTimeSeries.timeRange, gen)
    writeJson(spanTimeSeries.scope, gen)
    gen.writeStringField("spanTypeName", SpanType.formatSpanTypeName(spanTimeSeries.spanTypeName))
    gen.writeStringField("pointDescription", "Each point consist of an array of [time, duration, sampled]. The last element is omitted when sampled is 1.")
    gen.writeDurationTimeUnitField("durationUnit")

    gen.writeArrayFieldStart("points")
    for (point ← spanTimeSeries.points) {
      gen.writeStartArray()
      gen.writeTimestamp(point.timestamp)
      gen.writeDuration(point.duration)
      if (point.sampled != 1) gen.writeNumber(point.sampled)
      gen.writeEndArray()
    }
    gen.writeEndArray()

    gen.writeEndObject()
    gen.flush()
  }

}
