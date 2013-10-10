/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import com.typesafe.trace._
import com.typesafe.trace.uuid.UUID
import scala.language.implicitConversions
import scala.util.control.Exception._
import activator.analytics.data.{ RequestSummaryType, PlayRequestSummary }

class PlayTraceTreeHelpers(tree: Seq[TraceEvent]) {
  private final def traceFinder(test: Annotation ⇒ Boolean): Option[TraceEvent] = tree.find(e ⇒ test(e.annotation))
  private final def allTracesFinder(test: Annotation ⇒ Boolean): Seq[TraceEvent] = tree.filter(e ⇒ test(e.annotation))
  private final def annotationFinder[T <: Annotation](test: Annotation ⇒ Boolean): Option[T] = traceFinder(test).map(_.annotation.asInstanceOf[T])
  private final def allAnnotationFinder[T <: Annotation](test: Annotation ⇒ Boolean): Seq[T] = allTracesFinder(test).map(_.annotation.asInstanceOf[T])
  private def invocation: Option[ActionInvoked] = annotationFinder[ActionInvoked](_.isInstanceOf[ActionInvoked])
  lazy val traceOfActionError: Option[TraceEvent] = traceFinder(_.isInstanceOf[ActionError])
  lazy val actionError: Option[ActionError] = traceOfActionError.map(_.annotation.asInstanceOf[ActionError])
  lazy val traceOfActionHandlerNotFound: Option[TraceEvent] = traceFinder(_.isInstanceOf[ActionHandlerNotFound])
  lazy val actionHandlerNotFound: Option[ActionHandlerNotFound] = traceOfActionHandlerNotFound.map(_.annotation.asInstanceOf[ActionHandlerNotFound])
  lazy val traceOfActionBadRequest: Option[TraceEvent] = traceFinder(_.isInstanceOf[ActionBadRequest])
  lazy val actionBadRequest: Option[ActionBadRequest] = traceOfActionBadRequest.map(_.annotation.asInstanceOf[ActionBadRequest])
  lazy val traceOfActionRouteRequest: Option[TraceEvent] = traceFinder(_.isInstanceOf[ActionRouteRequest])
  lazy val actionRouteRequest: Option[ActionRouteRequest] = traceOfActionRouteRequest.map(_.annotation.asInstanceOf[ActionRouteRequest])
  lazy val requestInfo: Option[ActionRequestInfo] = actionRouteRequest.map(_.requestInfo)
  lazy val summaryType: RequestSummaryType =
    if (traceOfActionError.isDefined) RequestSummaryType.Exception
    else if (traceOfActionHandlerNotFound.isDefined) RequestSummaryType.HandlerNotFound
    else if (traceOfActionBadRequest.isDefined) RequestSummaryType.BadRequest
    else RequestSummaryType.Normal

  lazy val errorMessage: Option[String] = summaryType match {
    case RequestSummaryType.Normal          ⇒ None
    case RequestSummaryType.Exception       ⇒ actionError.map(_.message)
    case RequestSummaryType.HandlerNotFound ⇒ None
    case RequestSummaryType.BadRequest      ⇒ actionBadRequest.map(_.error)
  }

  lazy val stackTrace: Option[Seq[String]] = actionError.map(_.stackTrace)

  lazy val traceOfEarliestEventOption: Option[TraceEvent] = allCatch[TraceEvent].opt(tree.minBy(_.nanoTime))
  lazy val traceIdOption: Option[UUID] = traceOfEarliestEventOption.map(_.trace)
  lazy val traceId: UUID = traceIdOption.get
  lazy val traceOfEarliestEvent: TraceEvent = traceOfEarliestEventOption.get
  lazy val traceOfActionResultGenerationStart: Option[TraceEvent] = traceFinder(_.isInstanceOf[ActionResultGenerationStart.type])
  lazy val traceOfNettyHttpReceivedStart: Option[TraceEvent] = traceFinder(_.isInstanceOf[NettyHttpReceivedStart.type])
  lazy val traceOfActionResultGenerationEnd: Option[TraceEvent] = traceFinder(_.isInstanceOf[ActionResultGenerationEnd.type])
  lazy val traceOfActionResponseAnnotation: Option[TraceEvent] = traceFinder(_.isInstanceOf[ActionResponseAnnotation])
  lazy val tracesForActionAsyncResult: Seq[TraceEvent] = allTracesFinder(_.isInstanceOf[ActionAsyncResult.type])
  lazy val tracesForNettyResponseBody: Seq[TraceEvent] = allTracesFinder(_.isInstanceOf[NettyResponseBody])
  lazy val tracesForNettyWriteChunk: Seq[TraceEvent] = allTracesFinder(_.isInstanceOf[NettyWriteChunk])
  lazy val lastTraceOfNettyResponseBody: Option[TraceEvent] = allCatch[TraceEvent].opt(tracesForNettyResponseBody.maxBy(_.nanoTime))
  lazy val lastTraceOfNettyWriteChunk: Option[TraceEvent] = allCatch[TraceEvent].opt(tracesForNettyWriteChunk.maxBy(_.nanoTime))
  lazy val lastTraceOfBytesOut: Option[TraceEvent] = lastTraceOfNettyResponseBody orElse lastTraceOfNettyWriteChunk
  lazy val bytesReceived: Option[Long] = allAnnotationFinder[NettyReadBytes](_.isInstanceOf[NettyReadBytes]).map(_.size).foldLeft[Option[Long]](Some(0)) { // strictly speaking Some(0) is wrong
    case (None, v)    ⇒ Some(v)
    case (Some(s), v) ⇒ Some(s + v)
  }
  lazy val bytesSent: Option[Long] = allTracesFinder(_ match {
    case _: NettyResponseHeader ⇒ true
    case _: NettyResponseBody   ⇒ true
    case _: NettyWriteChunk     ⇒ true
    case _                      ⇒ false
  }).view.map(_.annotation).map({
    (_: Annotation @unchecked) match {
      case NettyResponseHeader(s) ⇒ s
      case NettyResponseBody(s)   ⇒ s
      case NettyWriteChunk(o, s)  ⇒ o + s
    }
  }).foldLeft[Option[Long]](None)({
    case (None, v)    ⇒ Some(v)
    case (Some(s), v) ⇒ Some(s + v)
  })
  lazy val invocationInfo: Option[ActionInvocationInfo] = invocation.map(_.invocationInfo)
  lazy val result: Option[ActionResponseAnnotation] = traceOfActionResponseAnnotation.map(_.annotation.asInstanceOf[ActionResponseAnnotation])
  lazy val simpleResult: Option[ActionSimpleResult] = annotationFinder[ActionSimpleResult](_.isInstanceOf[ActionSimpleResult])
  lazy val chunkedResult: Option[ActionChunkedResult] = annotationFinder[ActionChunkedResult](_.isInstanceOf[ActionChunkedResult])
  lazy val asyncResult: Option[ActionAsyncResult.type] = annotationFinder[ActionAsyncResult.type](_ == ActionAsyncResult)
  lazy val httpResponseCode: Option[Int] = result.map(_.resultInfo.httpResponseCode)
}

object PlayTraceTreeHelpers {
  implicit def toHelpers(tree: Seq[TraceEvent]): PlayTraceTreeHelpers = new PlayTraceTreeHelpers(tree)
}
