/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.TimeRange.{ dayRange, hourRange, minuteRange, monthRange }
import activator.analytics.data.TimeRangeType.{ Days, Hours, Minutes, Months }
import com.typesafe.atmos.util.UtcDateFormat
import java.util.Date
import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class TimeRangeSpec extends WordSpec with MustMatchers {
  val timestampFormat = new UtcDateFormat("yyyy-MM-dd HH:mm:ss:SSS")

  "TimeRange" must {

    "give me start and end time of a minute range for a specific point in time" in {

      val time = timestampFormat.parse("2011-05-25 13:09:45:666")
      val range = minuteRange(time.getTime)
      timestampFormat.format(new Date(range.startTime)) must be("2011-05-25 13:09:00:000")
      timestampFormat.format(new Date(range.endTime)) must be("2011-05-25 13:09:59:999")
      range.rangeType must be(Minutes)
    }

    "give me start and end time of a hour range for a specific point in time" in {

      val time = timestampFormat.parse("2011-05-25 13:09:45:666")
      val range = hourRange(time.getTime)
      timestampFormat.format(new Date(range.startTime)) must be("2011-05-25 13:00:00:000")
      timestampFormat.format(new Date(range.endTime)) must be("2011-05-25 13:59:59:999")
      range.rangeType must be(Hours)
    }

    "give me start and end time of a day range for a specific point in time" in {
      val time = timestampFormat.parse("2010-12-31 02:45:59:777")
      val range = dayRange(time.getTime)
      timestampFormat.format(new Date(range.startTime)) must be("2010-12-31 00:00:00:000")
      timestampFormat.format(new Date(range.endTime)) must be("2010-12-31 23:59:59:999")
      range.rangeType must be(Days)
    }

    "give me start and end time of a month range for a specific point in time" in {
      val time = timestampFormat.parse("2011-01-31 23:59:59:999")
      val range = monthRange(time.getTime)
      timestampFormat.format(new Date(range.startTime)) must be("2011-01-01 00:00:00:000")
      timestampFormat.format(new Date(range.endTime)) must be("2011-01-31 23:59:59:999")
      range.rangeType must be(Months)
    }

    "give me day range spanning over several days" in {
      val start = timestampFormat.parse("2011-01-31 02:00:01:002")
      val end = timestampFormat.parse("2011-02-02 14:59:59:999")
      val range = dayRange(start.getTime, end.getTime)
      timestampFormat.format(new Date(range.startTime)) must be("2011-01-31 00:00:00:000")
      timestampFormat.format(new Date(range.endTime)) must be("2011-02-02 23:59:59:999")
      range.rangeType must be(Days)
    }

  }
}
