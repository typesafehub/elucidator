/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ ActorSystem, ReflectiveDynamicAccess }
import activator.analytics.repository._
import com.typesafe.atmos.trace.store.LocalMemoryTraceRepository
import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import com.typesafe.inkan.TypesafeLicense
import java.util.Date
import scala.util.{ Failure, Success }

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

  def repository(license: Option[TypesafeLicense], system: ActorSystem, mode: String): AtmosRepository = {
    val repo: AtmosRepository = license match {
      case None ⇒
        system.log.info("*** No license available. Defaulting to in-memory persistence. ***")
        new LocalMemoryAtmosRepository(system)
      case Some(l) ⇒
        system.log.info("*** Licensed servers: " + l.servers + ", expiry = " + new Date(l.expiry) + " ***")
        mode match {
          case "local" ⇒ new LocalMemoryAtmosRepository(system)
          case "mongo" ⇒
            val dynamicAccess = new ReflectiveDynamicAccess(getClass.getClassLoader)
            dynamicAccess.createInstanceFor[AtmosRepository](MongoAtmosRepositoryName, Seq(classOf[ActorSystem] -> system)) match {
              case Success(repository) ⇒ repository
              case Failure(exception)  ⇒ throw new UnsupportedOperationException("Failed to create mongo atmos repository", exception)
            }
          case x ⇒ throw new UnsupportedOperationException("Unknown repository mode: " + x)
        }
    }
    repo
  }
}
