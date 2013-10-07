/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ ActorContext, Props, ActorSystem, ActorRef }
import activator.analytics.repository._
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import scala.collection.mutable.ArrayBuffer

/**
 * Common things to implement for the analyzer types (local and mongo).
 */
trait AnalyzerBoot {
  def system: ActorSystem

  def actorStatsRepository: ActorStatsRepository
  def dispatcherTimeSeriesRepository: DispatcherTimeSeriesRepository
  def duplicatesRepository: DuplicatesRepository
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
