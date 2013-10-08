/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import collection.mutable.ListBuffer
import activator.analytics.repository._
import com.typesafe.atmos.subscribe.SubscribeMessages.Ack
import com.typesafe.atmos.trace._
import com.typesafe.atmos.trace.store.MemoryTraceEventListener
import com.typesafe.config.Config

/**
 * Local memory analyzer.
 * Only intended to be used for development and test.
 */
class LocalMemoryAnalyzerBoot(val system: ActorSystem, receiver: Option[TraceReceiver] = None)
  extends AnalyzerBoot {

  val traceReceiver = receiver orElse ReceiveMain.getReceiver getOrElse {
    throw new Exception("LocalMemoryAnalyzerBoot must be used with a TraceReceiver")
  }

  val traceRepository = MemoryTraceEventListener.getRepositoryFor(traceReceiver).getOrElse {
    throw new Exception("LocalMemoryAnalyzerBoot must be used with MemoryTraceEventListener")
  }

  val actorStatsRepository = LocalMemoryActorStatsRepository
  val dispatcherTimeSeriesRepository = LocalMemoryDispatcherTimeSeriesRepository
  val errorStatsRepository = LocalMemoryErrorStatsRepository
  val histogramSpanStatsRepository = LocalMemoryHistogramSpanStatsRepository
  val mailboxTimeSeriesRepository = LocalMemoryMailboxTimeSeriesRepository
  val messageRateTimeSeriesRepository = LocalMemoryMessageRateTimeSeriesRepository
  val metadataStatsRepository = LocalMemoryMetadataStatsRepository
  val percentilesSpanStatsRepository = LocalMemoryPercentilesSpanStatsRepository
  val playStatsRepository = LocalMemoryPlayStatsRepository
  val playTraceTreeRepository = LocalMemoryPlayTraceTreeRepository
  val recordStatsRepository = LocalMemoryRecordStatsRepository
  val remoteStatusStatsRepository = LocalMemoryRemoteStatusStatsRepository
  val spanRepository = LocalMemorySpanRepository
  val spanTimeSeriesRepository = LocalMemorySpanTimeSeriesRepository
  val summarySpanStatsRepository = LocalMemorySummarySpanStatsRepository
  val systemMetricsTimeSeriesRepository = LocalMemorySystemMetricsRepository
  val playRequestSummaryRepository = LocalMemoryPlayRequestSummaryRepository
  val playTraceTreeFlushAge: Long = 2L * 1000L // 2 seconds
  /**
   * Make it easy to override construction of Analyzer in tests
   */
  def createAnalyzer() = new Analyzer(this)

  val analyzer = system.actorOf(Props(createAnalyzer).withDispatcher(Analyzer.dispatcherId), "atmos-analyzer")
  val batcher = system.actorOf(Props(new Batcher(analyzer)).withDispatcher(Analyzer.dispatcherId), "atmos-batcher")

  LocalMemoryAnalyzerBoot.addBootFor(traceReceiver, this)

  def topLevelActors: Seq[ActorRef] = Seq(analyzer, batcher)
}

class Batcher(receiver: ActorRef) extends Actor with ActorLogging {
  val MaxEvents = 1000
  val MaxTimeMillis = 4000L
  val events = ListBuffer[TraceEvent]()
  var nextFlushTime = 0L;

  def receive = {
    case TraceEvents(events) ⇒
      handleEvents(events)
    case ack: Ack ⇒
  }

  def handleEvents(newEvents: Seq[TraceEvent]) {
    events ++= newEvents
    if (timeToFlush) {
      receiver ! TraceEvents(events.toSeq)
      nextFlushTime = System.currentTimeMillis + MaxTimeMillis
      events.clear()
    }
  }

  def timeToFlush = {
    System.currentTimeMillis >= nextFlushTime || events.size >= MaxEvents
  }
}

/**
 * Store local memory analyzers for event listeners to pick up and use.
 */
object LocalMemoryAnalyzerBoot {
  private[this] var boots: Map[String, LocalMemoryAnalyzerBoot] = Map.empty

  def get(id: Option[String]): Option[LocalMemoryAnalyzerBoot] = synchronized {
    boots get id.getOrElse("local")
  }

  def getAnalyzer(id: Option[String]): Option[ActorRef] = {
    get(id).map(_.batcher)
  }

  def add(id: String, boot: LocalMemoryAnalyzerBoot): Unit = synchronized {
    boots += id -> boot
  }

  def addBootFor(receiver: TraceReceiver, boot: LocalMemoryAnalyzerBoot): Unit = {
    getIdFor(receiver) foreach { id ⇒ add(id, boot) }
  }

  def getIdFor(receiver: TraceReceiver): Option[String] = {
    if (MemoryTraceEventListener.useLocal(receiver)) Some("local") else getListenerFor(receiver) map { _.path.name }
  }

  def getListenerFor(receiver: TraceReceiver): Option[ActorRef] = {
    receiver.eventHandler.listeners find { _.path.name contains "LocalAnalyzerTraceEventListener" }
  }

  def remove(id: String): Unit = synchronized {
    boots -= id
  }
}

/**
 * Publishes to the local memory analyzer stored for this event listener.
 * For testing and development only.
 */
class LocalAnalyzerTraceEventListener(override val config: Config) extends TraceEventListener {
  import LocalMemoryAnalyzerBoot.{ getAnalyzer, remove }

  def repository = sys.error("no repository on publish to analyzer")
  def currentStore = sys.error("no current store on publish to analyzer")

  val useLocal = MemoryTraceEventListener.useLocal(config)

  override def handle(batch: Batch) {
    for (traceEvents ← batch.payload) {
      val analyzer = if (useLocal) getAnalyzer(None) else getAnalyzer(Some(self.path.name))
      analyzer foreach { _ ! traceEvents }
    }
  }

  override def postStop = remove(self.path.name)
}
