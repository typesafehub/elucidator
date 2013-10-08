/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.repository._
import com.typesafe.atmos.trace.store.LocalMemoryTraceRepository

class LocalMemoryAtmosRepository(system: ActorSystem) {
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
