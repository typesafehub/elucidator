/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import akka.actor.ActorSystem
import activator.analytics.metrics.HistogramMetric
import com.typesafe.trace.uuid.UUID
import java.util.concurrent.TimeUnit.MICROSECONDS
import scala.concurrent.duration._
import activator.analytics.AnalyticsExtension

/**
 * Histogram statistics over the time period for the durations of the spans
 * that belong to the scope and span type. Histogram is number of spans that
 * fall into each duration interval (bucket).
 * A value fall into a bucket when the value is less than the corresponding bucket
 * boundary and greater than or equal to the previous bucket boundary. The last
 * bucket contains all values greater than or equal to the last bucket boundary.
 * The first bucket contains all values less than the first bucket boundary.
 * This means that there is always one more bucket than the number of
 * elements in the bucketBoundaries.
 */
case class HistogramSpanStats(
  timeRange: TimeRange,
  scope: Scope,
  spanTypeName: String,
  bucketBoundaries: IndexedSeq[Long],
  buckets: IndexedSeq[Long],
  id: UUID = new UUID()) extends BasicStats {

  def redistribute(newBucketBoundaries: IndexedSeq[Long]): HistogramSpanStats = {
    val metric = new HistogramMetric(bucketBoundaries, buckets)
    val newMetric = metric.redistribute(newBucketBoundaries)
    HistogramSpanStats(timeRange, scope, spanTypeName, newMetric.bucketBoundaries, newMetric.buckets)
  }
}

object HistogramSpanStats {
  /**
   * Configuration property activator.analytics.storageBucketBoundariesMicros.default.
   * Defines the bucket boundaries in microseconds of the histogram statistics.
   * The format is comma separated values "100, 200, 300", or width times number of buckets
   * "100x3", or a mix of both "20x4, 100, 200, 300, 700, 1000x4".
   * Default is "100x9, 1000x9, 10000x9, 1000000x9"
   * The boundaries can also be configured by span type, or user defined marker span name,
   * e.g. activator.analytics.bucketBoundariesMicros.mailbox.
   */
  def DefaultBucketBoundaries(implicit system: ActorSystem): IndexedSeq[Long] = {
    mapConfigBucketBoundaries(AnalyticsExtension(system).DefaultStorageBucketBoundariesMicros)
  }

  private def mapConfigBucketBoundaries(definition: String) = {
    HistogramMetric.bucketBoundaries(definition).
      map(micros ⇒ Duration(micros, MICROSECONDS).toNanos)
  }

  def configBucketBoundaries(name: String, prefix: String = "storage")(implicit system: ActorSystem): IndexedSeq[Long] = {
    if (system.settings.config.hasPath("activator.analytics." + prefix + "-bucket-boundaries-micros." + name)) {
      mapConfigBucketBoundaries(system.settings.config.getString("activator.analytics." + prefix + "-bucket-boundaries-micros." + name))
    } else if (system.settings.config.hasPath("activator.analytics." + prefix + "-bucket-boundaries-micros.default")) {
      mapConfigBucketBoundaries(system.settings.config.getString("activator.analytics." + prefix + "-bucket-boundaries-micros.default"))
    } else {
      DefaultBucketBoundaries
    }
  }

  def apply(timeRange: TimeRange, scope: Scope, spanTypeName: String)(implicit system: ActorSystem): HistogramSpanStats = {
    val boundaries = configBucketBoundaries(spanTypeName)
    apply(timeRange, scope, spanTypeName, boundaries)
  }

  def apply(timeRange: TimeRange, scope: Scope, spanTypeName: String, bucketBoundaries: IndexedSeq[Long])(implicit system: ActorSystem): HistogramSpanStats = {
    val buckets = Vector.fill(bucketBoundaries.size + 1)(0L)
    HistogramSpanStats(timeRange, scope, spanTypeName, bucketBoundaries, buckets)
  }

  def concatenate(stats: Iterable[HistogramSpanStats], timeRange: TimeRange, scope: Scope, spanTypeName: String)(implicit system: ActorSystem): HistogramSpanStats = {
    // the last defines the bucketBoundaries to use
    val statsList = stats.toList.reverse
    if (statsList.isEmpty) {
      HistogramSpanStats(timeRange, scope, spanTypeName)
    } else {
      val metrics = statsList.map(a ⇒ new HistogramMetric(a.bucketBoundaries, a.buckets))
      val acc = new HistogramMetric(metrics.head.bucketBoundaries)
      for (m ← metrics) {
        acc += m
      }
      HistogramSpanStats(timeRange, scope, spanTypeName, acc.bucketBoundaries, acc.buckets)
    }
  }
}
