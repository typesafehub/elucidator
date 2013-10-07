/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ ActorSystem, Actor, ActorLogging }
import activator.analytics.rest.RestExtension
import java.io.StringWriter
import java.io.Writer
import org.codehaus.jackson.JsonGenerator
import spray.http.{ HttpResponse, HttpRequest }

class RootResource extends Actor with ActorLogging with FileContentResource {
  import GatewayActor._

  def jsonRepresentation(req: HttpRequest) = new RootJsonRepresentation(baseUrl(req), context.system)

  def receive = {
    case JRequest(request) ⇒ sender ! handle(request)
  }

  def handle(req: HttpRequest): HttpResponse = handleFileRequest(req, context.system) match {
    case Some(r) ⇒ r
    case _       ⇒ HttpResponse(entity = jsonRepresentation(req).toJson())
  }
}

class RootJsonRepresentation(override val baseUrl: Option[String], system: ActorSystem) extends JsonRepresentation {
  import RootJsonRepresentation._
  import GatewayActor._

  def toJson(): String = {
    val writer = new StringWriter
    writeJson(writer)
    writer.toString
  }

  def writeJson(writer: Writer) {
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartObject()
    writeGeneralDescription(gen)
    gen.writeArrayFieldStart("resources")
    writeMetadata(gen)
    writeMetadataNodes(gen)
    writeMetadataActorSystems(gen)
    writeMetadataDispatchers(gen)
    writeMetadataTags(gen)
    writeMetadataPaths(gen)
    writeMetadataSpanTypes(gen)
    writeSummarySpanStats(gen)
    writeHistogramSpanStats(gen)
    writePercentilesSpanStats(gen)
    writeSpanTimeSeries(gen)
    writeActorStats(gen)
    writePlayStats(gen)
    writeMessageRateTimeSeries(gen)
    writeMessageRatePoint(gen)
    writeMailboxTimeSeries(gen)
    writeMailboxPoint(gen)
    writeDispatcherTimeSeries(gen)
    writeDispatcherPoint(gen)
    writeSystemMetricsTimeSeries(gen)
    writeSystemMetricsPoint(gen)
    writeErrorStats(gen)
    writeRemoteStatus(gen)
    writeTraceEvents(gen)
    writeSpans(gen)
    writeRecordStats(gen)

    gen.writeEndArray()
    gen.writeEndObject()
    gen.flush()
  }

  def writeGeneralDescription(gen: JsonGenerator): Unit = {
    gen.writeStringField("description",
      """|
         |Atmos Monitoring is a tailor-made tracing and monitoring product for event-driven Akka actor based systems.
         |It captures the asynchronous processing, and links the events into meaningful trace flows across actors and
         |remote nodes. Aggregated metrics and detailed trace flows are accessible through a REST API, described here.
         |
         |The REST API provides an easy way to integrate Atmos with other products for
         |monitoring and surveillance.
         |
         |Several queries supports search in two dimensions; scope and time.
         |
         |The scope dimension filters actors based on:
         |- actorPath - the unique name of the actor
         |- tag - one or more tags can be assigned to an actor with configuration
         |- dispatcher - the name of the message dispatcher of the actor
         |- node - the logical name of the node, which corresponds to a JVM
         |- actorSystem - the name of the ActorSystem of the actors,
         |         it is possible to run several ActorSystems in the same node (JVM),
         |         and an ActorSystem may span multiple nodes (JVMs)
         |
         |The time dimension can filter queries with a rolling window or an explicit time period.
         |The aggregated statistics is grouped by whole time period units, e.g. whole hours.
         |Note about resolution. When current time is June the 16th the following is true:
         |- rolling=1month is translated to the period 1 June - 30 June
         |- rolling=2months is translated to the period 1 May - 30 June
         |
         |For more fine grained resolution the day resolution can be used:
         |- rolling=30days is translated to the period  17 May - 16 June
         |- rolling=60days is translated to the period  17 April - 16 June
         |
         |All timestamps are in UTC time zone, in format yyyy-MM-ddTHH:mm:ss:SSS.
         |With formatTimestamps=off query paramter it is possible to turn off
         |formatting of timestamps and instead use milliseconds since midnight,
         |January 1, 1970 UTC.
         |
         |For the span related queries the name of the span type is part of the URI.
         |The 'spantype' can be one of:
         |- message - span for a message, from when it is sent to when the processing of the message is completed
         |- mailbox - span for the waiting time of message in actor mailbox
         |- receive - span for the processing time in actor receive method
         |- question - span between ask (?) and future completed (reply)
         |- remote - span for the remote latency
         |- name of any user defined marker span - a user defined span may start and end in any location in the
         |message trace, i.e. it may span over several actors.
         |""".stripMargin)
  }

