/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data._
import activator.analytics.repository.PlayStatsRepository
import java.io.StringWriter
import java.util.concurrent.TimeUnit.NANOSECONDS
import org.codehaus.jackson.JsonGenerator
import PlayStatsResource.QueryBuilder
import spray.http.{ StatusCodes, HttpRequest, HttpResponse }
import activator.analytics.AnalyticsExtension

class PlayStatsResource(repository: PlayStatsRepository) extends RestResourceActor {
  import GatewayActor._

  val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = {
    new PlayStatsJsonRepresentation(baseUrl(request), formatTimestamps(request), context.system)
  }

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val stats = repository.findWithinTimePeriod(query.timeRange, query.scope)
        val representation = query.chunks match {
          case None ⇒
            val result = PlayStats.concatenate(stats, query.timeRange, query.scope)
            jsonRepresentation(req).toJson(result)
          case Some((min, max)) ⇒
            val result = PlayStats.chunk(min, max, stats, query.timeRange, query.scope)
            jsonRepresentation(req).toJson(result)
        }
        HttpResponse(entity = representation, headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = "Could not handle request: " + req.uri.path.toString + " with parameters " + req.uri.query.toString).asJson
    }
  }
}

object PlayStatsResource {

  case class Query(
    timeRange: TimeRange,
    scope: Scope,
    chunks: Option[(Int, Int)])

  class QueryBuilder extends TimeRangeQueryBuilder with ScopeQueryBuilder with ChunkQueryBuilder {

    def build(url: String, queryParams: String): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒
          extractScope(url, queryParams) match {
            case Left(message) ⇒ Left(message)
            case Right(scope) ⇒
              val chunks = extractNumberOfChunks(queryParams)
              Right(Query(timeRange, scope, chunks))
          }
      }
    }
  }
}

class PlayStatsJsonRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  def toJson(stats: PlayStats): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(stats, gen)
    gen.flush()
    writer.toString
  }

  def toJson(stats: Iterable[PlayStats]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    gen.writeStartArray()
    for (s ← stats) writeJson(s, gen)
    gen.writeEndArray()
    gen.flush()
    writer.toString
  }

  def writeJson(stats: PlayStats, gen: JsonGenerator) {
    gen.writeStartObject()

    writeJson(stats.timeRange, gen)
    writeJson(stats.scope, gen)

    gen.writeNumberField("invocationCount", stats.metrics.counts.invocationCount)
    gen.writeNumberField("simpleResultCount", stats.metrics.counts.simpleResultCount)
    gen.writeNumberField("chunkedResultCount", stats.metrics.counts.chunkedResultCount)
    gen.writeNumberField("asyncResultCount", stats.metrics.counts.asyncResultCount)
    gen.writeNumberField("errorCount", stats.metrics.counts.errorCount)
    gen.writeNumberField("exceptionCount", stats.metrics.counts.exceptionCount)
    gen.writeNumberField("handlerNotFoundCount", stats.metrics.counts.handlerNotFoundCount)
    gen.writeNumberField("badRequestCount", stats.metrics.counts.badRequestCount)

    gen.writeObjectFieldStart("countsByStatus")
    stats.metrics.counts.countsByStatus.foreach { case (k, v) ⇒ gen.writeNumberField(k, v) }
    gen.writeEndObject()

    gen.writeBytesField("bytesWritten", stats.metrics.bytesWritten, unitField = false)
    gen.writeBytesField("bytesRead", stats.metrics.bytesRead, unitField = false)

    if (stats.metrics.latestTraceEventTimestamp > 0L) {
      gen.writeTimestampField("latestTraceEventTimestamp", stats.metrics.latestTraceEventTimestamp)
    }
    if (stats.metrics.latestInvocationTimestamp > 0L) {
      gen.writeTimestampField("latestInvocationTimestamp", stats.metrics.latestInvocationTimestamp)
      gen.writeStringField("rateUnit", "invocations/second")
      writeMessageRates(gen, stats.metrics.invocationRateMetrics, stats.metrics.peakInvocationRateMetrics)
      gen.writeNumberField("meanInvocationRate", stats.meanInvocationRate)
      gen.writeStringField("meanInvocationRateUnit", "invocations/second")
      gen.writeNumberField("meanBytesReadRate", stats.meanBytesReadRate)
      gen.writeStringField("meanBytesReadRateUnit", "bytes/second")
      gen.writeNumberField("meanBytesWrittenRate", stats.meanBytesWrittenRate)
      gen.writeStringField("meanBytesWrittenRateUnit", "bytes/second")
      gen.writeNumberField("meanDuration", stats.meanDuration / 1000000.0)
      gen.writeStringField("meanDurationUnit", "milliseconds/invocation")
      gen.writeNumberField("meanInputProcessingDuration", stats.meanInputProcessingDuration / 1000000.0)
      gen.writeStringField("meanInputProcessingDurationUnit", "milliseconds/invocation")
      gen.writeNumberField("meanActionExecutionDuration", stats.meanActionExecutionDuration / 1000000.0)
      gen.writeStringField("meanActionExecutionDurationUnit", "milliseconds/invocation")
      gen.writeNumberField("meanOutputProcessingDuration", stats.meanOutputProcessingDuration / 1000000.0)
      gen.writeStringField("meanOutputProcessingDurationUnit", "milliseconds/invocation")

      writePlayProcessingStats(gen, stats.metrics.processingStats)
    }

    gen.writeEndObject()
  }

  private def writeMessageRates(
    gen: JsonGenerator,
    invocationRateMetrics: InvocationRateMetrics,
    peakInvocationRateMetrics: PeakInvocationRateMetrics) {

    gen.writeNumberField("totalInvocationRate", invocationRateMetrics.totalInvocationRate)
    if (peakInvocationRateMetrics.totalInvocationRateTimestamp > 0L) {
      gen.writeNumberField("peakTotalInvocationRate", peakInvocationRateMetrics.totalInvocationRate)
      gen.writeTimestampField("peakTotalInvocationRateTimestamp", peakInvocationRateMetrics.totalInvocationRateTimestamp)
    }

    gen.writeNumberField("bytesWrittenRate", invocationRateMetrics.bytesWrittenRate)
    gen.writeStringField("bytesWrittenRateUnit", "bytes/second")
    if (peakInvocationRateMetrics.bytesWrittenRateTimestamp > 0L) {
      gen.writeNumberField("peakBytesWrittenRate", peakInvocationRateMetrics.bytesWrittenRate)
      gen.writeTimestampField("peakBytesWrittenRateTimestamp", peakInvocationRateMetrics.bytesWrittenRateTimestamp)
    }

    gen.writeNumberField("bytesReadRate", invocationRateMetrics.bytesReadRate)
    gen.writeStringField("bytesReadRateUnit", "bytes/second")
    if (peakInvocationRateMetrics.bytesReadRateTimestamp > 0L) {
      gen.writeNumberField("peakBytesReadRate", peakInvocationRateMetrics.bytesReadRate)
      gen.writeTimestampField("peakBytesReadRateTimestamp", peakInvocationRateMetrics.bytesReadRateTimestamp)
    }
  }

  private def writePlayProcessingStats(
    gen: JsonGenerator,
    playProcessingStats: PlayProcessingStats) {
    import PlayRequestSummaryResource._
    gen.writeLinkOrId("maxDurationTrace", SummaryEventUri, playProcessingStats.maxDurationTrace)
    gen.writeTimestampField("maxDurationTimestamp", playProcessingStats.maxDurationTimestamp)
    gen.writeDurationField("maxDuration", playProcessingStats.maxDuration, NANOSECONDS)
    gen.writeLinkOrId("maxInputProcessingDurationTrace", SummaryEventUri, playProcessingStats.maxInputProcessingDurationTrace)
    gen.writeTimestampField("maxInputProcessingDurationTimestamp", playProcessingStats.maxInputProcessingDurationTimestamp)
    gen.writeDurationField("maxInputProcessingDuration", playProcessingStats.maxInputProcessingDuration, NANOSECONDS)
    gen.writeLinkOrId("maxActionExecutionDurationTrace", SummaryEventUri, playProcessingStats.maxActionExecutionDurationTrace)
    gen.writeTimestampField("maxActionExecutionDurationTimestamp", playProcessingStats.maxActionExecutionDurationTimestamp)
    gen.writeDurationField("maxActionExecutionDuration", playProcessingStats.maxActionExecutionDuration, NANOSECONDS)
    gen.writeLinkOrId("maxOutputProcessingDurationTrace", SummaryEventUri, playProcessingStats.maxOutputProcessingDurationTrace)
    gen.writeTimestampField("maxOutputProcessingDurationTimestamp", playProcessingStats.maxOutputProcessingDurationTimestamp)
    gen.writeDurationField("maxOutputProcessingDuration", playProcessingStats.maxOutputProcessingDuration, NANOSECONDS)
  }

}

