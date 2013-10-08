/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.ActorSystem
import activator.analytics.repository._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

/**
 * Common things to implement for the analyzer types
 */
trait AnalyzerBoot {
  def system: ActorSystem

  def actorStatsRepository: ActorStatsRepository
  def dispatcherTimeSeriesRepository: DispatcherTimeSeriesRepository
  def errorStatsRepository: ErrorStatsRepository
  def histogramSpanStatsRepository: HistogramSpanStatsRepository
  def mailboxTimeSeriesRepository: MailboxTimeSeriesRepository
  def messageRateTimeSeriesRepository: MessageRateTimeSeriesRepository
  def metadataStatsRepository: MetadataStatsRepository
  def percentilesSpanStatsRepository: PercentilesSpanStatsRepository
  def playRequestSummaryRepository: PlayRequestSummaryRepository
  def playStatsRepository: PlayStatsRepository
  def playTraceTreeFlushAge: Long
  def playTraceTreeRepository: PlayTraceTreeRepository
  def recordStatsRepository: RecordStatsRepository
  def remoteStatusStatsRepository: RemoteStatusStatsRepository
  def spanRepository: SpanRepository
  def spanTimeSeriesRepository: SpanTimeSeriesRepository
  def summarySpanStatsRepository: SummarySpanStatsRepository
  def systemMetricsTimeSeriesRepository: SystemMetricsTimeSeriesRepository
  def traceRepository: TraceRetrievalRepository
}
