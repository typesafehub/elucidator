/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

trait PointInTimeQueryBuilder {
  import PointInTimeQueryBuilder._

  def extractPointInTime(queryParams: String): Either[String, Long] = queryParams match {
    case PointInTimePattern(point) ⇒ TimeParser.parse(point) match {
      case None       ⇒ Left("Could not parse time parameter [%s]".format(point))
      case Some(time) ⇒ Right(time)
    }
    case _ ⇒ Right(System.currentTimeMillis())
  }
}

object PointInTimeQueryBuilder {
  val PointInTimePattern = """^.*time=([0-9T\-\:]*).*""".r
}
