/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor._
import activator.analytics.repository.{ PlayStatsRepository, PlayTraceTreeRepository, PlayRequestSummaryRepository }
import com.typesafe.trace.store.TraceRetrievalRepository
import scala.concurrent.duration._

object PlayAnalyzerConfiguration {
  def apply(system: ActorSystem,
            actorOf: (Props, String) ⇒ ActorRef,
            dispatcherId: String,
            storeFlushDelay: Duration,
            accumulatorFlushDelay: Duration,
            statsFlushDelay: Duration,
            traceTreePurgeInterval: Duration,
            playRequestSummaryPurgeInterval: Duration,
            playStatsRepository: PlayStatsRepository,
            playRequestSummaryRepository: PlayRequestSummaryRepository,
            traceRepository: TraceRetrievalRepository,
            playTraceTreeRepository: PlayTraceTreeRepository): PlayAnalyzerConfiguration = {
    val playRequestSummaryStore = actorOf(Props(new PlayRequestSummaryStore(playRequestSummaryPurgeInterval.toMillis, playRequestSummaryRepository)).withDispatcher(dispatcherId), "playRequestSummaryStore")
    val playStatsAnalyzer = actorOf(Props(new PlayStatsAnalyzer(statsFlushDelay.toMillis, playRequestSummaryStore, playStatsRepository, traceRepository)).withDispatcher(dispatcherId), "playStatsAnalyzer")
    val traceTreeStore = actorOf(Props(new TraceTreeStore(storeFlushDelay.toMillis, traceTreePurgeInterval.toMillis, playTraceTreeRepository, playStatsAnalyzer)).withDispatcher(dispatcherId), "playTraceTreeStore")
    val traceAccumulator = actorOf(Props(new TraceAccumulator(accumulatorFlushDelay.toMillis, traceTreeStore)).withDispatcher(dispatcherId), "traceAccumulator")
    val eventFilter = actorOf(Props(new PlayEventFilter(traceAccumulator)).withDispatcher(dispatcherId), "playEventFilter")
    new PlayAnalyzerConfiguration(system = system,
      playStatsAnalyzer = playStatsAnalyzer,
      traceTreeStore = traceTreeStore,
      traceAccumulator = traceAccumulator,
      eventFilter = eventFilter,
      playRequestSummaryStore = playRequestSummaryStore)
  }
}

// This class represents a simple configuration of the Play analysis system
class PlayAnalyzerConfiguration(system: ActorSystem,
                                val eventFilter: ActorRef,
                                traceAccumulator: ActorRef, // could make more than one of these if needed
                                traceTreeStore: ActorRef, // could make more than one of these if needed
                                playStatsAnalyzer: ActorRef,
                                playRequestSummaryStore: ActorRef) {
  import scala.concurrent.Await
  import akka.pattern.gracefulStop
  import akka.actor.PoisonPill

  def awaitStop(actor: ActorRef, waitTime: FiniteDuration): Unit = awaitStop(Seq(actor), waitTime)

  def awaitStop(actors: Seq[ActorRef], waitTime: FiniteDuration): Unit = {
    for (a ← actors) {
      Await.ready(gracefulStop(a, waitTime), waitTime)
      system.stop(a)
    }
  }

  def shutdown(waitTime: FiniteDuration): Unit = {
    eventFilter ! PoisonPill
    awaitStop(eventFilter, waitTime)
    traceAccumulator ! TraceAccumulator.ForceFlushAndExit
    awaitStop(traceAccumulator, waitTime)
    traceTreeStore ! TraceTreeStore.ForceFlushAndExit
    awaitStop(traceTreeStore, waitTime)
    playStatsAnalyzer ! PoisonPill
    awaitStop(playStatsAnalyzer, waitTime)
    playRequestSummaryStore ! PoisonPill
    awaitStop(playRequestSummaryStore, waitTime)
  }
}
