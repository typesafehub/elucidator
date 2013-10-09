/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ Props, ActorLogging, Actor }
import spray.can.Http
import spray.http._
import spray.http.HttpResponse
import com.typesafe.atmos.trace.TraceEvent
import activator.analytics.AnalyticsExtension

case class JRequest(req: HttpRequest)
case class ResultPayload(json: String, headers: List[HttpHeader])

class GatewayActor extends Actor with ActorLogging {
  import GatewayActor._
  val settings = AnalyticsExtension(context.system)
  val repository = new LocalMemoryRepository(context.system)
  val rootResource = context.actorOf(Props[RootResource], "rootResource")
  val dashboardResource = context.actorOf(Props[DashboardResource], "dashboardResource")
  val actorStatsResource = context.actorOf(Props(new ActorStatsResource(repository.actorStatsRepository)), "actorStatsResource")
  val actorStatsSortedResource = context.actorOf(Props(new ActorStatsSortedResource(repository.actorStatsRepository)), "actorStatsSortedResource")
  val dispatcherPointResource = context.actorOf(Props(new DispatcherPointResource(repository.dispatcherTimeSeriesRepository)), "dispatcherPointResource")
  val dispatcherTimeSeriesResource = context.actorOf(Props(new DispatcherTimeSeriesResource(repository.dispatcherTimeSeriesRepository)), "dispatcherTimeSeriesResource")
  val errorStatsResource = context.actorOf(Props(new ErrorStatsResource(repository.errorStatsRepository)), "errorResource")
  val histogramSpanStatsResource = context.actorOf(Props(new HistogramSpanStatsResource(repository.histogramSpanStatsRepository)), "histogramSpanStatsResource")
  val mailboxPointResource = context.actorOf(Props(new MailboxPointResource(repository.mailboxTimeSeriesRepository)), "mailboxPointResource")
  val mailboxTimeSeriesResource = context.actorOf(Props(new MailboxTimeSeriesResource(repository.mailboxTimeSeriesRepository)), "mailboxTimeSeriesResource")
  val messageRatePointResource = context.actorOf(Props(new MessageRatePointResource(repository.messageRateTimeSeriesRepository)), "messageRatePointResource")
  val messageRateTimeSeriesResource = context.actorOf(Props(new MessageRateTimeSeriesResource(repository.messageRateTimeSeriesRepository)), "messageRateTimeSeriesResource")
  val metadataStatsResource = context.actorOf(Props(new MetadataStatsResource(repository.metadataStatsRepository, repository.summarySpanStatsRepository, repository.errorStatsRepository, repository.actorStatsRepository)), "metadataStatsResource")
  val percentilesSpanStatsResource = context.actorOf(Props(new PercentilesSpanStatsResource(repository.percentilesSpanStatsRepository)), "percentilesSpanStatResource")
  val playStatsResource = context.actorOf(Props(new PlayStatsResource(repository.playStatsRepository)), "playStatsResource")
  val playRequestSummaryResource = context.actorOf(Props(new PlayRequestSummaryResource(repository.playRequestSummaryRepository, repository.traceRepository)), "playRequestSummaryResource")
  val recordStatsResource = context.actorOf(Props(new RecordStatsResource(repository.recordStatsRepository)), "recordStatsResource")
  val remoteStatusStatsResource = context.actorOf(Props(new RemoteStatusStatsResource(repository.remoteStatusStatsRepository)), "remoteStatusStatsResource")
  val spanTimeSeriesResource = context.actorOf(Props(new SpanTimeSeriesResource(repository.spanTimeSeriesRepository)), "spanTimeSeriesResource")
  val summarySpanStatsResource = context.actorOf(Props(new SummarySpanStatsResource(repository.summarySpanStatsRepository)), "summarySpanStatsResource")
  val systemMetricsPointResource = context.actorOf(Props(new SystemMetricsPointResource(repository.systemMetricsTimeSeriesRepository)), "systemMetricsPointResource")
  val systemMetricsTimeSeriesResource = context.actorOf(Props(new SystemMetricsTimeSeriesResource(repository.systemMetricsTimeSeriesRepository)), "systemMetricsTimeSeriesResource")
  val traceEventResource = context.actorOf(Props(new TraceEventResource(repository.traceRepository, repository.spanRepository)), "traceEventResource")

  def receive = {
    case _: Http.Connected ⇒ sender ! Http.Register(self)
    case r @ HttpRequest(HttpMethods.GET, _, _, _, _) if (r.uri.path.toString != "/favicon.ico") ⇒ handleRequest(r)
    case x ⇒ log.debug("Unhandled message: %s".format(x))
  }

