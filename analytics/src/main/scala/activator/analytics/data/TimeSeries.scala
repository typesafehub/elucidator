/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

trait TimeSeries[P <: TimeSeriesPoint] {
  def points: IndexedSeq[P]
  def withPoints(points: IndexedSeq[P]): TimeSeries[P]
}

trait TimeSeriesPoint {
  def timestamp: BasicTypes.Timestamp
}