  def writeMetadata(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Metadata")
    gen.writeStringField("description", "Brief metadata about the system (Nodes, Span Types, Actor Systems, etc.)")
    gen.writeLink("uri", MetadataStatsResource.MetadataUri)
    gen.writeLink("sample", MetadataStatsResource.MetadataUri + "?" + SampleRollingTimeParameter2 + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    writeTagFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMetadataNodes(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Metadata Nodes")
    gen.writeStringField("description", "Nodes in the system. The logical name of the (JVM) node.")
    gen.writeLink("uri", MetadataStatsResource.NodesUri)
    gen.writeLink("sample", MetadataStatsResource.NodesUri + "?" + SampleRollingTimeParameter2)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeDispatcherFilter(gen)
    writeTagFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMetadataActorSystems(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Metadata ActorSystems")
    gen.writeStringField("description", "ActorSystems in the system. The name and node of the ActorSystem.")
    gen.writeLink("uri", MetadataStatsResource.ActorSystemsUri)
    gen.writeLink("sample", MetadataStatsResource.ActorSystemsUri + "?" + SampleRollingTimeParameter2)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeTagFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMetadataDispatchers(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Metadata Dispatchers")
    gen.writeStringField("description", "Name of the message dispatchers in the system, and their ActorSystem and node.")
    gen.writeLink("uri", MetadataStatsResource.DispatchersUri)
    gen.writeLink("sample", MetadataStatsResource.DispatchersUri + "?" + SampleRollingTimeParameter2 + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    writeTagFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMetadataTags(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Metadata Tags")
    gen.writeStringField("description", "Actor tags in the system. One or more tags can be assigned to an actor with configuration.")
    gen.writeLink("uri", MetadataStatsResource.TagsUri)
    gen.writeLink("sample", MetadataStatsResource.TagsUri + "?" + SampleRollingTimeParameter2 + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorPathFilter(gen)
    writeDispatcherFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMetadataPaths(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Metadata Paths")
    gen.writeStringField("description", "Tree structure of the paths of the actors, grouped by node and actor system.")
    gen.writeLink("uri", MetadataStatsResource.PathsUri)
    gen.writeLink("sample", MetadataStatsResource.PathsUri + "?" + SampleRollingTimeParameter2 + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    writeActorPathFilter(gen)
    writeTagFilter(gen)
    writeDispatcherFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMetadataSpanTypes(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Metadata Span Types")
    gen.writeStringField("description", "Predefined and user defined span types in the system.")
    gen.writeLink("uri", MetadataStatsResource.SpanTypesUri)
    gen.writeLink("sample", MetadataStatsResource.SpanTypesUri + "?" + SampleRollingTimeParameter2)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeSummarySpanStats(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Summary Span Statistics")
    gen.writeStringField("description", "Statistics of overview character for the durations of the spans.")
    gen.writeLink("uri", SummarySpanStatsUri + "/{spanType}")
    gen.writeLink("spanTypeValues", MetadataStatsResource.SpanTypesUri)
    gen.writeLink("sample", SummarySpanStatsUri + "/message" + "?" + SampleRollingTimeParameter + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeScopeFilters(gen)
    writeChunksFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeHistogramSpanStats(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Histogram Span Statistics")
    gen.writeStringField("description",
      """|Histogram statistics for the durations of the spans. Histogram is number of spans that
         |fall into each duration interval (bucket). A value fall into a bucket when the value is
         |less than the corresponding bucket boundary and greater than or equal to the previous bucket
         |boundary. The last bucket contains all values greater than or equal to the last bucket boundary.
         |The first bucket contains all values less than the first bucket boundary.""".stripMargin)
    gen.writeLink("uri", HistogramSpanStatsUri + "/{spanType}")
    gen.writeLink("spanTypeValues", MetadataStatsResource.SpanTypesUri)
    gen.writeLink("sample", HistogramSpanStatsUri + "/message" + "?" + SampleRollingTimeParameter + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeScopeFilters(gen)
    writeBucketBoundariesFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writePercentilesSpanStats(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Percentiles Span Statistics")
    gen.writeStringField("description",
      """|Percentile statistics for the durations of the spans. A percentile is the value below
         |which a certain percent of observations fall. For example, the 20th percentile is the
         |value below which 20 percent of the observations may be found.
         |
         |Note that the time filter is not excactly the same as for other rolling or explicit
         |time filters. The percentiles are only availble for 1 hour time range or for all time.
         |Note that the explicit time is defined with one 'time' parameter.
         |
         |When the number of values exceeds a limit a uniform sample of the values
         |is used for the hour time range and the latest values are used when no 'rolling' or 'time'
         |parameter is specified.""".stripMargin)
    gen.writeLink("uri", PercentilesSpanStatsUri + "/{spanType}")
    gen.writeLink("spanTypeValues", MetadataStatsResource.SpanTypesUri)
    gen.writeLink("sample", PercentilesSpanStatsUri + "/message" + "?" + "rolling=1hour" + "&" + SampleActorPathParameter)
    gen.writeArrayFieldStart("filter")
    writePercentilesTimeFilter(gen)
    writeScopeFilters(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writePlayStats(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Play Statistics")
    gen.writeStringField("description", "Play statistics request statistics.")
    gen.writeLink("uri", PlayStatsUri + "/{spanType}")
    gen.writeLink("sample", PlayStatsUri + "?" + SampleRollingTimeParameter + "&" + SampleTagParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeScopeFilters(gen)
    writeChunksFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeSpanTimeSeries(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Span Time Series")
    gen.writeStringField("description",
      """|Time series for the durations of the spans. A time series point holds the timestamp,
         |duration and sampling factor. This information is typically used for producing time series
         |scatter plot of latencies.""".stripMargin)
    gen.writeLink("uri", SpanTimeSeriesUri + "/{spanType}")
    gen.writeLink("spanTypeValues", MetadataStatsResource.SpanTypesUri)
    gen.writeLink("sample", SpanTimeSeriesUri + "/message" + "?" + SampleRollingTimeParameter +
      "&" + SampleActorPathParameter + "&" + "sampling=10")
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeScopeFilters(gen)
    writeSpanSamplingFilter(gen)
    writeMaxPointsFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeActorStats(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Actor Statistics")
    gen.writeStringField("description", "Actor lifecycle events and message processing statistics.")
    gen.writeLink("uri", ActorStatsUri)
    gen.writeLink("sample", ActorStatsUri + "?" + SampleRollingTimeParameter + "&" + SampleTagParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeScopeFilters(gen)
    writeChunksFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMessageRateTimeSeries(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Message Rate Time Series")
    gen.writeStringField("description",
      """|Time series for message rate. A time series point holds the timestamp, and current messages
         |per second at a specific point in time, measured over a short time window.
         |This information is typically used for graphing values over time.""".stripMargin)
    gen.writeLink("uri", MessageRateTimeSeriesUri)
    gen.writeLink("sample", MessageRateTimeSeriesUri + "?" + SampleRollingTimeParameter +
      "&" + SampleActorPathParameter + "&" + "sampling=3")
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeScopeFilters(gen)
    writeSamplingFilter(gen)
    writeMaxPointsFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMessageRatePoint(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Message Rate Point")
    gen.writeStringField("description",
      """|A specific point in a time series of message rate points. The point holds the timestamp, and current messages
         |per second at a specific point in time, measured over a short time window.
         |If no time parameter is given the latest point in the time series will be shown.
         |Typically used for getting information of specific point in time.""".stripMargin)
    gen.writeLink("uri", MessageRatePointUri)
    gen.writeLink("sample", MessageRatePointUri)
    gen.writeArrayFieldStart("filter")
    writeScopeFilters(gen)
    writeSpecificTimeFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMailboxTimeSeries(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Mailbox Time Series")
    gen.writeStringField("description",
      """|Time series for mailbox size and message waiting time in mailbox.
         |A time series point holds the timestamp, and current messages
         |mailbox size and message waiting time in mailbox.
         |This information is typically used for graphing values over time.""".stripMargin)
    gen.writeLink("uri", MailboxTimeSeriesUri + "/{actorPath}")
    gen.writeLink("actorPathValues", MetadataStatsResource.PathsUri)
    gen.writeLink("sample", MailboxTimeSeriesUri + "/" + SampleActorPath +
      "?" + SampleRollingTimeParameter + "&" + "sampling=3")
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeSamplingFilter(gen)
    writeMaxPointsFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeMailboxPoint(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Mailbox Point")
    gen.writeStringField("description",
      """|A specific point in a time series of message rate points.
         |A time series point holds the timestamp, and current messages mailbox size and message waiting time in mailbox.
         |If no time parameter is given the latest point in the time series will be shown.
         |Typically used for getting information of specific point in time.""".stripMargin)
    gen.writeLink("uri", MailboxPointUri + "/{actorPath}")
    gen.writeLink("actorPathValues", MetadataStatsResource.PathsUri)
    gen.writeLink("sample", MailboxPointUri + "/" + SampleActorPath)
    gen.writeArrayFieldStart("filter")
    writeSpecificTimeFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeDispatcherTimeSeries(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Dispatcher Metrics Time Series")
    gen.writeStringField("description",
      """|Message dispatcher metrics, such as thread pool configuration and active thread count.
         |It's possible to retrieve time series for the dispatcher metrics. A time series point holds the timestamp,
         |and its metrics. This information is typically used for producing a time series graph.
         |The metrics information is collected periodically from the thread pools that are used by the dispatchers.""".stripMargin)
    gen.writeLink("uri", DispatcherTimeSeriesUri + "/{node}/{actorSystem}/{dispatcher}")
    gen.writeLink("nodeValues", MetadataStatsResource.NodesUri)
    gen.writeLink("actorSystemValues", MetadataStatsResource.ActorSystemsUri)
    gen.writeLink("dispatcherValues", MetadataStatsResource.DispatchersUri)
    gen.writeLink("sample", DispatcherTimeSeriesUri + "/" + SampleNode + "/" + SampleActorSystem + "/" + SampleDispatcher + "?" + SampleRollingTimeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeSamplingFilter(gen)
    writeMaxPointsFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeDispatcherPoint(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Dispatcher Metrics Point")
    gen.writeStringField("description",
      """|A specific point in a time series of dispatcher metrics points.
         |A time series point holds the timestamp and its metrics
         |Typically used for getting information of specific point in time.
         |The metrics information is collected periodically from the thread pools that are used by the dispatchers.""".stripMargin)
    gen.writeLink("uri", DispatcherPointUri + "/{node}/{actorSystem}/{dispatcher}")
    gen.writeLink("nodeValues", MetadataStatsResource.NodesUri)
    gen.writeLink("actorSystemValues", MetadataStatsResource.ActorSystemsUri)
    gen.writeLink("dispatcherValues", MetadataStatsResource.DispatchersUri)
    gen.writeLink("sample", DispatcherPointUri + "/" + SampleNode + "/" + SampleActorSystem + "/" + SampleDispatcher)
    gen.writeArrayFieldStart("filter")
    writeSpecificTimeFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeSystemMetricsTimeSeries(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "System Metrics Time Series")
    gen.writeStringField("description",
      """|JVM and OS health metrics. It's possible to retrieve time series for system metrics.
         |A time series point holds the timestamp, and its metrics. This information is typically
         |used for producing a time series graphs.
         |The metrics information is collected periodically from the nodes in the system.""".stripMargin)
    gen.writeLink("uri", SystemMetricsTimeSeriesUri + "/{node}")
    gen.writeLink("nodeValues", MetadataStatsResource.NodesUri)
    gen.writeLink("sample", SystemMetricsTimeSeriesUri + "/node1" + "?" + SampleRollingTimeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeSamplingFilter(gen)
    writeMaxPointsFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeSystemMetricsPoint(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "System Metrics Point")
    gen.writeStringField("description",
      """|A specific point in a time series of system metrics points.
         |A time series point holds the timestamp and its metrics information.
         |Typically used for getting information of specific point in time.
         |The metrics information is collected periodically from the nodes in the system.""".stripMargin)
    gen.writeLink("uri", SystemMetricsPointUri + "/{node}")
    gen.writeLink("nodeValues", MetadataStatsResource.NodesUri)
    gen.writeLink("sample", SystemMetricsPointUri + "/" + SampleNode)
    gen.writeArrayFieldStart("filter")
    writeSpecificTimeFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeErrorStats(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Error Statistics")
    gen.writeStringField("description",
      """|Number of errors, warnings, dead-letters, unhandled messages, and deadlocks in the whole system,
         |for a specific node, or for an ActorSystem.
         |Links to the most recent trace trees of the errors.""".stripMargin)
    gen.writeLink("uri", ErrorStatsUri)
    gen.writeLink("sample", ErrorStatsUri + "?" + SampleRollingTimeParameter + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    writeChunksFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeRecordStats(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Record Statistics")
    gen.writeStringField("description",
      """|Statitics for "records", i.e. all logged messsages with the Trace.record(name, data) API method.
        |The name gets a counter and also each combination of name + data has a counter.
        |The name serves as a group for underlying name + data combinations.""".stripMargin)
    gen.writeLink("uri", RecordStatsUri)
    gen.writeLink("sample", RecordStatsUri + "?" + SampleRollingTimeParameter + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    writeChunksFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeRemoteStatus(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Remote Status")
    gen.writeStringField("description",
      """|Remote status events. Counts of lifecycle events from RemoteSupport.
         |The counts correspond to the types of RemoteServerLifeCycleEvent and RemoteClientLifeCycleEvent, i.e.
         |- remoteServerError
         |- remoteServerStarted
         |- remoteServerShutdown
         |- remoteServerClientConnected
         |- remoteServerClientDisconnected
         |- remoteServerClientClosed
         |- remoteClientError
         |- remoteClientConnected
         |- remoteClientDisconnected
         |- remoteClientStarted
         |- remoteClientShutdown
         |- remoteClientWriteFailed""".stripMargin)
    gen.writeLink("uri", RemoteStatusStatsUri)
    gen.writeLink("sample", RemoteStatusStatsUri + "?" + SampleRollingTimeParameter + "&" + SampleNodeParameter)
    gen.writeArrayFieldStart("filter")
    writeTimeFilters(gen)
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    writeChunksFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeTraceEvents(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Trace Events")
    gen.writeStringField("description",
      """|Should you like to retrieve information about the trace events in the system we suggest that you start to
         |retrieve information with one of the interval search methods described here. From the result of these
         |methods it is easy to drill down into the specific information about a trace event or a trace tree,
         |by following the links.
         |
         |In the end of the response paging information is part of the response from the server.
         |With this information it is possible to determine if there is more data to retrieve.
         |This is indicated by the field 'nextPosition', and the ready to use 'next' link.
         |Should there be such a field it is almost certain that there is
         |more data available. In rare cases there will be no additional information and in such cases the
         |result returned from the server will be an empty array of 'traceEvents'.""".stripMargin)
    gen.writeLink("uri", TraceEventResource.TraceEventsUri)
    gen.writeLink("sample", TraceEventResource.TraceEventsUri + "?" + SampleRollingTimeParameter)
    gen.writeArrayFieldStart("filter")
    writeRollingFilter(gen)
    writeExactTimeRangeFilter(gen)
    writePagingFilter(gen)
    writeLinksFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeSpans(gen: JsonGenerator): Unit = {
    gen.writeStartObject()
    gen.writeStringField("name", "Spans")
    gen.writeStringField("description",
      """|Should you like to retrieve information about the spans in the system we suggest that you start to
         |retrieve information with one of the interval search methods described here. From the result of these
         |methods it is easy to drill down into the specific information about a span, trace event or a trace tree,
         |by following the links.
         |
         |In the end of the response paging information is part of the response from the server.
         |With this information it is possible to determine if there is more data to retrieve.
         |This is indicated by the field 'nextPosition', and the ready to use 'next' link.
         |Should there be such a field it is almost certain that there is
         |more data available. In rare cases there will be no additional information and in such cases the
         |result returned from the server will be an empty array of 'spans'.""".stripMargin)
    gen.writeLink("uri", TraceEventResource.SpansUri)
    gen.writeLink("sample", TraceEventResource.SpansUri + "?" + SampleRollingTimeParameter)
    gen.writeArrayFieldStart("filter")
    writeRollingFilter(gen)
    writeExactTimeRangeFilter(gen)
    writeFormatTimestampsFilter(gen)
    gen.writeEndArray()
    gen.writeEndObject()
  }

  def writeTimeFilters(gen: JsonGenerator) {
    writeRollingFilter(gen)
    writeTimeRangeFilter(gen)
  }

  def writeSpecificTimeFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "time")
    gen.writeStringField("format", "yyyy-MM-ddTHH:mm:ss.SSS")
    gen.writeStringField("sample", "time=2011-09-13T11:43:38.999")
    gen.writeEndObject()
  }

  def writeRollingFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "rolling")
    gen.writeStringField("format", "##{timeRangeType}, ## can be any number from 1-99 and both 'hour' and 'hours' are accepted as qualifier.")
    writeTimeRangeValues(gen)
    gen.writeStringField("sample", "rolling=4hours")
    gen.writeEndObject()
  }

  def writeTimeRangeFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "startTime")
    gen.writeStringField("description", "Use together with endTime and rangeType")
    gen.writeStringField("format", "yyyy-MM, yyyy-MM-dd, yyyy-MM-ddTHH or yyyy-MM-ddTHH:mm")
    gen.writeStringField("sample", "startTime=2011-06-11T09:43")
    gen.writeEndObject()
    gen.writeStartObject()
    gen.writeStringField("parameter", "endTime")
    gen.writeStringField("description", "Use together with startTime and rangeType")
    gen.writeStringField("format", "yyyy-MM, yyyy-MM-dd, yyyy-MM-ddTHH or yyyy-MM-ddTHH:mm")
    gen.writeStringField("sample", "endTime=2011-06-11T17:59")
    gen.writeEndObject()
    gen.writeStartObject()
    gen.writeStringField("parameter", "rangeType")
    gen.writeStringField("description", "Use together with startTime and endTime")
    writeTimeRangeValues(gen)
    gen.writeStringField("sample", "rangeType=hours")
    gen.writeEndObject()
  }

  def writeExactTimeRangeFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "startTime")
    gen.writeStringField("description", "Use together with endTime")
    gen.writeStringField("format", "yyyy-MM-ddTHH:mm:ss:SSS")
    gen.writeStringField("sample", "startTime=2011-06-11T09:43:21:123")
    gen.writeEndObject()
    gen.writeStartObject()
    gen.writeStringField("parameter", "endTime")
    gen.writeStringField("description", "Use together with startTime")
    gen.writeStringField("format", "yyyy-MM-ddTHH:mm:ss:SSS")
    gen.writeStringField("sample", "endTime=2011-06-11T17:59:59:999")
    gen.writeEndObject()
  }

  def writeTimeRangeValues(gen: JsonGenerator) {
    gen.writeArrayFieldStart("values")
    gen.writeString("minutes")
    gen.writeString("hours")
    gen.writeString("days")
    gen.writeString("months")
    gen.writeEndArray()
  }

  def writeScopeFilters(gen: JsonGenerator) {
    writeNodeFilter(gen)
    writeActorSystemFilter(gen)
    writeDispatcherFilter(gen)
    writeTagFilter(gen)
    writeActorPathFilter(gen)
    writePlayPathPatternFilter(gen)
    writePlayControllerFilter(gen)
  }

  def writeNodeFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "node")
    gen.writeLink("values", MetadataStatsResource.NodesUri)
    gen.writeStringField("sample", "node=backend1")
    gen.writeEndObject()
  }

  def writeActorSystemFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "actorSystem")
    gen.writeLink("values", MetadataStatsResource.ActorSystemsUri)
    gen.writeStringField("sample", "actorSystem=MySystem")
    gen.writeEndObject()
  }

  def writeActorPathFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "actorPath")
    gen.writeLink("values", MetadataStatsResource.PathsUri)
    gen.writeStringField("sample", "actorPath=akka://Demo/user/ActorA")
    gen.writeEndObject()
  }

  def writePlayPathPatternFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "playPathPattern")
    gen.writeLink("values", MetadataStatsResource.PlayPatternsUri)
    gen.writeStringField("sample", "/sample/$id<[^/]+>")
    gen.writeEndObject()
  }

  def writePlayControllerFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "playController")
    gen.writeLink("values", MetadataStatsResource.PlayControllersUri)
    gen.writeStringField("sample", "controllers.Application.index")
    gen.writeEndObject()
  }

  def writeDispatcherFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "dispatcher")
    gen.writeLink("values", MetadataStatsResource.DispatchersUri)
    gen.writeStringField("sample", "dispatcher=akka.actor.default-dispatcher")
    gen.writeEndObject()
  }

  def writeTagFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "tag")
    gen.writeLink("values", MetadataStatsResource.TagsUri)
    gen.writeStringField("sample", "tag=demo")
    gen.writeEndObject()
  }

  def writeBucketBoundariesFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "bucketBoundaries")
    gen.writeStringField("description", "Defines the size of the buckets in microseconds of the histogram statistics.")
    gen.writeStringField("format", "comma separated values '100, 200, 300', or width times number of buckets " +
      "'100x3', or a mix of both '20x4, 100, 200, 300, 700, 1000x4'")
    gen.writeStringField("sample", "bucketBoundaries=100,200,300")
    gen.writeEndObject()
  }

  def writePercentilesTimeFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "rolling")
    gen.writeStringField("format", "1hour")
    gen.writeStringField("sample", "rolling=1hour")
    gen.writeEndObject()

    gen.writeStartObject()
    gen.writeStringField("parameter", "time")
    gen.writeStringField("description", "Use together with rangeType")
    gen.writeStringField("format", "yyyy-MM-ddTHH")
    gen.writeStringField("sample", "time=2011-06-11T17")
    gen.writeEndObject()
    gen.writeStartObject()
    gen.writeStringField("parameter", "rangeType")
    gen.writeStringField("description", "Use together with time")
    gen.writeArrayFieldStart("values")
    gen.writeString("hours")
    gen.writeEndArray()
    gen.writeStringField("sample", "rangeType=hours")
    gen.writeEndObject()
  }

  def writeSamplingFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "sampling")
    gen.writeStringField("description",
      """|The frequency is approximately one point per second, but it may be less when there are no events.
         |'sampling' query parameter can be used reduce the amount of data. For example 'sampling=60'
         |will filter the result to approximately one point per minute.""".stripMargin)
    gen.writeStringField("format", "##")
    gen.writeStringField("sample", "sampling=10")
    gen.writeEndObject()
  }

  def writeSpanSamplingFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "sampling")
    gen.writeStringField("description",
      """|The trace events may be sampled when collected. Additional sampling can be used in this query to
         |reduce the amount of data. Additional sampling is specified with 'sampling' query parameter.""".stripMargin)
    gen.writeStringField("format", "##")
    gen.writeStringField("sample", "sampling=10")
    gen.writeEndObject()
  }

  def writeMaxPointsFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "maxPoints")
    gen.writeStringField("description",
      """|Reduce number of time series points with automatically adjusted sampling if number of points exceeds
         |the specified 'maxPoints' query parameter.""".stripMargin)
    gen.writeStringField("format", "##")
    gen.writeStringField("sample", "maxPoints=300")
    gen.writeEndObject()
  }

  def writeChunksFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "minChunks")
    gen.writeStringField("description",
      """|Partition the requested time period into a number of equal size groups. This parameter defines
         |the minimum accepted number of chunks and together with maxChunks limit the best number of chunks
         |is selected. Used together with maxChunks.""".stripMargin)
    gen.writeStringField("format", "##")
    gen.writeStringField("sample", "minChunks=5")
    gen.writeEndObject()
    gen.writeStartObject()
    gen.writeStringField("parameter", "maxChunks")
    gen.writeStringField("description",
      """|Partition the requested time period into a number of equal size groups. This parameter defines
         |the maximum accepted number of chunks and together with minChunks limit the best number of chunks
         |is selected. Used together with maxChunks.""".stripMargin)
    gen.writeStringField("format", "##")
    gen.writeStringField("sample", "maxChunks=15")
    gen.writeEndObject()
  }

  def writePagingFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "offset")
    gen.writeStringField("description", "Use together with limit")
    gen.writeStringField("format", "a positive integer >= 1 (defaults to 1)")
    gen.writeStringField("sample", "offset=2")
    gen.writeEndObject()
    gen.writeStartObject()
    gen.writeStringField("parameter", "limit")
    gen.writeStringField("description", "Use together with offset")
    gen.writeStringField("format", "a positive integer <= 100 (defaults to 100)")
    gen.writeStringField("sample", "limit=50")
    gen.writeEndObject()
  }

