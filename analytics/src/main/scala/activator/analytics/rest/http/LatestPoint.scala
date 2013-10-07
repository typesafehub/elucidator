/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import activator.analytics.data.{ TimeSeriesPoint, TimeSeries }

trait LatestPoint {

  def getLatestPoint[A <: TimeSeries[B], B <: TimeSeriesPoint](pointInMillis: Long, timeSeriesResult: Option[A]): Option[B] = timeSeriesResult match {
    case None ⇒ None
    case Some(timeSeries) ⇒
      if (timeSeries.points.isEmpty) {
        None
      } else {
        // Reverse in order to find == before <
        timeSeries.points.reverse.find(_.timestamp <= pointInMillis) match {
          case some @ Some(_) ⇒ some
          case None           ⇒ None
        }
      }
  }

}
