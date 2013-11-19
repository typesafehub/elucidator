/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import com.typesafe.trace.{ ActionInvocationInfo, ActionResponseAnnotation, ActionRequestInfo }
import com.typesafe.trace.uuid.UUID
import activator.analytics.data.BasicTypes._

sealed trait RequestSummaryType {
  def toString: String
}

object RequestSummaryType {
  def fromStringOption(in: String): Option[RequestSummaryType] = in.trim.toLowerCase match {
    case "normal"          ⇒ Some(Normal)
    case "exception"       ⇒ Some(Exception)
    case "handlernotfound" ⇒ Some(HandlerNotFound)
    case "badrequest"      ⇒ Some(BadRequest)
    case _                 ⇒ None
  }

  def fromString(in: String): RequestSummaryType = fromStringOption(in).get

  case object Normal extends RequestSummaryType {
    override def toString: String = "Normal"
  }
  case object Exception extends RequestSummaryType {
    override def toString: String = "Exception"
  }
  case object HandlerNotFound extends RequestSummaryType {
    override def toString: String = "HandlerNotFound"
  }
  case object BadRequest extends RequestSummaryType {
    override def toString: String = "BadRequest"
  }
}

case class TimeMark(millis: Timestamp, nanoTime: NanoTime)

case class PlayRequestSummary(traceId: UUID,
                              node: String,
                              host: String,
                              summaryType: RequestSummaryType,
                              requestInfo: ActionRequestInfo,
                              start: TimeMark,
                              end: TimeMark,
                              duration: DurationNanos,
                              invocationInfo: ActionInvocationInfo,
                              response: ActionResponseAnnotation,
                              asyncResponseNanoTimes: Seq[NanoTime],
                              inputProcessingDuration: DurationNanos,
                              actionExecution: TimeMark,
                              actionExecutionDuration: DurationNanos,
                              outputProcessing: TimeMark,
                              outputProcessingDuration: DurationNanos,
                              bytesIn: Long,
                              bytesOut: Long,
                              errorMessage: Option[String],
                              stackTrace: Option[Seq[String]])
