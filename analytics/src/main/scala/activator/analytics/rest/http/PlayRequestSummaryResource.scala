/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data._
import activator.analytics.repository.PlayRequestSummaryRepository
import com.typesafe.trace._
import com.typesafe.trace.uuid.UUID
import com.typesafe.trace.store.TraceRetrievalRepository
import GatewayActor._
import java.io.StringWriter
import org.codehaus.jackson.JsonGenerator
import spray.http.StatusCodes
import PlayRequestSummaryResource._
import activator.analytics.AnalyticsExtension
import com.typesafe.trace.ActionChunkedResult
import com.typesafe.trace.ActionSimpleResult
import scala.Some
import spray.http.HttpResponse
import com.typesafe.trace.ActionRequestInfo
import spray.http.HttpRequest
import com.typesafe.trace.ActionInvocationInfo
import com.typesafe.trace.ActionResolvedInfo
import com.typesafe.trace.ActorInfo

class PlayRequestSummaryResource(playRequestSummaryRepository: PlayRequestSummaryRepository,
                                 traceRepository: TraceRetrievalRepository) extends RestResourceActor {

  val queryBuilder = new QueryBuilder(AnalyticsExtension(context.system).PagingSize)

  def handle(req: HttpRequest): HttpResponse = {
    val path = req.uri.path.toString
    path match {
      case _ if path.contains(SummaryMultipleEventsUri) ⇒ restEvents(req)
      case SummaryEventPattern(eventId) ⇒ restEvent(req, eventId)
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest).asJson
    }
  }

  def restEvent(req: HttpRequest, id: String): HttpResponse = {
    parseUUID(id) match {
      case Right(uuid) ⇒
        val result = playRequestSummaryRepository.find(uuid)
        val actorInfo: Set[ActorInfo] = traceRepository.trace(uuid).view
          .map(_.annotation)
          .filter(_.isInstanceOf[ActorAnnotation])
          .map(_.asInstanceOf[ActorAnnotation].info)
          .toSet
        val representation = playRequestSummaryJsonRepresentation(req).toJson(result, actorInfo)
        HttpResponse(entity = representation).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  /**
   * If offset is defined in the query then paging will be used.
   * If not then the latest "limit" number of events will be retrieved.
   */
  def restEvents(req: HttpRequest): HttpResponse = {
    val result = queryBuilder.build(req.uri.query.toString)
    result match {
      case Right(query) ⇒
        val offset = query.offset getOrElse 0
        val limit = query.limit
        val sortOn = query.sortOn
        val sortDirection = query.sortDirection
        val requestSummaries =
          if (query.offset.isDefined)
            playRequestSummaryRepository.findRequestsWithinTimePeriod(
              query.timeRange.startTime,
              query.timeRange.endTime,
              offset,
              limit,
              sortOn,
              sortDirection)
          else
            playRequestSummaryRepository.findRequestsWithinTimePeriod(
              query.timeRange.startTime,
              query.timeRange.endTime,
              1,
              limit,
              sortOn,
              sortDirection)
        val actorInfos: Seq[Set[ActorInfo]] = requestSummaries.map { rs ⇒
          traceRepository.trace(rs.traceId).view
            .map(_.annotation)
            .filter(_.isInstanceOf[ActorAnnotation])
            .map(_.asInstanceOf[ActorAnnotation].info)
            .toSet
        }

        val nextPosition = if (requestSummaries.size == limit) Some(limit + offset) else None
        val representation = playRequestSummariesJsonRepresentation(req)
          .toJson(requestSummaries.zip(actorInfos), query.timeRange, Paging(offset, nextPosition, limit))
        HttpResponse(entity = representation).asJson

      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  def parseUUID(id: String): Either[String, UUID] = {
    try {
      Right(new UUID(id))
    } catch {
      case e: RuntimeException ⇒ Left("Invalid uuid [%s]".format(id))
    }
  }

  def playRequestSummaryJsonRepresentation(request: HttpRequest) =
    new PlayRequestSummaryRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def playRequestSummariesJsonRepresentation(request: HttpRequest) =
    new PlayRequestSummariesRepresentation(baseUrl(request), formatTimestamps(request), context.system)
}

object PlayRequestSummaryResource {
  val SummaryUri = PlayRequestSummaryUri + "/"
  val SummaryEventUri = SummaryUri + "event/"
  val SummaryMultipleEventsUri = SummaryUri + "multi"
  val SummaryEventPattern = """^.*/event\/([\w\-]+)""".r

  import Sorting._

  case class Query(timeRange: TimeRange, offset: Option[Int], limit: Int, sortOn: PlayStatsSort[_], sortDirection: SortDirection)

  class QueryBuilder(defaultLimit: Int) extends TimeRangeQueryBuilder with PagingQueryBuilder {
    def build(queryPath: String): Either[String, Query] = {
      def extractSortOn(queryPath: String): PlayStatsSort[_] = queryPath match {
        case SortOnPattern(sort) ⇒ sort match {
          case "time"         ⇒ PlayStatsSorts.TimeSort
          case "controller"   ⇒ PlayStatsSorts.ControllerSort
          case "method"       ⇒ PlayStatsSorts.MethodSort
          case "responseCode" ⇒ PlayStatsSorts.ResponseCodeSort
          case _              ⇒ PlayStatsSorts.InvocationTimeSort
        }
        case _ ⇒ PlayStatsSorts.TimeSort
      }

      def extractSortDirection(queryPath: String): SortDirection = queryPath match {
        case SortDirectionPattern(direction) ⇒ direction match {
          case "asc" ⇒ ascendingSort
          case _     ⇒ descendingSort
        }
        case _ ⇒ descendingSort
      }

      extractTime(queryPath) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒
          val offset = extractOffset(queryPath)
          val limit = extractLimit(queryPath) getOrElse defaultLimit
          val sortOn = extractSortOn(queryPath)
          val sortDirection = extractSortDirection(queryPath)
          Right(Query(timeRange, offset, limit, sortOn, sortDirection))
      }
    }
  }

  val TimeRangeToQueryParametersTemplate = "startTime=%s&endTime=%s"
  val PagingQueryParametersTemplate = "offset=%s&limit=%s"
  val SortOnPattern = """^.*sortOn=([\w\-]+)&?.*?""".r
  val SortDirectionPattern = """^.*sortDirection=([\w\+])&?.*?""".r

  def timeRangeToQueryParameters(timeRange: TimeRange): Option[String] = {
    if (timeRange.rangeType == TimeRangeType.AllTime) {
      None
    } else {
      val str = TimeRangeToQueryParametersTemplate.format(
        TimeParser.format(timeRange.startTime),
        TimeParser.format(timeRange.endTime))
      Some(str)
    }
  }

  def nextQueryParameters(paging: Paging, timeRange: TimeRange): Option[String] = {
    paging.nextPosition map {
      next ⇒
        val pagingParams = PagingQueryParametersTemplate.format(next, paging.limit)
        val timeQueryParams = timeRangeToQueryParameters(timeRange)
        pagingParams + timeQueryParams.map("&" + _).getOrElse("")
    }

  }

}

object PlayRequestSummaryRepresentation {
  def writeActionRequestInfoJson(fieldName: String, requestInfo: ActionRequestInfo, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)

    generator.writeNumberField("id", requestInfo.id)
    generator.writeObjectFieldStart("tags")
    requestInfo.tags.foreach { case (k, v) ⇒ generator.writeStringField(k, v) }
    generator.writeEndObject()
    generator.writeStringField("uri", requestInfo.uri)
    generator.writeStringField("path", requestInfo.path)
    generator.writeStringField("method", requestInfo.method)
    generator.writeStringField("version", requestInfo.version)
    generator.writeObjectFieldStart("queryString")
    requestInfo.queryString.foreach {
      case (k, v) ⇒
        generator.writeArrayFieldStart(k)
        v.foreach(generator.writeString)
        generator.writeEndArray()
    }
    generator.writeEndObject()
    generator.writeObjectFieldStart("headers")
    requestInfo.headers.foreach {
      case (k, v) ⇒
        generator.writeArrayFieldStart(k)
        v.foreach(generator.writeString)
        generator.writeEndArray()
    }
    generator.writeEndObject()

    generator.writeEndObject()
  }

  def writeInvocationInfo(fieldName: String, info: ActionInvocationInfo, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)
    generator.writeStringField("controller", info.controller)
    generator.writeStringField("method", info.method)
    generator.writeStringField("pattern", info.pattern)
    generator.writeNumberField("id", info.id)
    generator.writeStringField("uri", info.uri)
    generator.writeStringField("path", info.path)
    generator.writeStringField("httpMethod", info.httpMethod)
    generator.writeStringField("version", info.version)
    generator.writeStringField("remoteAddress", info.remoteAddress)
    info.host.foreach(x ⇒ generator.writeStringField("host", x))
    info.domain.foreach(x ⇒ generator.writeStringField("domain", x))
    info.session.foreach { x ⇒
      generator.writeObjectFieldStart("session")
      x.foreach {
        case (key, value) ⇒
          generator.writeStringField(key, value)
      }
      generator.writeEndObject()
    }
    generator.writeEndObject()
  }

  def writeResolvedInfo(fieldName: String, info: ActionResolvedInfo, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)
    generator.writeStringField("controller", info.controller)
    generator.writeStringField("method", info.method)
    generator.writeArrayFieldStart("parameterTypes")
    info.parameterTypes.foreach(generator.writeString)
    generator.writeEndArray()
    generator.writeStringField("verb", info.verb)
    generator.writeStringField("comments", info.comments)
    generator.writeStringField("path", info.path)
    generator.writeEndObject()
  }

  def writeResponse(fieldName: String, info: ActionResponseAnnotation, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)
    info match {
      case _: ActionChunkedResult ⇒ generator.writeStringField("type", "chunked")
      case _: ActionSimpleResult  ⇒ generator.writeStringField("type", "simple")
    }
    generator.writeNumberField("httpResponseCode", info.resultInfo.httpResponseCode)
    generator.writeEndObject()
  }
}

class PlayRequestSummaryRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  import PlayRequestSummaryRepresentation._

  def toJson(requestSummaryOption: Option[PlayRequestSummary], actorInfo: Set[ActorInfo]): String = requestSummaryOption match {
    case None ⇒ "{}"
    case Some(requestSummary) ⇒
      val writer = new StringWriter
      val generator = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
      writeJson(requestSummary, actorInfo, generator)
      generator.flush()
      writer.toString
  }

  def writeJson(requestSummary: PlayRequestSummary, actorInfo: Set[ActorInfo], generator: JsonGenerator, objectFieldName: String = "") {
    if (objectFieldName == "") generator.writeStartObject()
    else generator.writeObjectFieldStart(objectFieldName)

    generator.writeLinkOrId("traceId", TraceEventResource.TraceTreeUri, requestSummary.traceId)
    generator.writeStringField("node", requestSummary.node)
    generator.writeStringField("host", requestSummary.host)
    generator.writeStringField("summaryType", requestSummary.summaryType.toString)
    writeActionRequestInfoJson("requestInfo", requestSummary.requestInfo, generator)
    generator.writeTimestampField("startMillis", requestSummary.start.millis)
    generator.writeNumberField("startNanoTime", requestSummary.start.nanoTime)
    generator.writeTimestampField("endMillis", requestSummary.end.millis)
    generator.writeNumberField("endNanoTime", requestSummary.end.nanoTime)
    generator.writeNumberField("duration", requestSummary.duration)
    writeInvocationInfo("invocationInfo", requestSummary.invocationInfo, generator)
    writeResponse("response", requestSummary.response, generator)
    generator.writeArrayFieldStart("asyncResponseNanoTimes")
    requestSummary.asyncResponseNanoTimes.foreach(generator.writeNumber)
    generator.writeEndArray()
    generator.writeArrayFieldStart("actorInfo")
    actorInfo.foreach(ai ⇒ TraceEventRepresentation.writeActorInfoOption(None, ai, generator))
    generator.writeEndArray()
    generator.writeNumberField("inputProcessingDuration", requestSummary.inputProcessingDuration)
    generator.writeTimestampField("actionExecutionMillis", requestSummary.actionExecution.millis)
    generator.writeNumberField("actionExecutionNanoTime", requestSummary.actionExecution.nanoTime)
    generator.writeNumberField("actionExecutionDuration", requestSummary.actionExecutionDuration)
    generator.writeTimestampField("outputProcessingMillis", requestSummary.outputProcessing.millis)
    generator.writeNumberField("outputProcessingNanoTime", requestSummary.outputProcessing.nanoTime)
    generator.writeNumberField("outputProcessingDuration", requestSummary.outputProcessingDuration)
    generator.writeNumberField("bytesIn", requestSummary.bytesIn)
    generator.writeNumberField("bytesOut", requestSummary.bytesOut)
    requestSummary.errorMessage.foreach(e ⇒ generator.writeStringField("errorMessage", e))
    requestSummary.stackTrace.foreach { st ⇒
      generator.writeArrayFieldStart("stackTrace")
      st.foreach(generator.writeString)
      generator.writeEndArray()
    }

    generator.writeEndObject()
  }
}

class PlayRequestSummariesRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  val requestSummaryRepresentation = new PlayRequestSummaryRepresentation(baseUrl, formatTimestamps, system)

  def toJson(requestSummaries: Seq[(PlayRequestSummary, Set[ActorInfo])], timeRange: TimeRange, paging: Paging): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(requestSummaries, timeRange, paging, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(requestSummaries: Seq[(PlayRequestSummary, Set[ActorInfo])], timeRange: TimeRange, paging: Paging, generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeArrayFieldStart("playRequestSummaries")
    for { (requestSummary, actorInfo) ← requestSummaries } {
      requestSummaryRepresentation.writeJson(requestSummary, actorInfo, generator)
    }
    generator.writeEndArray()
    generator.writeNumberField("offset", paging.offset)
    generator.writeNumberField("limit", paging.limit)
    for (p ← paging.nextPosition) {
      generator.writeNumberField("nextPosition", p)
      if (useLinks) {
        val queryParams = nextQueryParameters(paging, timeRange)
        val uri = SummaryMultipleEventsUri + "?" + queryParams.getOrElse("")
        generator.writeLink("next", uri)
      }
    }

    generator.writeEndObject()
  }
}