  def writeLinksFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "links")
    gen.writeStringField("description", "Turn off links by adding query parameter links=off. Only ids are returned.")
    gen.writeArrayFieldStart("values")
    gen.writeString("off")
    gen.writeString("on")
    gen.writeEndArray()
    gen.writeStringField("sample", "links=off")
    gen.writeEndObject()
  }

  def writeFormatTimestampsFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "formatTimestamps")
    gen.writeStringField("description",
      """|Turn off formatting of timestamps by adding query parameter formatTimestamps=off.
           |Unformatted value is milliseconds since midnight, January 1, 1970 UTC""".stripMargin)
    gen.writeArrayFieldStart("values")
    gen.writeString("off")
    gen.writeString("on")
    gen.writeEndArray()
    gen.writeStringField("sample", "formatTimestamps=off")
    gen.writeEndObject()
  }

  def writeWidthFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "width")
    gen.writeStringField("description", "Image width in pixels.")
    gen.writeStringField("format", "###")
    gen.writeStringField("sample", "width=900")
    gen.writeEndObject()
  }

  def writeHeigthFilter(gen: JsonGenerator) {
    gen.writeStartObject()
    gen.writeStringField("parameter", "height")
    gen.writeStringField("description", "Image height in pixels.")
    gen.writeStringField("format", "###")
    gen.writeStringField("sample", "height=300")
    gen.writeEndObject()
  }

}

object RootJsonRepresentation {
  final val SampleRollingTimeParameter = "rolling=5minutes"
  final val SampleRollingTimeParameter2 = "rolling=2hours"
  final val SampleNode = "node1"
  final val SampleActorSystem = "Demo"
  final val SampleNodeParameter = "node=" + SampleNode
  final val SampleDispatcher = "akka.actor.default-dispatcher"
  final val SampleActorPath = "akka://Demo/user/ActorB"
  final val SampleActorPathParameter = "actorPath=" + SampleActorPath
  final val SampleTagParameter = "tag=demo"
}

