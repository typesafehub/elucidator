/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes.DurationNanos
import activator.analytics.metrics.SummaryMetrics
import com.typesafe.trace.uuid.UUID
import scala.annotation.tailrec

/**
 * Aggregated statistics of overview character over the time period
 * for the durations of the spans that belong to the scope and
 * span type.
 */
case class SummarySpanStats(
  timeRange: TimeRange,
  scope: Scope,
  spanTypeName: String,
  n: Long = 0L,
  totalDuration: DurationNanos = 0L,
  minDuration: DurationNanos = -1L,
  maxDuration: DurationNanos = -1L,
  meanDuration: Double = 0.0,
  id: UUID = new UUID()) extends BasicStats

object SummarySpanStats extends Chunker[SummarySpanStats] {

  def concatenate(stats: Iterable[SummarySpanStats], timeRange: TimeRange, scope: Scope, spanTypeName: String): SummarySpanStats = {
    val metrics = stats.map(s ⇒ new SummaryMetrics(s.n, s.totalDuration, s.minDuration, s.maxDuration))
    val acc = new SummaryMetrics()
    for (m ← metrics) {
      acc += m
    }

    SummarySpanStats(timeRange, scope, spanTypeName, acc.count, acc.sum, acc.min, acc.max, acc.mean)
  }

  def chunk(
    minNumberOfChunks: Int,
    maxNumberOfChunks: Int,
    stats: Iterable[SummarySpanStats],
    timeRange: TimeRange,
    scope: Scope,
    spanTypeName: String): Seq[SummarySpanStats] = {

    chunk(minNumberOfChunks, maxNumberOfChunks, stats, timeRange,
      s ⇒ s.timeRange,
      t ⇒ SummarySpanStats(t, scope, spanTypeName),
      (t, s) ⇒ SummarySpanStats.concatenate(s, t, scope, spanTypeName))
  }

}
