/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

trait TimestampQueryBuilder {
  def extractTimestamp(query: String): Option[Long] = query match {
    case TimestampQueryBuilder.TimestampPattern(time) ⇒ Some(time.toLong)
    case _ ⇒ None
  }
}

object TimestampQueryBuilder {
  val TimestampPattern = """^.*from=([0-9]+$)""".r
}
