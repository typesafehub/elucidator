/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ TimeRange, Scope, MessageRateTimeSeriesPoint, MessageRateTimeSeries }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.MessageRateTimeSeriesRepository
import java.io.StringWriter
import java.io.Writer
import MessageRateTimeSeriesResource.QueryBuilder
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }

class MessageRateTimeSeriesResource(repository: MessageRateTimeSeriesRepository)
  extends RestResourceActor with TimeSeriesSampling[MessageRateTimeSeriesPoint] {

  import GatewayActor._

  val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) =
    new MessageRateTimeSeriesJsonRepresentation(formatTimestamps(request), context.system)

  override val configMaxPoints = RestExtension(context.system).MaxTimeriesPoints

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val timeSeries = repository.findWithinTimePeriod(query.timeRange, query.scope)
        val result = MessageRateTimeSeries.concatenate(timeSeries, query.timeRange, query.scope)
        val sampledResult = sample(result, query.sampling, query.maxPoints)
        HttpResponse(entity = jsonRepresentation(req).toJson(sampledResult), headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }
}

object MessageRateTimeSeriesResource {
  case class Query(
    timeRange: TimeRange,
    scope: Scope,
    sampling: Option[Int],
    maxPoints: Option[Int])

  class QueryBuilder extends TimeRangeQueryBuilder with ScopeQueryBuilder with SamplingQueryBuilder {

    def build(url: String, queryParams: String): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒
          extractScope(url, queryParams) match {
            case Left(message) ⇒ Left(message)
            case Right(scope) ⇒
              val sampling = extractSampling(queryParams)
              val maxPoints = extractMaxPoints(queryParams)
              Right(Query(timeRange, scope, sampling, maxPoints))
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

class MessageRateTimeSeriesJsonRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {

  def toJson(timeSeries: MessageRateTimeSeries): String = {
    val writer = new StringWriter
    writeJson(timeSeries, writer)
    writer.toString
  }

  def writeJson(timeSeries: MessageRateTimeSeries, writer: Writer) {
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartObject()

    writeJson(timeSeries.timeRange, gen)
    writeJson(timeSeries.scope, gen)
    gen.writeStringField("rateUnit", "messages/second")
    gen.writeStringField("bytesRateUnit", "bytes/second")

    gen.writeArrayFieldStart("points")
    for (point ← timeSeries.points) {
      gen.writeStartObject()
      writeJson(point, gen)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeEndObject()
    gen.flush()
  }

  def writeJson(point: MessageRateTimeSeriesPoint, generator: JsonGenerator) {
    generator.writeTimestampField("timestamp", point.timestamp)
    generator.writeNumberField("totalMessageRate", point.rates.totalMessageRate)
    generator.writeNumberField("receiveRate", point.rates.receiveRate)
    generator.writeNumberField("tellRate", point.rates.tellRate)
    generator.writeNumberField("askRate", point.rates.askRate)
    generator.writeNumberField("remoteSendRate", point.rates.remoteSendRate)
    generator.writeNumberField("remoteReceiveRate", point.rates.remoteReceiveRate)
    generator.writeNumberField("bytesWrittenRate", point.rates.bytesWrittenRate)
    generator.writeNumberField("bytesReadRate", point.rates.bytesReadRate)
  }

}
