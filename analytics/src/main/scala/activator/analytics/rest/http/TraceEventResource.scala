/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ ActorSystem, Actor }
import activator.analytics.data.{ TimeRangeType, TimeRange, SpanType, Span }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.SpanRepository
import com.typesafe.atmos.trace._
import com.typesafe.atmos.uuid.UUID
import com.typesafe.atmos.util.Uuid
import GatewayActor._
import java.io.StringWriter
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpRequest, HttpResponse }
import store.TraceRetrievalRepository
import TraceEventResource._

class TraceEventResource(traceRepository: TraceRetrievalRepository, spanRepository: SpanRepository) extends RestResourceActor {

  val queryBuilder = new QueryBuilder(RestExtension(context.system).PagingSize)

  def handle(req: HttpRequest): HttpResponse = {
    val path = req.uri.path.toString
    path match {
      case _ if path.contains(TraceEventsIntervalPath) ⇒ queryTraceEvents(req)
      case _ if path.contains(SpansIntervalPath) ⇒ querySpans(req)
      case TraceEventPattern(eventId) ⇒ queryTraceEvent(req, eventId)
      case SpanPattern(spanId) ⇒ querySpan(req, spanId)
      case TraceTreePattern(traceId) ⇒ queryTraceTree(req, traceId)
      case TraceContextPattern(traceId) ⇒ queryTraceContext(req, traceId)
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest).asJson
    }
  }

  def queryTraceEvent(req: HttpRequest, id: String): HttpResponse = {
    parseUUID(id) match {
      case Right(uuid) ⇒
        val result = traceRepository.event(uuid)
        val representation = traceEventJsonRepresentation(req).toJson(result)
        HttpResponse(entity = representation).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  def queryTraceEvents(req: HttpRequest): HttpResponse = {
    val result = queryBuilder.build(req.uri.query.toString)
    result match {
      case Right(query) ⇒
        val offset = query.offset
        val limit = query.limit
        val traceEvents =
          traceRepository.findEventsWithinTimePeriod(query.timeRange.startTime, query.timeRange.endTime, offset, limit)
        val nextPosition = if (traceEvents.size == limit) Some(limit + offset) else None
        val representation = traceEventsJsonRepresentation(req)
          .toJson(traceEvents, query.timeRange, Paging(offset, nextPosition, limit))
        HttpResponse(entity = representation).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  def queryTraceTree(req: HttpRequest, id: String): HttpResponse = {
    parseUUID(id) match {
      case Right(uuid) ⇒
        val traceEvents = traceRepository.trace(uuid)
        val traceTree = TraceTree(traceEvents)
        val representation = traceTreeJsonRepresentation(req).toJson(traceTree)
        val time = if (traceEvents.isEmpty) System.currentTimeMillis else (traceEvents.last.timestamp)
        HttpResponse(entity = representation, headers = HeadersBuilder.headers(time)).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  def queryTraceContext(req: HttpRequest, id: String): HttpResponse = {
    parseUUID(id) match {
      case Right(uuid) ⇒
        val traceEvents = traceRepository.trace(uuid)
        val representation = traceJsonRepresentation(req).toJson(traceEvents)
        val time = if (traceEvents.isEmpty) System.currentTimeMillis else (traceEvents.last.timestamp)
        HttpResponse(entity = representation, headers = HeadersBuilder.headers(time)).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  def querySpan(req: HttpRequest, id: String): HttpResponse = {
    parseUUID(id) match {
      case Right(uuid) ⇒
        val result = spanRepository.findBySpanId(uuid)
        val representation = spanJsonRepresentation(req).toJson(result)
        val time = if (result.isEmpty) System.currentTimeMillis else (result.get.startTime)
        HttpResponse(entity = representation, headers = HeadersBuilder.headers(time)).asJson
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

  def querySpans(req: HttpRequest): HttpResponse = {
    val result = queryBuilder.build(req.uri.query.toString)
    result match {
      case Right(query) ⇒
        val offset = query.offset
        val limit = query.limit
        val spans = spanRepository.findWithinTimePeriod(query.timeRange, offset, limit)
        val nextPosition = if (spans.size == limit) Some(limit + offset) else None
        val representation = spansJsonRepresentation(req).
          toJson(spans, query.timeRange, Paging(offset, nextPosition, limit))
        HttpResponse(entity = representation, headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }

  def traceEventJsonRepresentation(request: HttpRequest) =
    new TraceEventRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def traceEventsJsonRepresentation(request: HttpRequest) =
    new TraceEventsRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def traceTreeJsonRepresentation(request: HttpRequest) =
    new TraceTreeRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def traceJsonRepresentation(request: HttpRequest) =
    new TraceRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def spanJsonRepresentation(request: HttpRequest) =
    new SpanRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def spansJsonRepresentation(request: HttpRequest) =
    new SpansRepresentation(baseUrl(request), formatTimestamps(request), context.system)

}

object TraceEventResource {
  val TraceEventsIntervalPath = "events"
  val SpansIntervalPath = "spans"

  val TraceUri = TraceEventUri + "/"
  val TraceTreeUri = TraceUri + "tree/"
  val EventUri = TraceUri + "event/"
  val SpanUri = TraceUri + "span/"
  val TraceEventsUri = TraceUri + TraceEventsIntervalPath
  val SpansUri = TraceUri + SpansIntervalPath

  val TraceTreePattern = """^.*/tree\/([\w\-]+)""".r
  val TraceEventPattern = """^.*/event\/([\w\-]+)""".r
  val SpanPattern = """^.*/span\/([\w\-]+)""".r
  val TraceContextPattern = """^.*/([\w\-]+)""".r

  case class Query(timeRange: TimeRange, offset: Int, limit: Int)

  class QueryBuilder(defaultLimit: Int) extends TimeRangeQueryBuilder with PagingQueryBuilder {
    def build(queryPath: String): Either[String, Query] = {
      extractTime(queryPath) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒
          val offset = extractOffset(queryPath) getOrElse 1
          val limit = extractLimit(queryPath) getOrElse defaultLimit
          Right(Query(timeRange, offset, limit))
      }
    }
  }

  val TimeRangeToQueryParametersTemplate = "startTime=%s&endTime=%s"
  val PagingQueryParametersTemplate = "offset=%s&limit=%s"

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

object TraceEventRepresentation {
  def writeActorInfo(fieldName: String, info: ActorInfo, generator: JsonGenerator): Unit =
    writeActorInfoOption(Some(fieldName), info, generator)

  def writeActorInfoOption(fieldName: Option[String], info: ActorInfo, generator: JsonGenerator) {
    fieldName.map(fn ⇒ generator.writeObjectFieldStart(fn)).getOrElse(generator.writeStartObject())
    generator.writeStringField("actorPath", info.path)
    for (dispatcher ← info.dispatcher) generator.writeStringField("dispatcher", dispatcher)
    generator.writeBooleanField("remote", info.remote)
    generator.writeBooleanField("router", info.router)
    generator.writeArrayFieldStart("tags")
    for (tag ← info.tags) generator.writeString(tag)
    generator.writeEndArray()
    generator.writeEndObject()
  }
}

class TraceEventRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  import TraceEventRepresentation._

  def toJson(traceEventOption: Option[TraceEvent]): String = traceEventOption match {
    case None ⇒ "{}"
    case Some(traceEvent) ⇒
      val writer = new StringWriter
      val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
      writeJson(traceEvent, generator)
      generator.flush()
      writer.toString
  }

  def writeJson(traceEvent: TraceEvent, generator: JsonGenerator, objectFieldName: String = "") {
    if (objectFieldName == "") generator.writeStartObject()
    else generator.writeObjectFieldStart(objectFieldName)

    generator.writeLinkOrId("id", EventUri, traceEvent.id)
    generator.writeLinkOrId("trace", TraceTreeUri, traceEvent.trace)
    if (!Uuid.isZero(traceEvent.parent)) {
      generator.writeLinkOrId("parent", EventUri, traceEvent.parent)
    }
    generator.writeNumberField("sampled", traceEvent.sampled)
    generator.writeStringField("node", traceEvent.node)
    generator.writeStringField("actorSystem", traceEvent.actorSystem)
    generator.writeStringField("host", traceEvent.host)
    generator.writeTimestampField("timestamp", traceEvent.timestamp)
    generator.writeNumberField("nanoTime", traceEvent.nanoTime)

    generator.writeObjectFieldStart("annotation")

    val typeValue = {
      val name = traceEvent.annotation.getClass.getSimpleName
      // case objects ends with $
      if (name.endsWith("$")) name.dropRight(1) else name
    }
    generator.writeStringField("type", typeValue)

    traceEvent.annotation match {
      case x: ActorAnnotation ⇒
        writeActorInfo("actorInfo", x.info, generator)
        x match {
          case ActorRequested(info, actor)  ⇒ writeActorInfo("actor", actor, generator)
          case ActorReceived(info, message) ⇒ generator.writeStringField("message", message)
          case ActorTold(info, message, sender) ⇒
            generator.writeStringField("message", message)
            for (s ← sender) writeActorInfo("sender", s, generator)
          case ActorCompleted(info, message) ⇒ generator.writeStringField("message", message)
          case ActorFailed(info, reason, supervisor) ⇒
            generator.writeStringField("reason", reason)
            writeActorInfo("supervisor", supervisor, generator)
          case ActorAutoReceived(info, message)  ⇒ generator.writeStringField("message", message)
          case ActorAutoCompleted(info, message) ⇒ generator.writeStringField("message", message)
          case ActorAsked(info, message)         ⇒ generator.writeStringField("message", message)
          case _                                 ⇒
        }

      case x: SysMsgAnnotation ⇒
        val sysMsgType = x.message.getClass.getSimpleName.replace("SysMsg$", "").replace("SysMsg", "")
        writeActorInfo("actorInfo", x.info, generator)
        generator.writeStringField("sysMsgType", sysMsgType)
        writeSysMsg(x.message, generator)

      case x: ActorSelectionAnnotation ⇒
        writeActorSelectionInfo("actorSelectionInfo", x.info, generator)
        x match {
          case ActorSelectionTold(info, message, sender) ⇒
            generator.writeStringField("message", message)
            for (s ← sender) writeActorInfo("sender", s, generator)
          case ActorSelectionAsked(info, message) ⇒
            generator.writeStringField("message", message)
        }

      case x: FutureAnnotation ⇒
        x match {
          case FutureScheduled(info, taskInfo) ⇒
            writeTaskInfo("taskInfo", taskInfo, generator)
          case FutureSucceeded(info, result, resultInfo) ⇒
            generator.writeStringField("result", result)
            writeInfo("resultInfo", resultInfo, generator)
          case FutureFailed(info, exception) ⇒
            generator.writeStringField("cause", exception)
          case FutureAwaitTimedOut(info, duration) ⇒
            generator.writeStringField("timeout", duration)
          case _ ⇒
        }
        writeFutureInfo("futureInfo", x.info, generator)

      case x: RunnableAnnotation ⇒ writeTaskInfo("taskInfo", x.info, generator)

      case x: ScheduledAnnotation ⇒
        x match {
          case ScheduledOnce(info, delay) ⇒ generator.writeStringField("delay", delay)
          case _                          ⇒
        }
        writeTaskInfo("taskInfo", x.info, generator)

      case x: RemoteMessageAnnotation ⇒
        x match {
          case RemoteMessageSent(info, message, messageSize) ⇒
            generator.writeNumberField("messageSize", messageSize)
          case RemoteMessageReceived(info, message, messageSize) ⇒
            generator.writeNumberField("messageSize", messageSize)
          case _ ⇒
        }
        generator.writeStringField("message", x.message)
        writeActorInfo("actorInfo", x.info, generator)

      case x: EventStreamAnnotation ⇒
        generator.writeStringField("message", x.message)
        x match {
          case EventStreamDeadLetter(message, sender, recipient) ⇒
            writeActorInfo("sender", sender, generator)
            writeActorInfo("recipient", recipient, generator)
          case EventStreamUnhandledMessage(message, sender, recipient) ⇒
            writeActorInfo("sender", sender, generator)
            writeActorInfo("recipient", recipient, generator)
          case _ ⇒
        }

      case MarkerStarted(name) ⇒ generator.writeStringField("name", name)
      case MarkerEnded(name)   ⇒ generator.writeStringField("name", name)
      case Marker(name, data) ⇒
        generator.writeStringField("name", name)
        generator.writeStringField("data", data)

      case GroupStarted(name) ⇒ generator.writeStringField("name", name)
      case GroupEnded(name)   ⇒ generator.writeStringField("name", name)

      case RemoteStatus(statusType, serverNode, clientNode, cause) ⇒
        generator.writeStringField("statusType", statusType.toString)
        for (x ← serverNode) generator.writeStringField("serverNode", x)
        for (x ← clientNode) generator.writeStringField("clientNode", x)
        for (x ← cause) generator.writeStringField("cause", x)

      case status: DispatcherStatus ⇒
        generator.writeStringField("dispatcher", status.dispatcher)
        generator.writeNumberField("corePoolSize", status.metrics.corePoolSize)
        generator.writeNumberField("maximumPoolSize", status.metrics.maximumPoolSize)
        generator.writeNumberField("keepAliveTime", status.metrics.keepAliveTime)
        generator.writeStringField("rejectedHandler", status.metrics.rejectedHandler)
        generator.writeNumberField("activeThreadCount", status.metrics.activeThreadCount)
        generator.writeNumberField("taskCount", status.metrics.taskCount)
        generator.writeNumberField("completedTaskCount", status.metrics.completedTaskCount)
        generator.writeNumberField("largestPoolSize", status.metrics.largestPoolSize)
        generator.writeNumberField("poolSize", status.metrics.poolSize)
        generator.writeNumberField("queueSize", status.metrics.queueSize)

      case metrics: SystemMetrics ⇒
        generator.writeNumberField("runningActors", metrics.runningActors)
        generator.writeTimestampField("startTime", metrics.startTime)
        generator.writeNumberField("upTime", metrics.upTime)
        generator.writeNumberField("availableProcessors", metrics.availableProcessors)
        generator.writeNumberField("daemonThreadCount", metrics.daemonThreadCount)
        generator.writeNumberField("threadCount", metrics.threadCount)
        generator.writeNumberField("peakThreadCount", metrics.peakThreadCount)
        generator.writeNumberField("committedHeap", metrics.committedHeap)
        generator.writeNumberField("maxHeap", metrics.maxHeap)
        generator.writeNumberField("usedHeap", metrics.usedHeap)
        generator.writeNumberField("committedNonHeap", metrics.committedNonHeap)
        generator.writeNumberField("maxNonHeap", metrics.maxNonHeap)
        generator.writeNumberField("usedNonHeap", metrics.usedNonHeap)
        generator.writeNumberField("systemLoadAverage", metrics.systemLoadAverage)

      case deadlockedThreads: DeadlockedThreads ⇒
        generator.writeStringField("message", deadlockedThreads.message)
        generator.writeArrayFieldStart("deadlocks")
        for (deadlock ← deadlockedThreads.deadlocks) {
          generator.writeString(deadlock)
        }
        generator.writeEndArray()

      case x: ActionAnnotation ⇒
        import PlayRequestSummaryRepresentation._
        x match {
          case ActionResolved(resolutionInfo) ⇒ writeResolvedInfo("resolutionInfo", resolutionInfo, generator)
          case ActionInvoked(invocationInfo)  ⇒ writeInvocationInfo("invocationInfo", invocationInfo, generator)
          case ActionResultGenerationStart    ⇒
          case ActionResultGenerationEnd      ⇒
          case ActionChunkedInputStart        ⇒
          case ActionChunkedInputEnd          ⇒
          case ActionChunkedResult(resultInfo) ⇒
            generator.writeNumberField("httpResponseCode", resultInfo.httpResponseCode)
          case ActionSimpleResult(resultInfo) ⇒
            generator.writeNumberField("httpResponseCode", resultInfo.httpResponseCode)
          case ActionAsyncResult ⇒
          case ActionRouteRequest(requestInfo, result) ⇒
            writeActionRequestInfoJson("requestInfo", requestInfo, generator)
            generator.writeStringField("result", result.toString)
          case ActionError(requestInfo, message, stackTrace) ⇒
            writeActionRequestInfoJson("requestInfo", requestInfo, generator)
            generator.writeStringField("message", message)
            generator.writeArrayFieldStart("stackTrace")
            stackTrace.foreach(generator.writeString)
            generator.writeEndArray()
          case ActionHandlerNotFound(requestInfo) ⇒
            writeActionRequestInfoJson("requestInfo", requestInfo, generator)
          case ActionBadRequest(requestInfo, error) ⇒
            writeActionRequestInfoJson("requestInfo", requestInfo, generator)
            generator.writeStringField("error", error)
        }

      case x: NettyAnnotation ⇒
        x match {
          case NettyHttpReceivedStart ⇒
          case NettyHttpReceivedEnd   ⇒
          case NettyPlayReceivedStart ⇒
          case NettyPlayReceivedEnd   ⇒
          case NettyResponseHeader(size) ⇒
            generator.writeNumberField("size", size)
          case NettyResponseBody(size) ⇒
            generator.writeNumberField("size", size)
          case NettyWriteChunk(overhead, size) ⇒
            generator.writeNumberField("overhead", overhead)
            generator.writeNumberField("size", size)
          case NettyReadBytes(size) ⇒
            generator.writeNumberField("size", size)
        }

      case x: IterateeAnnotation ⇒
        x match {
          case IterateeCreated(info) ⇒
            writeIterateeInfo("info", info, generator)
          case IterateeFolded(info) ⇒
            writeIterateeInfo("info", info, generator)
          case IterateeDone(info) ⇒
            writeIterateeInfo("info", info, generator)
          case IterateeContinued(info, input: IterateeInput.Tag, nextInfo) ⇒
            writeIterateeInfo("info", info, generator)
            (input: @unchecked) match {
              case IterateeInput.El(value) ⇒
                generator.writeStringField("inputType", "El")
                generator.writeStringField("inputValue", value)
              case IterateeInput.Empty ⇒
                generator.writeStringField("inputType", "Empty")
              case IterateeInput.EOF ⇒
                generator.writeStringField("inputType", "EOF")
            }
            writeIterateeInfo("nextInfo", nextInfo, generator)
          case IterateeError(info) ⇒
            writeIterateeInfo("info", info, generator)
        }

      case _ ⇒
    }
    generator.writeEndObject()

    generator.writeEndObject()
  }

  def writeIterateeInfo(fieldName: String, info: IterateeInfo, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)
    generator.writeStringField("uuid", info.uuid.toString)
    generator.writeEndObject()
  }

  def writeInfo(fieldName: String, info: Info, generator: JsonGenerator): Unit = {
    info match {
      case i: ActorInfo          ⇒ writeActorInfo(fieldName, i, generator)
      case i: ActorSelectionInfo ⇒ writeActorSelectionInfo(fieldName, i, generator)
      case i: FutureInfo         ⇒ writeFutureInfo(fieldName, i, generator)
      case i: TaskInfo           ⇒ writeTaskInfo(fieldName, i, generator)
      case i: IterateeInfo       ⇒ writeIterateeInfo(fieldName, i, generator)
      case _                     ⇒
    }
  }

  def writeActorSelectionInfo(fieldName: String, info: ActorSelectionInfo, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)
    writeActorInfo("anchorInfo", info.anchor, generator)
    generator.writeStringField("selectionPath", info.path)
    generator.writeEndObject()
  }

  def writeFutureInfo(fieldName: String, info: FutureInfo, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)
    generator.writeStringField("uuid", info.uuid.toString)
    generator.writeEndObject()
  }

  def writeTaskInfo(fieldName: String, taskInfo: TaskInfo, generator: JsonGenerator) {
    generator.writeObjectFieldStart(fieldName)
    generator.writeStringField("uuid", taskInfo.uuid.toString)
    generator.writeStringField("dispatcher", taskInfo.dispatcher)
    generator.writeEndObject()
  }

  def writeSysMsg(message: SysMsg, generator: JsonGenerator) {
    message match {
      case RecreateSysMsg(cause) ⇒
        generator.writeStringField("cause", cause)
      case SuperviseSysMsg(child) ⇒
        writeActorInfo("child", child, generator)
      case ChildTerminatedSysMsg(child) ⇒
        writeActorInfo("child", child, generator)
      case LinkSysMsg(subject) ⇒
        writeActorInfo("subject", subject, generator)
      case UnlinkSysMsg(subject) ⇒
        writeActorInfo("subject", subject, generator)
      case WatchSysMsg(watchee, watcher) ⇒
        writeActorInfo("watchee", watchee, generator)
        writeActorInfo("watcher", watcher, generator)
      case UnwatchSysMsg(watchee, watcher) ⇒
        writeActorInfo("watchee", watchee, generator)
        writeActorInfo("watcher", watcher, generator)
      case FailedSysMsg(child, cause) ⇒
        writeActorInfo("child", child, generator)
        generator.writeStringField("cause", cause)
      case DeathWatchSysMsg(actor, existenceConfirmed, addressTerminated) ⇒
        writeActorInfo("watched", actor, generator)
        generator.writeBooleanField("existenceConfirmed", existenceConfirmed)
        generator.writeBooleanField("addressTerminated", addressTerminated)
      case _ ⇒
    }
  }
}

class TraceEventsRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  val traceEventRepresentation = new TraceEventRepresentation(baseUrl, formatTimestamps, system)

  def toJson(traceEvents: Seq[TraceEvent], timeRange: TimeRange, paging: Paging): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(traceEvents, timeRange, paging, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(traceEvents: Seq[TraceEvent], timeRange: TimeRange, paging: Paging, generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeArrayFieldStart("traceEvents")
    for (traceEvent ← traceEvents) {
      traceEventRepresentation.writeJson(traceEvent, generator)
    }
    generator.writeEndArray()
    generator.writeNumberField("offset", paging.offset)
    generator.writeNumberField("limit", paging.limit)
    for (p ← paging.nextPosition) {
      generator.writeNumberField("nextPosition", p)
      if (useLinks) {
        val queryParams = nextQueryParameters(paging, timeRange)
        val uri = TraceEventsUri + "?" + queryParams.getOrElse("")
        generator.writeLink("next", uri)
      }
    }

    generator.writeEndObject()
  }
}

class TraceTreeRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  val traceEventRepresentation = new TraceEventRepresentation(baseUrl, formatTimestamps, system)

  def toJson(traceTree: TraceTree): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(traceTree, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(traceTree: TraceTree, generator: JsonGenerator) {
    generator.writeStartObject()
    for (root ← traceTree.root) {
      writeJson(root, generator)
    }
    generator.writeEndObject()
  }

  def writeJson(node: TraceTree.Node, generator: JsonGenerator) {
    traceEventRepresentation.writeJson(node.event, generator, "event")
    if (node.children.nonEmpty) {
      generator.writeArrayFieldStart("children")
      for (child ← node.children) {
        generator.writeStartObject()
        writeJson(child, generator)
        generator.writeEndObject()
      }
      generator.writeEndArray()
    }
  }
}

class TraceRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  val traceEventRepresentation = new TraceEventRepresentation(baseUrl, formatTimestamps, system)

  def toJson(traceEvents: Seq[TraceEvent]): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(traceEvents, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(traceEvents: Seq[TraceEvent], generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeArrayFieldStart("traceEvents")
    for (traceEvent ← traceEvents) {
      traceEventRepresentation.writeJson(traceEvent, generator)
    }
    generator.writeEndArray()
    generator.writeEndObject()
  }
}

class SpanRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  def toJson(spanOption: Option[Span]): String = spanOption match {
    case None ⇒ "{}"
    case Some(span) ⇒
      val writer = new StringWriter
      val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
      writeJson(span, generator)
      generator.flush()
      writer.toString
  }

  def writeJson(span: Span, generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeLinkOrId("id", SpanUri, span.id)
    generator.writeLinkOrId("trace", TraceTreeUri, span.trace)
    generator.writeStringField("spanTypeName", SpanType.formatSpanTypeName(span.spanTypeName))
    generator.writeTimestampField("startTime", span.startTime)
    generator.writeDurationField("duration", span.duration.toNanos)
    generator.writeNumberField("sampled", span.sampled)
    generator.writeBooleanField("remote", span.remote)
    generator.writeLinkOrId("startEvent", EventUri, span.startEvent)
    generator.writeLinkOrId("endEvent", EventUri, span.endEvent)
    generator.writeEndObject()
  }
}

class SpansRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  val spanRepresentation = new SpanRepresentation(baseUrl, formatTimestamps, system)

  def toJson(spans: Seq[Span], timeRange: TimeRange, paging: Paging): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(spans, timeRange, paging, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(spans: Seq[Span], timeRange: TimeRange, paging: Paging, generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeArrayFieldStart("spans")
    for (span ← spans) {
      spanRepresentation.writeJson(span, generator)
    }
    generator.writeEndArray()
    generator.writeNumberField("offset", paging.offset)
    generator.writeNumberField("limit", paging.limit)
    for (p ← paging.nextPosition) {
      generator.writeNumberField("nextPosition", p)
      if (useLinks) {
        val queryParams = nextQueryParameters(paging, timeRange)
        val uri = SpansUri + "?" + queryParams.getOrElse("")
        generator.writeLink("next", uri)
      }
    }

    generator.writeEndObject()
  }
}

case class Paging(offset: Int, nextPosition: Option[Int], limit: Int)
