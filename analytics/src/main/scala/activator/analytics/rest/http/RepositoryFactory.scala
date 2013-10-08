/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.repository._
import com.typesafe.atmos.trace.store.LocalMemoryTraceRepository
import com.typesafe.atmos.trace.store.TraceRetrievalRepository

trait AtmosRepository {
  def actorStatsRepository: ActorStatsRepository
  def dispatcherTimeSeriesRepository: DispatcherTimeSeriesRepository
  def errorStatsRepository: ErrorStatsRepository
  def histogramSpanStatsRepository: HistogramSpanStatsRepository
  def hostStatsRepository: HostStatsRepository
  def mailboxTimeSeriesRepository: MailboxTimeSeriesRepository
  def messageRateTimeSeriesRepository: MessageRateTimeSeriesRepository
  def metadataStatsRepository: MetadataStatsRepository
  def percentilesSpanStatsRepository: PercentilesSpanStatsRepository
  def playRequestSummaryRepository: PlayRequestSummaryRepository
  def playStatsRepository: PlayStatsRepository
  def recordStatsRepository: RecordStatsRepository
  def remoteStatusStatsRepository: RemoteStatusStatsRepository
  def spanRepository: SpanRepository
  def spanTimeSeriesRepository: SpanTimeSeriesRepository
  def summarySpanStatsRepository: SummarySpanStatsRepository
  def systemMetricsTimeSeriesRepository: SystemMetricsTimeSeriesRepository
  def traceRepository: TraceRetrievalRepository
}

class LocalMemoryAtmosRepository(system: ActorSystem) extends AtmosRepository {
  def actorStatsRepository = LocalMemoryActorStatsRepository
  def dispatcherTimeSeriesRepository = LocalMemoryDispatcherTimeSeriesRepository
  def errorStatsRepository = LocalMemoryErrorStatsRepository
  def histogramSpanStatsRepository = LocalMemoryHistogramSpanStatsRepository
  def hostStatsRepository = LocalMemoryHostStatsRepository
  def mailboxTimeSeriesRepository = LocalMemoryMailboxTimeSeriesRepository
  def messageRateTimeSeriesRepository = LocalMemoryMessageRateTimeSeriesRepository
  def metadataStatsRepository = LocalMemoryMetadataStatsRepository
  def percentilesSpanStatsRepository = LocalMemoryPercentilesSpanStatsRepository
  def playRequestSummaryRepository = LocalMemoryPlayRequestSummaryRepository
  def playStatsRepository = LocalMemoryPlayStatsRepository
  def recordStatsRepository = LocalMemoryRecordStatsRepository
  def remoteStatusStatsRepository = LocalMemoryRemoteStatusStatsRepository
  def spanRepository = LocalMemorySpanRepository
  def spanTimeSeriesRepository = LocalMemorySpanTimeSeriesRepository
  def summarySpanStatsRepository = LocalMemorySummarySpanStatsRepository
  def systemMetricsTimeSeriesRepository = LocalMemorySystemMetricsRepository
  val traceRepository = LocalMemoryTraceRepository
}

object RepositoryFactory {
  val MongoAtmosRepositoryName = "com.typesafe.atmos.analytics.http.MongoAtmosRepository"
  def repository(system: ActorSystem): AtmosRepository = new LocalMemoryAtmosRepository(system)
}
