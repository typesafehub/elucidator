/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes._
import activator.analytics.data.SpanType.PredefinedSpanTypesNamePrefix
import com.typesafe.trace.uuid.UUID
import scala.concurrent.duration._
import TimeRangeType.AllTime

case class Span(
  id: UUID,
  trace: UUID,
  spanTypeName: String,
  startTime: Long,
  duration: Duration,
  sampled: Int = 1,
  remote: Boolean = false,
  startEvent: UUID,
  endEvent: UUID) {

  def formattedSpanTypeName: String = SpanType.formatSpanTypeName(spanTypeName)
}

object SpanType {
  final val PredefinedSpanTypesNamePrefix = "_"

  def forName(name: String): Option[SpanType] = name.toLowerCase match {
    case MessageSpan.name  ⇒ Some(MessageSpan)
    case MailboxSpan.name  ⇒ Some(MailboxSpan)
    case ReceiveSpan.name  ⇒ Some(ReceiveSpan)
    case QuestionSpan.name ⇒ Some(QuestionSpan)
    case RemoteSpan.name   ⇒ Some(RemoteSpan)
    case other if !other.startsWith(PredefinedSpanTypesNamePrefix) ⇒
      Some(MarkerSpan)
    case other ⇒ None
  }

  /**
   * Removes the internal "_" prefix for predefined span types.
   */
  def formatSpanTypeName(value: String): String = {
    if (value.startsWith("_")) value.drop(1) else value
  }

  def allSpanTypes = Seq(MessageSpan, MailboxSpan, ReceiveSpan, QuestionSpan, RemoteSpan, MarkerSpan)

}

sealed trait SpanType {
  def name: String
  def formattedName: String = SpanType.formatSpanTypeName(name)
}
case object MessageSpan extends SpanType {
  override val name = PredefinedSpanTypesNamePrefix + "message"
}
case object MailboxSpan extends SpanType {
  override val name = PredefinedSpanTypesNamePrefix + "mailbox"
}
case object ReceiveSpan extends SpanType {
  override val name = PredefinedSpanTypesNamePrefix + "receive"
}
case object QuestionSpan extends SpanType {
  override val name = PredefinedSpanTypesNamePrefix + "question"
}
case object RemoteSpan extends SpanType {
  override val name = PredefinedSpanTypesNamePrefix + "remote"
}
case object MarkerSpan extends SpanType {
  override val name = PredefinedSpanTypesNamePrefix + "marker"
}

case class Spans(of: Seq[Span])
