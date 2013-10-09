/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import akka.actor.{ ExtensionIdProvider, ExtensionId, Extension, ExtendedActorSystem }

class StandardAnalyticsExtension(system: ExtendedActorSystem) extends Extension {
  val config = system.settings.config
  import config._
  import scala.collection.JavaConverters._

  // ** ANALYZE RELATED **

  val AccumulatorFlushDelay = Duration(getMilliseconds("activator.analytics.accumulator-flush-delay"), TimeUnit.MILLISECONDS)
  val ActorPathTimeRanges: Set[String] = getStringList("activator.analytics.actor-path-time-ranges").asScala.toSet
  val DefaultStorageBucketBoundariesMicros = getString("activator.analytics.storage-bucket-boundaries-micros.default")
  val Partition = getString("activator.analytics.partition")
  val IgnoreAggregatedSpanTimeSeries: Set[String] = getStringList("activator.analytics.ignore-aggregated-span-time-series").asScala.toSet
  val IgnoreSpanTimeSeries: Set[String] = getStringList("activator.analytics.ignore-span-time-series").asScala.toSet
  val IgnoreSpanTypes: Set[String] = getStringList("activator.analytics.ignore-span-types").asScala.toSet
  val MaxDeadLetterDeviations = getInt("activator.analytics.max-dead-letter-deviations")
  val MaxDeadlockDeviations = getInt("activator.analytics.max-deadlock-deviations")
  val MaxErrorDeviations = getInt("activator.analytics.max-error-deviations")
  val MaxUnhandledMessageDeviations = getInt("activator.analytics.max-unhandled-message-deviations")
  val MaxWarningDeviations = getInt("activator.analytics.max-warning-deviations")
  val Percentiles: Seq[String] = getStringList("activator.analytics.percentiles").asScala.toSeq
  val PercentilesSampleReservoirSize = getInt("activator.analytics.percentiles-sample-reservoir-size")
  val PercentilesSampleReservoirSizeIndividualActor = getInt("activator.analytics.percentiles-sample-reservoir-size-individual-actor")
  val PercentilesStoreTimeInterval = getLong("activator.analytics.percentiles-store-time-interval")
  val PlayStatsFlushInterval = Duration(getMilliseconds("activator.analytics.play-stats-flush-interval"), TimeUnit.MILLISECONDS)
  val PlayTraceTreeMaxRetentionAge = Duration(getMilliseconds("activator.analytics.play-trace-tree-max-retention-age"), TimeUnit.MILLISECONDS)
  val PlayTraceTreePurgeInterval = Duration(getMilliseconds("activator.analytics.play-trace-tree-purge-interval"), TimeUnit.MILLISECONDS)
  val PlayRequestSummaryRetentionAge = Duration(getMilliseconds("activator.analytics.play-request-summary-max-retention-age"), TimeUnit.MILLISECONDS)
  val PlayRequestSummaryPurgeInterval = Duration(getMilliseconds("activator.analytics.play-request-summary-purge-interval"), TimeUnit.MILLISECONDS)
  val PlayTraceTreeFlushAge = Duration(getMilliseconds("activator.analytics.play-trace-tree-flush-age"), TimeUnit.MILLISECONDS)
  val StoreFlushDelay = Duration(getMilliseconds("activator.analytics.store-flush-delay"), TimeUnit.MILLISECONDS)
  val StoreLimit = getLong("activator.analytics.store-limit")
  val StoreTimeInterval = getLong("activator.analytics.store-time-interval")
  val StoreUseAllTime = getBoolean("activator.analytics.store-use-all-time")
  val UseNanoTimeCrossNodes = getBoolean("activator.analytics.use-nano-time-cross-nodes")
  val MaxRetryAttempts = getInt("activator.analytics.max-retry-attempts")
  val RetryDelay = Duration(getMilliseconds("activator.analytics.retry-delay"), TimeUnit.MILLISECONDS)
  val SaveSpans = getBoolean("activator.analytics.save-spans")
  val UseActorStatsAnalyzer = getBoolean("activator.analytics.use-actor-stats-analyzer")
  val UseDispatcherTimeSeriesAnalyzer = getBoolean("activator.analytics.use-dispatcher-time-series-analyzer")
  val UseErrorStatsAnalyzer = getBoolean("activator.analytics.use-error-stats-analyzer")
  val UseHistogramSpanStatsAnalyzer = getBoolean("activator.analytics.use-histogram-span-stats-analyzer")
  val UseMailboxTimeSeriesAnalyzer = getBoolean("activator.analytics.use-mailbox-time-series-analyzer")
  val UseMessageRateTimeSeriesAnalyzer = getBoolean("activator.analytics.use-message-rate-time-series-analyzer")
  val UseMetadataStatsAnalyzer = getBoolean("activator.analytics.use-metadata-stats-analyzer")
  val UsePercentilesSpanStatsAnalyzer = getBoolean("activator.analytics.use-percentiles-span-stats-analyzer")
  val UsePlayStatsAnalyzer = getBoolean("activator.analytics.use-play-stats-analyzer")
  val UseRecordStatsAnalyzer = getBoolean("activator.analytics.use-record-stats-analyzer")
  val UseRemoteStatusStatsAnalyzer = getBoolean("activator.analytics.use-remote-status-stats-analyzer")
  val UseSpanTimeSeriesAnalyzer = getBoolean("activator.analytics.use-span-time-series-analyzer")
  val UseSummarySpanStatsAnalyzer = getBoolean("activator.analytics.use-summary-span-stats-analyzer")
  val UseSystemMetricsTimeSeriesAnalyzer = getBoolean("activator.analytics.use-system-metrics-time-series-analyzer")

  // ** REST RELATED **

  final val HtmlFileResources = config.getString("activator.analytics.html-file-resources")
  final val JsonPrettyPrint = config.getBoolean("activator.analytics.json-pretty-print")
  final val MaxTimeriesPoints = config.getInt("activator.analytics.max-timeseries-points")
  final val MaxSpanTimeriesPoints = config.getInt("activator.analytics.max-span-timeseries-points")
  final val PagingSize = config.getInt("activator.analytics.paging-size")
  final val DefaultLimit = config.getInt("activator.analytics.default-limit")
  final val IncludeAnonymousActorPathsInMetadata = config.getBoolean("activator.analytics.include-anonymous-paths-in-metadata")
  final val IncludeTempActorPathsInMetadata = config.getBoolean("activator.analytics.include-temp-paths-in-metadata")
}

object AnalyticsExtension extends ExtensionId[StandardAnalyticsExtension] with ExtensionIdProvider {

  def lookup() = AnalyticsExtension

  def createExtension(system: ExtendedActorSystem) = new StandardAnalyticsExtension(system)
}
