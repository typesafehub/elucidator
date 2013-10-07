/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import ActorStatsResource.QueryBuilder
import akka.actor.ActorSystem
import activator.analytics.data._
import activator.analytics.data.MessageRateMetrics
import activator.analytics.data.PeakMessageRateMetrics
import activator.analytics.rest.RestExtension
import activator.analytics.repository.ActorStatsRepository
import java.io.StringWriter
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpRequest, HttpResponse }
import TraceEventResource.{ EventUri, TraceTreeUri }
import activator.analytics.analyzer.{ AnalyzeExtension, StandardAnalyzeExtension }

class ActorStatsResource(repository: ActorStatsRepository) extends RestResourceActor {
  import GatewayActor._

  val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = {
    new ActorStatsJsonRepresentation(baseUrl(request), formatTimestamps(request), context.system)
  }

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val stats = repository.findWithinTimePeriod(query.timeRange, query.scope)
        val settings = AnalyzeExtension(context.system)
        val representation = query.chunks match {
          case None ⇒
            val result = ActorStats.concatenate(stats, query.timeRange, query.scope)
            val result2 = trimDeviationDetails(result, settings)
            jsonRepresentation(req).toJson(result2)
          case Some((min, max)) ⇒
            val result = ActorStats.chunk(min, max, stats, query.timeRange, query.scope)
            val result2 = result.map(trimDeviationDetails(_, settings))
            jsonRepresentation(req).toJson(result2)
        }
        HttpResponse(entity = representation, headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = "Could not handle request: " + req.uri.path.toString + " with parameters " + req.uri.query.toString).asJson
    }
  }

  def trimDeviationDetails(result: ActorStats, settings: StandardAnalyzeExtension): ActorStats = {
    result.copy(
      metrics = result.metrics.copy(
        deviationDetails =
          result.metrics.deviationDetails.truncateToMax(
            settings.MaxErrorDeviations,
            settings.MaxWarningDeviations,
            settings.MaxDeadLetterDeviations,
            settings.MaxUnhandledMessageDeviations,
            settings.MaxDeadlockDeviations)))
  }
}

