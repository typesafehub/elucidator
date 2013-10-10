/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.TimeRange.minuteRange
import com.typesafe.trace.util.UtcDateFormat
import java.util.Date
import org.junit.runner.RunWith
import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec
import scala.concurrent.duration._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ChunkSpec extends WordSpec with MustMatchers {

  val OneMinute = 1.minute
  val timestampFormat = new UtcDateFormat("yyyy-MM-dd HH:mm:ss:SSS")
  val scope = Scope()
  val spanTypeName = "message"

  def summarySpanStats(timeRange: TimeRange, n: Long) =
    SummarySpanStats(timeRange, scope, spanTypeName, n)

  "Chunker" must {

    "give exact chunks for matching periods" in {
      val start = timestampFormat.parse("2012-03-21 14:20:00:000")
      val end = timestampFormat.parse("2012-03-21 14:22:00:00")
      val range = minuteRange(start.getTime, end.getTime)

      val stats = for (i ← 0 to 2) yield summarySpanStats(minuteRange(start.getTime + (i * OneMinute).toMillis), 3 + (i * 2))

      val chunks = SummarySpanStats.chunk(3, 3, stats, range, scope, spanTypeName)
      chunks.size must be(3)
      chunks(0).n must be(3)
      chunks(1).n must be(5)
      chunks(2).n must be(7)
    }

    "give empty slots for missing periods" in {
      val start = timestampFormat.parse("2012-03-21 14:18:00:000")
      val end = timestampFormat.parse("2012-03-21 14:24:00:00")
      val range = minuteRange(start.getTime, end.getTime)

      val stats = Seq(
        summarySpanStats(minuteRange(start.getTime + (2 * OneMinute).toMillis), 3),
        summarySpanStats(minuteRange(start.getTime + (3 * OneMinute).toMillis), 5),
        summarySpanStats(minuteRange(start.getTime + (5 * OneMinute).toMillis), 7))

      val chunks = SummarySpanStats.chunk(7, 7, stats, range, scope, spanTypeName)
      chunks.size must be(7)
      chunks(0).n must be(0)
      chunks(1).n must be(0)
      chunks(2).n must be(3)
      chunks(3).n must be(5)
      chunks(4).n must be(0)
      chunks(5).n must be(7)
      chunks(6).n must be(0)
    }

    "split even divisable periods" in {
      val start = timestampFormat.parse("2012-03-21 14:20:00:000")
      val end = timestampFormat.parse("2012-03-21 14:23:00:00")
      val range = minuteRange(start.getTime, end.getTime)

      val stats = for (i ← 0 to 3) yield summarySpanStats(minuteRange(start.getTime + (i * OneMinute).toMillis), 3 + (i * 2))

      val chunks = SummarySpanStats.chunk(2, 2, stats, range, scope, spanTypeName)
      chunks.size must be(2)
      chunks(0).n must be(8)
      chunks(1).n must be(16)
    }

    "drop oldest when splitting not even divisable periods" in {
      val start = timestampFormat.parse("2012-03-21 14:20:00:000")
      val end = timestampFormat.parse("2012-03-21 14:24:00:00")
      val range = minuteRange(start.getTime, end.getTime)

      val stats = for (i ← 0 to 4) yield summarySpanStats(minuteRange(start.getTime + (i * OneMinute).toMillis), 3 + (i * 2))

      val chunks = SummarySpanStats.chunk(2, 2, stats, range, scope, spanTypeName)
      chunks.size must be(2)
      chunks(0).n must be(12)
      chunks(1).n must be(20)
    }

    "use best number of chunks" in {
      SummarySpanStats.bestNumberOfChunks(3, 6, 45) must be(5)
    }

    "use most number of chunks when ambiguous" in {
      SummarySpanStats.bestNumberOfChunks(2, 12, 10) must be(10)
    }

  }
}
