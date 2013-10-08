/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ Props, ActorRef, PoisonPill }
import com.typesafe.atmos.trace.{ TraceEvents, TraceEvent }
import activator.analytics.common.TraceExample
import com.typesafe.atmos.util.AtmosSpec
import com.typesafe.atmos.uuid.UUID
import scala.concurrent._
import scala.concurrent.duration._

object PlayEventFilterSpec {
  val uuid = TraceExample.traceId
  val teSet = TraceExample.traceTree.toSet
  val alt = TraceExample.traceTreeAlt.map(_.copy(id = new UUID))
  val teSetAlt = alt.toSet
  val teSetFiltered = teSet.filter(PlayEventFilter.isInteresting)
  val teSetAltFiltered = teSetAlt.filter(PlayEventFilter.isInteresting)
}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class PlayEventFilterSpec extends AtmosSpec with AnalyzeTest {
  import PlayEventFilterSpec._

  def withFilter(test: Seq[TraceEvent] ⇒ Boolean, flushAge: Long, within: Int = 5 * 1000, repoRetentionAge: Long = 60 * 1000L)(body: (ActorRef, Future[Boolean]) ⇒ Unit): Unit = {
    val result: Promise[Boolean] = Promise[Boolean]()
    val recipient: ActorRef = system.actorOf(Props(new TestEventuallyActor(within, result, test)))
    val filter: ActorRef = system.actorOf(Props(new PlayEventFilter(recipient)))
    body(filter, result.future)
    recipient ! PoisonPill
    filter ! PoisonPill
    awaitStop(filter)
    awaitStop(recipient)
  }

  def intercalate(l: Seq[TraceEvent], r: Seq[TraceEvent]): Seq[TraceEvent] = {
    l.zip(r).foldLeft(Seq.newBuilder[TraceEvent])({
      case (s, (le, re)) ⇒ s += (le, re)
    }).result
  }

  "PlayEventFilter" must {
    "filter an entire trace tree" in {
      withFilter({ _.toSet == teSetFiltered }, 2000L) { (filter, result) ⇒
        filter ! TraceEvents(TraceExample.traceTree)
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
      }
    }
    "filter trace trees regardless of trace id" in {
      withFilter({ _.toSet == teSetFiltered.union(teSetAltFiltered) }, 2000L) { (filter, result) ⇒
        filter ! TraceEvents(intercalate(TraceExample.traceTree, alt))
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
      }
    }
    "filter an entire trace tree in parts" in {
      var accumulator: Set[TraceEvent] = Set.empty[TraceEvent]
      def test(in: Seq[TraceEvent]): Boolean = {
        accumulator = accumulator union in.toSet
        accumulator.toSet == teSetFiltered
      }
      withFilter(test, 2000L) { (filter, result) ⇒
        TraceExample.traceTree.grouped(10).foreach { g ⇒
          filter ! TraceEvents(g)
          Thread.sleep(100)
        }
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
      }
    }
  }
}