  def handleRequest(req: HttpRequest) = {
    val uri = req.uri.path.toString
    val actor = if (uri == MonitoringQualifierUri || uri == RootUri || uri == ApiUri) Some(rootResource)
    else if (uri.startsWith(SummarySpanStatsUri)) Some(summarySpanStatsResource)
    else if (uri.startsWith(StaticUri)) Some(dashboardResource)
    else if (uri.startsWith(ErrorStatsUri)) Some(errorStatsResource)
    else if (uri.startsWith(HistogramSpanStatsUri)) Some(histogramSpanStatsResource)
    else if (uri.startsWith(PercentilesSpanStatsUri)) Some(percentilesSpanStatsResource)
    else if (uri.startsWith(SpanTimeSeriesUri)) Some(spanTimeSeriesResource)
    else if (uri.startsWith(MailboxTimeSeriesUri)) Some(mailboxTimeSeriesResource)
    else if (uri.startsWith(MailboxPointUri)) Some(mailboxPointResource)
    else if (uri.startsWith(MessageRateTimeSeriesUri)) Some(messageRateTimeSeriesResource)
    else if (uri.startsWith(MessageRatePointUri)) Some(messageRatePointResource)
    else if (uri.startsWith(ActorStatsSortedUri)) Some(actorStatsSortedResource)
    else if (uri.startsWith(ActorStatsUri)) Some(actorStatsResource)
    else if (uri.startsWith(MetadataStatsUri)) Some(metadataStatsResource)
    else if (uri.startsWith(TraceEventUri)) Some(traceEventResource)
    else if (uri.startsWith(RemoteStatusStatsUri)) Some(remoteStatusStatsResource)
    else if (uri.startsWith(MailboxTimeSeriesUri)) Some(mailboxTimeSeriesResource)
    else if (uri.startsWith(MailboxPointUri)) Some(mailboxPointResource)
    else if (uri.startsWith(DispatcherTimeSeriesUri)) Some(dispatcherTimeSeriesResource)
    else if (uri.startsWith(DispatcherPointUri)) Some(dispatcherPointResource)
    else if (uri.startsWith(SystemMetricsTimeSeriesUri)) Some(systemMetricsTimeSeriesResource)
    else if (uri.startsWith(SystemMetricsPointUri)) Some(systemMetricsPointResource)
    else if (uri.startsWith(RecordStatsUri)) Some(recordStatsResource)
    else if (uri.startsWith(PlayRequestSummaryUri)) Some(playRequestSummaryResource)
    else if (uri.startsWith(PlayStatsUri)) Some(playStatsResource)
    else {
      sender ! HttpResponse(status = StatusCodes.BadRequest, entity = "Unknown URI: " + uri)
      None
    }

    for (a ← actor) a.forward(JRequest(req))
  }

  private def getParam(request: HttpRequest, name: String): Option[String] = {
    val valueOption = request.uri.query.get(name).map(_.mkString(""))
    // empty string same as not present
    valueOption flatMap { s ⇒
      if (s.nonEmpty)
        Some(s)
      else
        None
    }
  }
}

object GatewayActor {
  /**
   * Used in various regular expressions
   */
  object PatternChars {
    final val SpanTypeChars = """[\w\-]+"""
    final val NodeChars = "[" + TraceEvent.NodeChars + "]+"
    final val ActorSystemChars = """[\w\:\-\.]+"""
    final val PlayControllerChars = """[\w\:\-\.]+"""
    final val PlayPatternChars = """[/\w\:\-\.$<>+_\\]+"""
    final val DispatcherChars = """[\w\:\-\.]+"""
    final val TagChars = """[\w\-\.]+"""
    // actor path may actually contain & also, but that ruins match of query parameter separator char
    // Need to do proper URL encoding escaping - JMP
    final val PathChars = """[\w\-\:\.\*$/@=+,!~_']+"""
  }

  final val MonitoringQualifierUri = "/monitoring"
  final val RootUri = MonitoringQualifierUri + "/"
  final val ApiUri = RootUri + "api.html"
  final val StaticUri = "/static"
  final val JQueryUri = StaticUri + "/jquery/jquery.min.js"
  final val LicenseUri = MonitoringQualifierUri + "/license"

  final val ActorStatsSortedUri = MonitoringQualifierUri + "/actors"
  final val ActorStatsUri = MonitoringQualifierUri + "/actor"
  final val DispatcherPointUri = MonitoringQualifierUri + "/dispatcher/point"
  final val DispatcherTimeSeriesUri = MonitoringQualifierUri + "/dispatcher/timeseries"
  final val ErrorStatsUri = MonitoringQualifierUri + "/error"
  final val HistogramSpanStatsUri = MonitoringQualifierUri + "/span/histogram"
  final val MailboxPointUri = MonitoringQualifierUri + "/actor/mailbox/point"
  final val MailboxTimeSeriesUri = MonitoringQualifierUri + "/actor/mailbox/timeseries"
  final val MessageRatePointUri = MonitoringQualifierUri + "/actor/rate/point"
  final val MessageRateTimeSeriesUri = MonitoringQualifierUri + "/actor/rate/timeseries"
  final val MetadataStatsUri = MonitoringQualifierUri + "/metadata"
  final val PercentilesSpanStatsUri = MonitoringQualifierUri + "/span/percentiles"
  final val PlayRequestSummaryUri = MonitoringQualifierUri + "/playrequestsummary"
  final val PlayStatsUri = MonitoringQualifierUri + "/play"
  final val RecordStatsUri = MonitoringQualifierUri + "/record"
  final val RemoteStatusStatsUri = MonitoringQualifierUri + "/remote/status"
  final val SpanTimeSeriesUri = MonitoringQualifierUri + "/span/timeseries"
  final val SummarySpanStatsUri = MonitoringQualifierUri + "/span/summary"
  final val SupervisorStatsPointUri = MonitoringQualifierUri + "/supervisor/point"
  final val SystemMetricsPointUri = MonitoringQualifierUri + "/systemmetrics/point"
  final val SystemMetricsTimeSeriesUri = MonitoringQualifierUri + "/systemmetrics/timeseries"
  final val TraceEventUri = MonitoringQualifierUri + "/trace"

  def baseUrl(req: HttpRequest): Option[String] = {
    val useLinks = req.uri.query.get("links")
    if (useLinks.isDefined && useLinks.get.nonEmpty && (useLinks.get == "off" || useLinks.get == "false")) {
      None
    } else {
      try {
        val url = req.uri.path.toString
        Some(url.take(url.indexOf(MonitoringQualifierUri)))
      } catch {
        // might happen when request has timed out
        case e: Exception ⇒ None
      }
    }
  }

  def formatTimestamps(req: HttpRequest): Boolean = {
    !req.uri.query.toString.contains("formatTimestamps=off") && !req.uri.query.toString.contains("formatTimestamps=false")
  }
}
