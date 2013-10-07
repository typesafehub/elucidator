/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ ActorSystem, Actor }
import activator.analytics.data.{ Counts, TimeRange, ErrorStats }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.ErrorStatsRepository
import GatewayActor._
import java.io.StringWriter
import org.codehaus.jackson.JsonGenerator
import spray.http._
import TraceEventResource.{ EventUri, TraceTreeUri }
import spray.http.HttpResponse
import activator.analytics.analyzer.{ AnalyzeExtension, StandardAnalyzeExtension }

class ErrorStatsResource(repository: ErrorStatsRepository) extends Actor {
  import ErrorStatsResource._

  val queryBuilder = new QueryBuilder
  def jsonFormatter(req: HttpRequest) = new ErrorStatsJsonRepresentation(baseUrl(req), formatTimestamps(req), context.system)

  def receive = {
    case JRequest(request) ⇒
      val response = handle(request) match {
        case Right(payload) ⇒ HttpResponse(entity = payload.json, headers = payload.headers)
        case Left(message)  ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message)
      }

      sender ! response
  }

  def handle(req: HttpRequest): Either[String, ResultPayload] = {
    queryBuilder.build(req.uri.toString, req.uri.query.toString) match {
      case Right(q) ⇒
        val stats = repository.findWithinTimePeriod(q.timeRange, q.node, q.actorSystem)
        val headers = HeadersBuilder.headers(q.timeRange.endTime, MediaTypes.`application/json`)
        val settings = AnalyzeExtension(context.system)
        val representation = q.chunks match {
          case None ⇒
            val result = filterByTime(q.fromTimestamp, ErrorStats.concatenate(stats, q.timeRange, q.node, q.actorSystem))
            val result2 = trimDeviationDetails(result, q.limit, settings)
            jsonFormatter(req).toJson(result2, q.fromTimestamp)
          case Some((min, max)) ⇒
            val result = ErrorStats.chunk(min, max, stats, q.timeRange, q.node, q.actorSystem) map { filterByTime(q.fromTimestamp, _) }
            val result2 = result.map(trimDeviationDetails(_, q.limit, settings))
            jsonFormatter(req).toJson(result2)
        }

        Right(ResultPayload(representation, headers))
      case Left(message) ⇒ Left(message)
    }
  }

  private def filterByTime(from: Option[Long], stats: ErrorStats): ErrorStats = {
    val filtered = for {
      f ← from
    } yield stats.copy(
      metrics = stats.metrics.copy(
        deviations =
          stats.metrics.deviations.filterByTime(f)))

    val recounted = for {
      f ← filtered
    } yield f.copy(
      metrics = f.metrics.copy(
        counts = Counts(
          errors = f.metrics.deviations.errors.size,
          warnings = f.metrics.deviations.warnings.size,
          deadLetters = f.metrics.deviations.deadLetters.size,
          unhandledMessages = f.metrics.deviations.unhandledMessages.size,
          deadlocks = f.metrics.deviations.deadlockedThreads.size)))

    recounted.getOrElse(stats)
  }

  private def trimDeviationDetails(result: ErrorStats, limit: Option[Int], settings: StandardAnalyzeExtension): ErrorStats = {
    result.copy(
      metrics = result.metrics.copy(
        deviations =
          result.metrics.deviations.truncateToMax(
            limit.getOrElse(settings.MaxErrorDeviations),
            limit.getOrElse(settings.MaxWarningDeviations),
            limit.getOrElse(settings.MaxDeadLetterDeviations),
            limit.getOrElse(settings.MaxUnhandledMessageDeviations),
            limit.getOrElse(settings.MaxDeadlockDeviations))))
  }
}

object ErrorStatsResource {

  case class Query(
    timeRange: TimeRange,
    node: Option[String],
    actorSystem: Option[String],
    fromTimestamp: Option[Long],
    limit: Option[Int],
    chunks: Option[(Int, Int)])

  class QueryBuilder extends TimeRangeQueryBuilder
    with ScopeQueryBuilder
    with ChunkQueryBuilder
    with TimestampQueryBuilder
    with PagingQueryBuilder {

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
                  val fromTimestamp = extractTimestamp(queryParams)
                  val limit = extractLimit(queryParams)
                  Right(Query(timeRange, node, actorSystem, fromTimestamp, limit, chunks))
              }
          }
      }
    }
  }

}

class ErrorStatsJsonRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  def toJson(errorStats: ErrorStats, dataFrom: Option[Long] = None): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(errorStats, dataFrom, gen)
    gen.flush()
    writer.toString
  }

  def toJson(stats: Iterable[ErrorStats]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartArray()
    for (s ← stats) writeJson(s, None, gen)
    gen.writeEndArray()
    gen.flush()
    writer.toString
  }

  def writeJson(errorStats: ErrorStats, dataFrom: Option[Long], gen: JsonGenerator) {
    gen.writeStartObject()
    writeJson(errorStats.timeRange, gen)
    for (x ← errorStats.node) gen.writeStringField("node", x)
    for (x ← errorStats.actorSystem) gen.writeStringField("actorSystem", x)

    gen.writeNumberField("deviationCount", errorStats.metrics.counts.total)
    for (x ← dataFrom) gen.writeNumberField("dataFrom", x)
    gen.writeNumberField("errorCount", errorStats.metrics.counts.errors)
    gen.writeArrayFieldStart("errors")
    for (deviation ← errorStats.metrics.deviations.errors) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeNumberField("warningCount", errorStats.metrics.counts.warnings)
    gen.writeArrayFieldStart("warnings")
    for (deviation ← errorStats.metrics.deviations.warnings) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeNumberField("deadLetterCount", errorStats.metrics.counts.deadLetters)
    gen.writeArrayFieldStart("deadLetters")
    for (deviation ← errorStats.metrics.deviations.deadLetters) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeNumberField("unhandledMessageCount", errorStats.metrics.counts.unhandledMessages)
    gen.writeArrayFieldStart("unhandledMessages")
    for (deviation ← errorStats.metrics.deviations.unhandledMessages) {
      gen.writeStartObject()
      gen.writeLinkOrId("event", EventUri, deviation.eventId)
      gen.writeLinkOrId("trace", TraceTreeUri, deviation.traceId)
      gen.writeStringField("message", deviation.message)
      gen.writeTimestampField("timestamp", deviation.timestamp)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeNumberField("deadlockCount", errorStats.metrics.counts.deadlocks)
    gen.writeArrayFieldStart("deadlocks")
    for (deviation ← errorStats.metrics.deviations.deadlockedThreads) {
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
}
