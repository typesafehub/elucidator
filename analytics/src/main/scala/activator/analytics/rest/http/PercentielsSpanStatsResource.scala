/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data._
import activator.analytics.data.{ TimeRange, SpanType, Scope, PercentilesSpanStats }
import activator.analytics.repository.PercentilesSpanStatsRepository
import GatewayActor._
import java.io.StringWriter
import java.io.Writer
import PercentilesSpanStatsResource.QueryBuilder
import PercentilesSpanStatsResource.QueryBuilder._
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import TimeRangeType.Hours
import activator.analytics.AnalyticsExtension

class PercentilesSpanStatsResource(repository: PercentilesSpanStatsRepository) extends RestResourceActor {

  val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) =
    new PercentilesSpanStatsJsonRepresentation(formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val stats = repository.findWithinTimePeriod(query.timeRange, query.scope, query.spanTypeName)
        val result = stats.headOption.getOrElse(PercentilesSpanStats(query.timeRange, query.scope, query.spanTypeName)(context.system))
        HttpResponse(entity = jsonRepresentation(req).toJson(result), headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }
}

object PercentilesSpanStatsResource {

  case class Query(
    timeRange: TimeRange,
    scope: Scope,
    spanTypeName: String)

  class QueryBuilder extends TimeRangeQueryBuilder with ScopeQueryBuilder {

    def build(url: String, queryParams: String): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒ extractSpanTypeName(url) match {
          case Left(message) ⇒ Left(message)
          case Right(spanTypeName) ⇒
            extractScope(url, queryParams) match {
              case Left(message) ⇒ Left(message)
              case Right(scope)  ⇒ Right(Query(timeRange, scope, spanTypeName))
            }
        }
      }
    }

    /**
     * Percentiles only available for rolling=1hour or time=2011-07-21T13&rangeType=hour,
     * or without any time parameters, i.e. AllTime.
     */
    override def extractTime(path: String): Either[String, TimeRange] = path match {
      case RollingOneHourPattern(value) ⇒
        Right(TimeRange.hourRange(System.currentTimeMillis, value.toInt))
      case HistoricalOneHourTimePattern(time) ⇒
        parseTime(Some(time), Some(time), Hours)
      case _ ⇒
        // using 'AllTime' as default
        Right(TimeRange())
    }

  }

  object QueryBuilder {
    val RollingOneHourPattern = """^.*rolling=(1)hour[s]?&?.*?""".r
    val HistoricalOneHourTimePattern = """^.*time=(.*)&rangeType=hour[s]?&?.*?""".r
  }

}

class PercentilesSpanStatsJsonRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {

  def toJson(percentilesSpanStats: PercentilesSpanStats): String = {
    val writer = new StringWriter
    writeJson(percentilesSpanStats, writer)
    writer.toString
  }

  def writeJson(percentilesSpanStats: PercentilesSpanStats, writer: Writer) {
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    gen.writeStartObject()

    writeJson(percentilesSpanStats.timeRange, gen)
    writeJson(percentilesSpanStats.scope, gen)
    gen.writeStringField("spanTypeName", SpanType.formatSpanTypeName(percentilesSpanStats.spanTypeName))

    gen.writeNumberField("n", percentilesSpanStats.n)
    gen.writeObjectFieldStart("percentiles")

    for ((p, v) ← percentilesSpanStats.percentiles.toSeq.sortBy(_._1.toDouble)) {
      gen.writeDurationField(p, v, unitField = false)
    }
    gen.writeDurationTimeUnitField("unit")
    gen.writeEndObject()

    gen.writeEndObject()
    gen.flush()
  }

}
