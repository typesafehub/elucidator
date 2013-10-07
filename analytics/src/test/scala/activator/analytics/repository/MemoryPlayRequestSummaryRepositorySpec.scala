/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import activator.analytics.common.PlayRequestSummaryGenerator
import com.typesafe.atmos.trace._
import com.typesafe.atmos.util._
import com.typesafe.atmos.util.AtmosSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.MustMatchers

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MemoryPlayRequestSummaryRepositorySpec extends AtmosSpec with MustMatchers with BeforeAndAfterEach {
  val one = PlayRequestSummaryGenerator.generate
  val two = PlayRequestSummaryGenerator.generate

  var repository: MemoryPlayRequestSummaryRepository = _

  override def beforeEach() {
    repository = new MemoryPlayRequestSummaryRepository(1000)
  }

  override def afterEach() {
    repository = null
  }

  "A MemoryPlayRequestSummaryRepository" must {
    "not retrieve summaries that aren't there" in {
      val found = repository.find(one.traceId)
      found must be(None)
    }
    "store and retrieve Play Request Summaries" in {
      val examples = PlayRequestSummaryGenerator.genStream.take(100).toSeq
      examples.foreach(repository.save)
      examples.foreach { s ⇒
        val found = repository.find(s.traceId)
        found must be(Some(s))
      }
    }
    "purge old versions Play Request Summaries" in {
      val examples = PlayRequestSummaryGenerator.genStream.take(100).toSeq
      val versions = Seq(examples.take(10), examples.take(20), examples.take(100))
      versions.foreach { version ⇒
        version.foreach(repository.save)
        version.foreach { s ⇒
          val found = repository.find(s.traceId)
          found must be(Some(s))
        }
      }
      Thread.sleep(2000)
      repository.save(one)
      val found1 = repository.find(one.traceId)
      found1 must be(Some(one))
      repository.purgeOld()
      val found2 = repository.find(two.traceId)
      found2 must be(None)
      Thread.sleep(2000)
      repository.purgeOld()
      val found3 = repository.find(one.traceId)
      found3 must be(None)
    }
    "find summaries in a time range" in {
      val examples = PlayRequestSummaryGenerator.genStream.take(100).toSeq
      val subset = PlayRequestSummary.sortByTime(examples).take(50)
      val times = subset.map(_.start.millis)
      val startTime = times.head
      val endTime = times.last
      examples.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(startTime, endTime, 1, 50)
      result.length must be(50)
      result.zip(subset).foreach { case (a, b) ⇒ a must be(b) }
    }
    "find summaries in a time range with paging" in {
      val examples = PlayRequestSummaryGenerator.genStream.take(100).toSeq
      val subset = PlayRequestSummary.sortByTime(examples).take(50)
      val times = subset.map(_.start.millis)
      val startTime = times.head
      val endTime = times.last
      examples.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(startTime, endTime, 25, 50)
      (result.length >= 25) must be(true)
      val asSet = examples.toSet
      result.foreach(v ⇒ asSet(v) must be(true))
    }
  }
}
