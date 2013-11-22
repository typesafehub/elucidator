/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import activator.analytics.common.PlayRequestSummaryGenerator
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.MustMatchers
import activator.analytics.AnalyticsSpec

object MemoryPlayRequestSummaryRepositorySpec {
  final val playEventTimeOrder: Ordering[PlayRequestSummary] = {
    implicit val timeOrdering = implicitly[Ordering[(Long, Long)]]
    timeOrdering.on((in: PlayRequestSummary) ⇒ (in.start.millis, in.start.nanoTime))
  }
}

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MemoryPlayRequestSummaryRepositorySpec extends AnalyticsSpec with MustMatchers with BeforeAndAfterEach {
  import MemoryPlayRequestSummaryRepositorySpec._

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

    "find summaries in an ascending time range" in {
      val examples = PlayRequestSummaryGenerator.genStream.take(100).toSeq
      val subset = examples.sorted(playEventTimeOrder).take(50)
      val times = subset.map(_.start.millis)
      val startTime = times.head
      val endTime = times.last
      examples.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(startTime, endTime, 0, 50, PlayStatsSorts.TimeSort, Sorting.ascendingSort)
      result.length must be(50)
      result.zip(subset).foreach { case (a, b) ⇒ a must be(b) }
    }

    "find summaries in an descending time range" in {
      val sortedOnTimeDescending = PlayRequestSummaryGenerator.genStream.take(50).toSeq.sorted(playEventTimeOrder.reverse)
      val times = sortedOnTimeDescending.map(_.start.millis)
      val startTime = times.last
      val endTime = times.head
      sortedOnTimeDescending.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(startTime, endTime, 0, 50, PlayStatsSorts.TimeSort, Sorting.descendingSort)
      result.length must be(50)
      result.zip(sortedOnTimeDescending).foreach { case (a, b) ⇒ a must be(b) }
    }

    "find summaries in an ascending time range with paging" in {
      val examples = PlayRequestSummaryGenerator.genStream.take(100).toSeq
      val subset = examples.sorted(playEventTimeOrder).take(50)
      val times = subset.map(_.start.millis)
      val startTime = times.head
      val endTime = times.last
      examples.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(startTime, endTime, 24, 50, PlayStatsSorts.TimeSort, Sorting.ascendingSort)
      val asSet = examples.toSet
      result.foreach(v ⇒ asSet(v) must be(true))
    }

    "find summaries sorted ascending on controller" in {
      val sortedOnController =
        PlayRequestSummaryGenerator.genStream.take(100).toSeq.sortWith((a, b) ⇒
          a.invocationInfo.controller < b.invocationInfo.controller)
      val sortedOnTime = sortedOnController.sorted(playEventTimeOrder)
      val times = sortedOnTime.map(_.start.millis)
      sortedOnController.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(times.head, times.last, 0, 50, PlayStatsSorts.ControllerSort, Sorting.ascendingSort)
      result.length must equal(50)
      result.zip(sortedOnController).foreach { case (a, b) ⇒ a must be(b) }
    }

    "find summaries sorted descending on controller" in {
      val sortedOnController =
        PlayRequestSummaryGenerator.genStream.take(100).toSeq.sortWith((a, b) ⇒
          a.invocationInfo.controller > b.invocationInfo.controller)
      val sortedOnTime = sortedOnController.sorted(playEventTimeOrder)
      val times = sortedOnTime.map(_.start.millis)
      sortedOnController.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(times.head, times.last, 0, 50, PlayStatsSorts.ControllerSort, Sorting.descendingSort)
      result.length must equal(50)
      result.zip(sortedOnController).foreach { case (a, b) ⇒ a must be(b) }
    }

    /*
    "find summaries sorted on invocation time in millis" in {
      def totalTimeMillis(prs: PlayRequestSummary): Long = (prs.end.nanoTime - prs.start.nanoTime) * 1000 * 1000

      val sortedOnInvocationTime =
        PlayRequestSummaryGenerator.genStream.take(50).toSeq.sortWith(
          (a, b) ⇒ totalTimeMillis(a) < totalTimeMillis(b))
      val sortedOnTime = sortedOnInvocationTime.sortWith((a, b) ⇒
        if (a.start.millis == b.start.millis) a.start.nanoTime < b.start.nanoTime
        else a.start.millis < b.start.millis)
      val times = sortedOnTime.map(_.start.millis)
      sortedOnInvocationTime.foreach(repository.save)
      val result = repository.findRequestsWithinTimePeriod(times.head, times.last, 0, 50, PlayStatsSorts.TimeSort, Sorting.descendingSort)
      result.length must equal(50)
      result.zip(sortedOnInvocationTime).foreach { case (a, b) ⇒ a must be(b) }
    }
    */
  }
}
