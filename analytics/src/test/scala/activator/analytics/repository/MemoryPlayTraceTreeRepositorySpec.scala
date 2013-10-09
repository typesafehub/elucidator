/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import activator.analytics.common.TraceExample
import com.typesafe.atmos.trace._
import com.typesafe.atmos.util._
import com.typesafe.atmos.uuid.UUID
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.MustMatchers
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MemoryPlayTraceTreeRepositorySpec extends AnalyticsSpec with MustMatchers with BeforeAndAfterEach {

  case class TaggedTrace(traceId: UUID, traces: Seq[Seq[TraceEvent]])

  var repository: MemoryPlayTraceTreeRepository = _

  override def beforeEach() {
    repository = new MemoryPlayTraceTreeRepository(1000)
  }

  override def afterEach() {
    repository = null
  }

  "A MemoryPlayTraceTreeRepository" must {
    "not retrieve trees that aren't there" in {
      val found = repository.find(TraceExample.traceId)
      found must be(None)
    }
    "store and retrieve Play TraceTrees" in {
      repository.save(TraceExample.traceId, TraceExample.traceTree)
      val found = repository.find(TraceExample.traceId)
      found must not be (None)
      found.foreach { f ⇒
        TraceExample.traceTree.toSet must be(f.toSet)
      }
    }
    "store and retrieve multiple versions Play TraceTrees" in {
      val versions = Seq(TraceExample.traceTree.take(10), TraceExample.traceTree.take(20), TraceExample.traceTree.take(100), TraceExample.traceTree)
      versions.foreach { version ⇒
        repository.save(TraceExample.traceId, version)
        val found = repository.find(TraceExample.traceId)
        found must not be (None)
        found.foreach { f ⇒
          version.toSet must be(f.toSet)
        }
      }
    }
    "purge old versions Play TraceTrees" in {
      val versions = Seq(TraceExample.traceTree.take(10), TraceExample.traceTree.take(20), TraceExample.traceTree.take(100))
      versions.foreach { version ⇒
        repository.save(TraceExample.traceId, version)
        val found = repository.find(TraceExample.traceId)
        found must not be (None)
        found.foreach { f ⇒
          version.toSet must be(f.toSet)
        }
      }
      Thread.sleep(2000)
      repository.save(TraceExample.traceId, TraceExample.traceTree)
      val found1 = repository.find(TraceExample.traceId)
      found1 must not be (None)
      found1.foreach { f ⇒
        TraceExample.traceTree.toSet must be(f.toSet)
      }
      repository.purgeOld()
      val found2 = repository.find(TraceExample.traceId)
      found2 must not be (None)
      found2.foreach { f ⇒
        TraceExample.traceTree.toSet must be(f.toSet)
      }
      Thread.sleep(2000)
      repository.purgeOld()
      val found3 = repository.find(TraceExample.traceId)
      found3 must be(None)
    }
    "store multiple Play TraceTrees that do not interfere with one-another" in {
      val versions = Seq((TraceExample.traceId, TraceExample.traceTree), (TraceExample.traceIdAlt, TraceExample.traceTreeAlt))
      versions.foreach { case (id, data) ⇒ repository.save(id, data) }
      versions.foreach {
        case (id, data) ⇒
          val found = repository.find(id)
          found must not be (None)
          found.foreach { f ⇒
            data.toSet must be(f.toSet)
          }
      }
    }
    "store versions of multiple Play TraceTrees that do not interfere with one-another" in {
      val versionsA = TaggedTrace(TraceExample.traceId, Seq(TraceExample.traceTree.take(5), TraceExample.traceTree.take(20), TraceExample.traceTree.take(100), TraceExample.traceTree))
      val versionsB = TaggedTrace(TraceExample.traceIdAlt, Seq(TraceExample.traceTreeAlt.take(5), TraceExample.traceTreeAlt.take(20), TraceExample.traceTreeAlt.take(100), TraceExample.traceTreeAlt))

      versionsA.traces.foreach { version ⇒
        repository.save(versionsA.traceId, version)
        val found = repository.find(versionsA.traceId)
        found must not be (None)
        found.foreach { f ⇒
          version.toSet must be(f.toSet)
        }
      }

      versionsB.traces.foreach { version ⇒
        repository.save(versionsB.traceId, version)
        val found = repository.find(versionsB.traceId)
        found must not be (None)
        found.foreach { f ⇒
          version.toSet must be(f.toSet)
        }
      }
    }
    "purging old versions of Play TraceTrees should not interfere with other trace trees" in {
      val versionsA = TaggedTrace(TraceExample.traceId, Seq(TraceExample.traceTree.take(5), TraceExample.traceTree.take(20), TraceExample.traceTree.take(100), TraceExample.traceTree))
      val versionsB = TaggedTrace(TraceExample.traceIdAlt, Seq(TraceExample.traceTreeAlt.take(5), TraceExample.traceTreeAlt.take(20), TraceExample.traceTreeAlt.take(100), TraceExample.traceTreeAlt))

      versionsA.traces.take(versionsA.traces.size - 1).foreach { version ⇒
        repository.save(versionsA.traceId, version)
        val found = repository.find(versionsA.traceId)
        found must not be (None)
        found.foreach { f ⇒
          version.toSet must be(f.toSet)
        }
      }
      versionsB.traces.take(versionsB.traces.size - 1).foreach { version ⇒
        repository.save(versionsB.traceId, version)
        val found = repository.find(versionsB.traceId)
        found must not be (None)
        found.foreach { f ⇒
          version.toSet must be(f.toSet)
        }
      }
      Thread.sleep(2000)
      repository.save(versionsA.traceId, versionsA.traces.last)
      val found1 = repository.find(versionsA.traceId)
      found1 must not be (None)
      found1.foreach { f ⇒
        versionsA.traces.last.toSet must be(f.toSet)
      }
      repository.purgeOld()
      val found2 = repository.find(versionsA.traceId)
      found2 must not be (None)
      found2.foreach { f ⇒
        versionsA.traces.last.toSet must be(f.toSet)
      }
      val found3 = repository.find(versionsB.traceId)
      found3 must be(None)
      repository.save(versionsB.traceId, versionsB.traces.last)
      val found4 = repository.find(versionsB.traceId)
      found4 must not be (None)
      found4.foreach { f ⇒
        versionsB.traces.last.toSet must be(f.toSet)
      }
      Thread.sleep(2000)
      repository.purgeOld()
      val found5 = repository.find(versionsA.traceId)
      found5 must be(None)
      val found6 = repository.find(versionsB.traceId)
      found6 must be(None)
    }
  }
}
