/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import com.typesafe.atmos.util.ThreadUtcDateFormat
import com.typesafe.atmos.util.UtcDateFormat
import java.text.ParseException
import java.util.Date

object TimeParser {
  private val OutputFormatPattern = "yyyy-MM-dd'T'HH:mm:ss:SSS"
  private val outputFormat = ThreadUtcDateFormat(TimeParser.OutputFormatPattern)
  private val timeFormatters = Map(
    23 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS"),
    19 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm:ss"),
    16 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH:mm"),
    13 -> ThreadUtcDateFormat("yyyy-MM-dd'T'HH"),
    10 -> ThreadUtcDateFormat("yyyy-MM-dd"),
    7 -> ThreadUtcDateFormat("yyyy-MM"))

  def parse(dateString: String): Option[Long] = {

    def tryParse(formatter: UtcDateFormat): Option[Long] = {
      try {
        Some(formatter.parse(dateString).getTime)
      } catch {
        case e: ParseException        ⇒ None
        case e: NumberFormatException ⇒ None
      }
    }

    timeFormatters.get(dateString.length).flatMap(x ⇒ tryParse(x.get))
  }

  def format(date: Date): String = {
    outputFormat.get.format(date)
  }

  def format(timestamp: Long): String = {
    outputFormat.get.format(new Date(timestamp))
  }
}

