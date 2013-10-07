/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import java.lang.NumberFormatException
import scala.util.control.Exception.catching

trait PagingQueryBuilder {
  import PagingQueryBuilder._

  def extractOffset(query: String): Option[Int] = query match {
    case OffsetPattern(offset) ⇒ parseInt(offset)
    case _                     ⇒ None
  }

  def extractLimit(query: String): Option[Int] = query match {
    case LimitPattern(limit) ⇒ parseInt(limit)
    case _                   ⇒ None
  }

  def parseInt(s: String): Option[Int] = {
    catching(classOf[NumberFormatException]) opt s.toInt
  }
}

object PagingQueryBuilder {
  val OffsetPattern = """^.*offset=([0-9]+).*""".r
  val LimitPattern = """^.*limit=([0-9]+).*""".r
}