object ActorStatsResource {

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

class ActorStatsJsonRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  def toJson(stats: ActorStats): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(stats, gen)
    gen.flush()
    writer.toString
  }

  def toJson(stats: Iterable[ActorStats]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartArray()
    for (s ← stats) writeJson(s, gen)
    gen.writeEndArray()
    gen.flush()
    writer.toString
  }

  def writeJson(stats: ActorStats, gen: JsonGenerator) {
    gen.writeStartObject()

    writeJson(stats.timeRange, gen)
    writeJson(stats.scope, gen)

    gen.writeNumberField("createdCount", stats.metrics.counts.createdCount)
    gen.writeNumberField("stoppedCount", stats.metrics.counts.stoppedCount)
    gen.writeNumberField("failedCount", stats.metrics.counts.failedCount)
    gen.writeNumberField("restartCount", stats.metrics.counts.restartCount)
    gen.writeNumberField("deviationCount", stats.metrics.counts.deviationCount)
    gen.writeNumberField("errorCount", stats.metrics.counts.errorCount)
    gen.writeNumberField("warningCount", stats.metrics.counts.warningCount)
    gen.writeNumberField("deadLetterCount", stats.metrics.counts.deadLetterCount)
    gen.writeNumberField("unhandledMessageCount", stats.metrics.counts.unhandledMessageCount)
    gen.writeNumberField("processedMessagesCount", stats.metrics.counts.processedMessagesCount)
    gen.writeNumberField("tellMessagesCount", stats.metrics.counts.tellMessagesCount)
    gen.writeNumberField("askMessagesCount", stats.metrics.counts.askMessagesCount)

    gen.writeBytesField("bytesWritten", stats.metrics.bytesWritten, unitField = false)
    gen.writeBytesField("bytesRead", stats.metrics.bytesRead, unitField = false)

    gen.writeNumberField("meanMailboxSize", stats.metrics.meanMailboxSize)
    gen.writeNumberField("maxMailboxSize", stats.metrics.mailbox.maxMailboxSize)
    gen.writeTimestampField("maxMailboxSizeTimestamp", stats.metrics.mailbox.maxMailboxSizeTimestamp)
    gen.writeStringField("maxMailboxSizeAddressNode", stats.metrics.mailbox.maxMailboxSizeAddress.node)
    gen.writeStringField("maxMailboxSizeAddressPath", stats.metrics.mailbox.maxMailboxSizeAddress.path)
    gen.writeDurationField("meanTimeInMailbox", stats.metrics.meanTimeInMailbox)
    gen.writeDurationField("maxTimeInMailbox", stats.metrics.mailbox.maxTimeInMailbox)
    gen.writeTimestampField("maxTimeInMailboxTimestamp", stats.metrics.mailbox.maxTimeInMailboxTimestamp)
    gen.writeStringField("maxTimeInMailboxAddressNode", stats.metrics.mailbox.maxTimeInMailboxAddress.node)
    gen.writeStringField("maxTimeInMailboxAddressPath", stats.metrics.mailbox.maxTimeInMailboxAddress.path)

    if (stats.metrics.latestTraceEventTimestamp > 0L) {
      gen.writeTimestampField("latestTraceEventTimestamp", stats.metrics.latestTraceEventTimestamp)
    }
    if (stats.metrics.latestMessageTimestamp > 0L) {
      gen.writeTimestampField("latestMessageTimestamp", stats.metrics.latestMessageTimestamp)
      gen.writeStringField("rateUnit", "messages/second")
      writeMessageRates(gen, stats.metrics.messageRateMetrics, stats.metrics.peakMessageRateMetrics)
      gen.writeNumberField("meanProcessedMessageRate", stats.meanProcessedMessageRate)
      gen.writeStringField("meanProcessedMessageRateUnit", "messages/second")
      gen.writeNumberField("meanBytesReadRate", stats.meanBytesReadRate)
      gen.writeStringField("meanBytesReadRateUnit", "bytes/second")
      gen.writeNumberField("meanBytesWrittenRate", stats.meanBytesWrittenRate)
      gen.writeStringField("meanBytesWrittenRateUnit", "bytes/second")
    }

    gen.writeArrayFieldStart("errors")
    for (deviation ← stats.metrics.deviationDetails.errors) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeArrayFieldStart("warnings")
    for (deviation ← stats.metrics.deviationDetails.warnings) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeArrayFieldStart("deadLetters")
    for (deviation ← stats.metrics.deviationDetails.deadLetters) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeArrayFieldStart("unhandledMessages")
    for (deviation ← stats.metrics.deviationDetails.unhandledMessages) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeEndObject()
  }

  private def writeMessageRates(
    gen: JsonGenerator,
    messageRateMetrics: MessageRateMetrics,
    peakMessageRateMetrics: PeakMessageRateMetrics) {

    gen.writeNumberField("totalMessageRate", messageRateMetrics.totalMessageRate)
    if (peakMessageRateMetrics.totalMessageRateTimestamp > 0L) {
      gen.writeNumberField("peakTotalMessageRate", peakMessageRateMetrics.totalMessageRate)
      gen.writeTimestampField("peakTotalMessageRateTimestamp", peakMessageRateMetrics.totalMessageRateTimestamp)
    }

    gen.writeNumberField("receiveRate", messageRateMetrics.receiveRate)
    if (peakMessageRateMetrics.receiveRateTimestamp > 0L) {
      gen.writeNumberField("peakReceiveRate", peakMessageRateMetrics.receiveRate)
      gen.writeTimestampField("peakReceiveRateTimestamp", peakMessageRateMetrics.receiveRateTimestamp)
    }

    gen.writeNumberField("tellRate", messageRateMetrics.tellRate)
    if (peakMessageRateMetrics.tellRateTimestamp > 0L) {
      gen.writeNumberField("peakTellRate", peakMessageRateMetrics.tellRate)
      gen.writeTimestampField("peakTellRateTimestamp", peakMessageRateMetrics.tellRateTimestamp)
    }

    gen.writeNumberField("askRate", messageRateMetrics.askRate)
    if (peakMessageRateMetrics.askRateTimestamp > 0L) {
      gen.writeNumberField("peakAskRate", peakMessageRateMetrics.askRate)
      gen.writeTimestampField("peakAskRateTimestamp", peakMessageRateMetrics.askRateTimestamp)
    }

    gen.writeNumberField("remoteSendRate", messageRateMetrics.remoteSendRate)
    if (peakMessageRateMetrics.remoteSendRateTimestamp > 0L) {
      gen.writeNumberField("peakRemoteSendRate", peakMessageRateMetrics.remoteSendRate)
      gen.writeTimestampField("peakRemoteSendRateTimestamp", peakMessageRateMetrics.remoteSendRateTimestamp)
    }

    gen.writeNumberField("remoteReceiveRate", messageRateMetrics.remoteReceiveRate)
    if (peakMessageRateMetrics.remoteReceiveRateTimestamp > 0L) {
      gen.writeNumberField("peakRemoteReceiveRate", peakMessageRateMetrics.remoteReceiveRate)
      gen.writeTimestampField("peakRemoteReceiveRateTimestamp", peakMessageRateMetrics.remoteReceiveRateTimestamp)
    }

    gen.writeNumberField("bytesWrittenRate", messageRateMetrics.bytesWrittenRate)
    gen.writeStringField("bytesWrittenRateUnit", "bytes/second")
    if (peakMessageRateMetrics.bytesWrittenRateTimestamp > 0L) {
      gen.writeNumberField("peakBytesWrittenRate", peakMessageRateMetrics.bytesWrittenRate)
      gen.writeTimestampField("peakBytesWrittenRateTimestamp", peakMessageRateMetrics.bytesWrittenRateTimestamp)
    }

    gen.writeNumberField("bytesReadRate", messageRateMetrics.bytesReadRate)
    gen.writeStringField("bytesReadRateUnit", "bytes/second")
    if (peakMessageRateMetrics.bytesReadRateTimestamp > 0L) {
      gen.writeNumberField("peakBytesReadRate", peakMessageRateMetrics.bytesReadRate)
      gen.writeTimestampField("peakBytesReadRateTimestamp", peakMessageRateMetrics.bytesReadRateTimestamp)
    }

  }

}

