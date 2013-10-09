/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ Props, ActorRef, PoisonPill }
import com.typesafe.atmos.trace.{ TraceEvents, TraceEvent }
import activator.analytics.common.TraceExample
import scala.concurrent.{ Promise, Future, Await }
import scala.concurrent.duration._
import activator.analytics.AnalyticsSpec
import activator.analytics.TimeoutHandler

object TraceAccumulatorSpec {
  val uuid = TraceExample.traceId
  val teSet = TraceExample.traceTree.toSet
  val teSetAlt = TraceExample.traceTreeAlt.toSet
}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class TraceAccumulatorSpec extends AnalyticsSpec with AnalyzeTest {
  import TraceAccumulatorSpec._

  def withAccumulator(test: Seq[TraceEvent] ⇒ Boolean, flushAge: Long, within: Int = 5 * 1000, repoRetentionAge: Long = 60 * 1000L)(body: (ActorRef, Future[Boolean]) ⇒ Unit): Unit = {
    val result: Promise[Boolean] = Promise[Boolean]()
    val recipient: ActorRef = system.actorOf(Props(new TestEventuallyActor(within, result, test)))
    val accumulator: ActorRef = system.actorOf(Props(new TraceAccumulator(flushAge, recipient)))
    body(accumulator, result.future)
    recipient ! PoisonPill
    accumulator ! PoisonPill
    awaitStop(accumulator)
    awaitStop(recipient)
  }

  def intercalate(l: Seq[TraceEvent], r: Seq[TraceEvent]): Seq[TraceEvent] = {
    l.zip(r).foldLeft(Seq.newBuilder[TraceEvent])({
      case (s, (le, re)) ⇒ s += (le, re)
    }).result
  }

  "TraceAccumulator" must {
    "accept an entire trace tree" in {
      withAccumulator({ _.toSet == teSet }, 2000L) { (store, result) ⇒
        store ! TraceEvents(TraceExample.traceTree)
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
      }
    }
    "accumulate trace trees into appropriate bins" in {
      var setsRemaining = 2
      def countSets(in: Seq[TraceEvent]): Boolean = {
        if (in.toSet == teSet) setsRemaining -= 1
        if (in.toSet == teSetAlt) setsRemaining -= 1
        setsRemaining == 0
      }
      withAccumulator(countSets, 2000L) { (store, result) ⇒
        store ! TraceEvents(intercalate(TraceExample.traceTree, TraceExample.traceTreeAlt))
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
      }
    }
    "accumulate an entire trace tree in parts" in {
      withAccumulator({ _.toSet == teSet }, 2000L) { (store, result) ⇒
        TraceExample.traceTree.grouped(10).foreach { g ⇒
          store ! TraceEvents(g)
          Thread.sleep(100)
        }
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
      }
    }
    "flush partial trace trees" in {
      val chunks: Int = 4 + (if (TraceExample.traceTree.length % 4 == 0) 0 else 1)
      val chunkSize = TraceExample.traceTree.length / 4
      var chunksRemaining = chunks
      def countChunks(evs: Seq[TraceEvent]): Boolean = {
        if (!evs.isEmpty && evs.toSet.subsetOf(teSet)) chunksRemaining -= 1
        chunksRemaining == 0
      }
      withAccumulator(countChunks, 100L) { (store, result) ⇒
        TraceExample.traceTree.grouped(chunkSize).foreach { g ⇒
          store ! TraceEvents(g)
          Thread.sleep(300)
        }
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
      }
    }
  }
}
