/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ Props, ActorRef, PoisonPill }
import com.typesafe.atmos.trace.{ TraceEvents, TraceEvent }
import activator.analytics.common.TraceExample
import activator.analytics.repository.MemoryPlayTraceTreeRepository
import scala.concurrent.{ Promise, Future, Await }
import scala.concurrent.duration._
import activator.analytics.AnalyticsSpec

object TraceTreeStoreSpec {
  val uuid = TraceExample.traceId
  case object RunTest
  val teSet = TraceExample.traceTree.toSet
}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class TraceTreeStoreSpec extends AnalyticsSpec with AnalyzeTest {
  import TraceTreeStoreSpec._

  def withStore(test: Seq[TraceEvent] ⇒ Boolean, flushAge: Long, within: Int = 5 * 1000, repoRetentionAge: Long = 5 * 1000L)(body: (ActorRef, MemoryPlayTraceTreeRepository, Future[Boolean]) ⇒ Unit): Unit = {
    val traceTreeRepository = new MemoryPlayTraceTreeRepository(repoRetentionAge)
    val result: Promise[Boolean] = Promise[Boolean]()
    val recipient: ActorRef = system.actorOf(Props(new TestEventuallyActor(within, result, test)))
    val store: ActorRef = system.actorOf(Props(new TraceTreeStore(flushAge, 1000L, traceTreeRepository, recipient)))
    body(store, traceTreeRepository, result.future)
    recipient ! PoisonPill
    store ! PoisonPill
    awaitStop(store)
    awaitStop(recipient)
  }

  "TraceTreeStore" must {
    "accept an entire trace tree" in {
      withStore({ _.toSet == teSet }, 2000L) { (store, repo, result) ⇒
        store ! TraceEvents(TraceExample.traceTree)
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
        Thread.sleep(3000)
        val r = repo.find(TraceExample.traceId)
        r must not be (None)
        r.foreach(_.toSet must be(teSet))
      }
    }
    "purge old trace trees" in {
      withStore({ _.toSet == teSet }, 2000L) { (store, repo, result) ⇒
        store ! TraceEvents(TraceExample.traceTree)
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
        Thread.sleep(7000)
        val r = repo.find(TraceExample.traceId)
        r must be(None)
      }
    }
    "accumulate an entire trace tree in parts" in {
      withStore({ _.toSet == teSet }, 2000L) { (store, repo, result) ⇒
        TraceExample.traceTree.grouped(10).foreach { g ⇒
          store ! TraceEvents(g)
          Thread.sleep(100)
        }
        val outcome = Await.result(result, Duration(10, SECONDS))
        outcome must be(true)
        Thread.sleep(3000)
        val r = repo.find(TraceExample.traceId)
        r must not be (None)
        r.foreach(_.toSet must be(teSet))
      }
    }
  }
}
