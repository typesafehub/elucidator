/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import activator.analytics.data.TimeRange._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.MustMatchers
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MemorySummarySpanStatsRepositorySpec extends AnalyticsSpec with MustMatchers with BeforeAndAfterEach {

  var repository: MemorySummarySpanStatsRepository = _
  val time = System.currentTimeMillis
  val minute = minuteRange(time)
  val hour = hourRange(time)
  val allTime = TimeRange()
  val node = Some("node1")
  val spanTypeName = "test"
  val scope1 = Scope()
  val scope2 = Scope(node = node)

  override def beforeEach() {
    repository = new MemorySummarySpanStatsRepository

    repository.saveOne(SummarySpanStats(minute, scope1, spanTypeName, 1))
    repository.saveOne(SummarySpanStats(minute, scope2, spanTypeName, 2))
    repository.saveOne(SummarySpanStats(hour, scope1, spanTypeName, 3))
    repository.saveOne(SummarySpanStats(hour, scope2, spanTypeName, 4))
    repository.saveOne(SummarySpanStats(allTime, scope1, spanTypeName, 5))
    repository.saveOne(SummarySpanStats(allTime, scope2, spanTypeName, 6))

  }

  override def afterEach() {
    repository.clear()
    repository = null
  }

  "MemorySummarySpanStatsRepository" must {

    "find exact match" in {
      repository.findBy(minute, scope1, spanTypeName).get.n must be(1)
      repository.findBy(minute, scope2, spanTypeName).get.n must be(2)
      repository.findBy(allTime, scope1, spanTypeName).get.n must be(5)
      repository.findBy(allTime, scope2, spanTypeName).get.n must be(6)
      repository.findBy(allTime, scope2, MessageSpan.name) must be(None)
    }

    "find within rolling 2 minute" in {
      val rolling2minutes = TimeRange.minuteRange(time, 2)
      val result = repository.findWithinTimePeriod(rolling2minutes, scope1, spanTypeName)
      result.size must be(1)
      result.head.n must be(1)
    }

    "find within rolling 3 hours" in {
      val rolling3hours = TimeRange.hourRange(time, 3)
      val result = repository.findWithinTimePeriod(rolling3hours, scope2, spanTypeName)
      result.size must be(1)
      result.head.n must be(4)
    }
  }
}
