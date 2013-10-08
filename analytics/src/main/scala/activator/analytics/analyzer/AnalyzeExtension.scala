/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._

class StandardAnalyzeExtension(system: ExtendedActorSystem) extends Extension {
  val config = system.settings.config
  import config._
  import scala.collection.JavaConverters._

  val DefaultStorageBucketBoundariesMicros = getString("atmos.analytics.storage-bucket-boundaries-micros.default")
  val Partition = getString("atmos.analytics.partition")

  val AccumulatorFlushDelay = Duration(getMilliseconds("atmos.analytics.accumulator-flush-delay"), TimeUnit.MILLISECONDS)
  val ActorPathTimeRanges: Set[String] = getStringList("atmos.analytics.actor-path-time-ranges").asScala.toSet
  val IgnoreAggregatedSpanTimeSeries: Set[String] = getStringList("atmos.analytics.ignore-aggregated-span-time-series").asScala.toSet
  val IgnoreSpanTimeSeries: Set[String] = getStringList("atmos.analytics.ignore-span-time-series").asScala.toSet
  val IgnoreSpanTypes: Set[String] = getStringList("atmos.analytics.ignore-span-types").asScala.toSet
  val MaxDeadLetterDeviations = getInt("atmos.analytics.max-dead-letter-deviations")
  val MaxDeadlockDeviations = getInt("atmos.analytics.max-deadlock-deviations")
  val MaxErrorDeviations = getInt("atmos.analytics.max-error-deviations")
  val MaxUnhandledMessageDeviations = getInt("atmos.analytics.max-unhandled-message-deviations")
  val MaxWarningDeviations = getInt("atmos.analytics.max-warning-deviations")
  val Percentiles: Seq[String] = getStringList("atmos.analytics.percentiles").asScala.toSeq
  val PercentilesSampleReservoirSize = getInt("atmos.analytics.percentiles-sample-reservoir-size")
  val PercentilesSampleReservoirSizeIndividualActor = getInt("atmos.analytics.percentiles-sample-reservoir-size-individual-actor")
  val PercentilesStoreTimeInterval = getLong("atmos.analytics.percentiles-store-time-interval")
  val PlayStatsFlushInterval = Duration(getMilliseconds("atmos.analytics.play-stats-flush-interval"), TimeUnit.MILLISECONDS)
  val PlayTraceTreeMaxRetentionAge = Duration(getMilliseconds("atmos.analytics.play-trace-tree-max-retention-age"), TimeUnit.MILLISECONDS)
  val PlayTraceTreePurgeInterval = Duration(getMilliseconds("atmos.analytics.play-trace-tree-purge-interval"), TimeUnit.MILLISECONDS)
  val PlayRequestSummaryRetentionAge = Duration(getMilliseconds("atmos.analytics.play-request-summary-max-retention-age"), TimeUnit.MILLISECONDS)
  val PlayRequestSummaryPurgeInterval = Duration(getMilliseconds("atmos.analytics.play-request-summary-purge-interval"), TimeUnit.MILLISECONDS)
  val PlayTraceTreeFlushAge = Duration(getMilliseconds("atmos.analytics.play-trace-tree-flush-age"), TimeUnit.MILLISECONDS)
  val StoreFlushDelay = Duration(getMilliseconds("atmos.analytics.store-flush-delay"), TimeUnit.MILLISECONDS)
  val StoreLimit = getLong("atmos.analytics.store-limit")
  val StoreTimeInterval = getLong("atmos.analytics.store-time-interval")
  val StoreUseAllTime = getBoolean("atmos.analytics.store-use-all-time")
  val UseNanoTimeCrossNodes = getBoolean("atmos.analytics.use-nano-time-cross-nodes")

  val MaxRetryAttempts = getInt("atmos.analytics.max-retry-attempts")
  val RetryDelay = Duration(getMilliseconds("atmos.analytics.retry-delay"), TimeUnit.MILLISECONDS)
  val SaveSpans = getBoolean("atmos.analytics.save-spans")

  val FailoverTimeout = Duration(getMilliseconds("atmos.subscribe.failover-timeout"), TimeUnit.MILLISECONDS)
  val NotificationEventLogSize = getLong("atmos.subscribe.notification-event-log-size")

  val UseActorStatsAnalyzer = getBoolean("atmos.analytics.use-actor-stats-analyzer")
  val UseDispatcherTimeSeriesAnalyzer = getBoolean("atmos.analytics.use-dispatcher-time-series-analyzer")
  val UseErrorStatsAnalyzer = getBoolean("atmos.analytics.use-error-stats-analyzer")
  val UseHistogramSpanStatsAnalyzer = getBoolean("atmos.analytics.use-histogram-span-stats-analyzer")
  val UseMailboxTimeSeriesAnalyzer = getBoolean("atmos.analytics.use-mailbox-time-series-analyzer")
  val UseMessageRateTimeSeriesAnalyzer = getBoolean("atmos.analytics.use-message-rate-time-series-analyzer")
  val UseMetadataStatsAnalyzer = getBoolean("atmos.analytics.use-metadata-stats-analyzer")
  val UsePercentilesSpanStatsAnalyzer = getBoolean("atmos.analytics.use-percentiles-span-stats-analyzer")
  val UsePlayStatsAnalyzer = getBoolean("atmos.analytics.use-play-stats-analyzer")
  val UseRecordStatsAnalyzer = getBoolean("atmos.analytics.use-record-stats-analyzer")
  val UseRemoteStatusStatsAnalyzer = getBoolean("atmos.analytics.use-remote-status-stats-analyzer")
  val UseSpanTimeSeriesAnalyzer = getBoolean("atmos.analytics.use-span-time-series-analyzer")
  val UseSummarySpanStatsAnalyzer = getBoolean("atmos.analytics.use-summary-span-stats-analyzer")
  val UseSystemMetricsTimeSeriesAnalyzer = getBoolean("atmos.analytics.use-system-metrics-time-series-analyzer")
}

object AnalyzeExtension extends ExtensionId[StandardAnalyzeExtension] with ExtensionIdProvider {

  def lookup() = AnalyzeExtension

  def createExtension(system: ExtendedActorSystem) = new StandardAnalyzeExtension(system)
}
