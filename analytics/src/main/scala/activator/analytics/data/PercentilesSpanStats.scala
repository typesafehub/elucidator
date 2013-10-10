/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import akka.actor.ActorSystem
import activator.analytics.data.BasicTypes.DurationNanos
import com.typesafe.trace.uuid.UUID
import activator.analytics.AnalyticsExtension

/**
 * Percentile statistics over the time period
 * for the durations of the spans that belong to the scope and
 * span type.
 */
case class PercentilesSpanStats(
  val timeRange: TimeRange,
  val scope: Scope,
  val spanTypeName: String,
  val n: Long,
  val percentiles: Map[String, DurationNanos],
  val id: UUID) extends BasicStats

object PercentilesSpanStats {

  def defaultMap(implicit system: ActorSystem): Map[String, DurationNanos] = {
    Map() ++ (for (p ← (AnalyticsExtension(system).Percentiles.toSeq)) yield (p, 0L))
  }

  def apply(timeRange: TimeRange, scope: Scope, spanTypeName: String, n: Long = 0L, percentiles: Option[Map[String, DurationNanos]] = None, id: UUID = new UUID())(implicit system: ActorSystem) = {
    if (percentiles.isDefined) new PercentilesSpanStats(timeRange, scope, spanTypeName, n, percentiles.get, id)
    else new PercentilesSpanStats(timeRange, scope, spanTypeName, n, defaultMap, id)
  }

}
