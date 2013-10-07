/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import activator.analytics.data.{ TimeSeriesPoint, TimeSeries }

trait TimeSeriesSampling[P <: TimeSeriesPoint] {

  def configMaxPoints: Int

  def sample[T <: TimeSeries[P]](timeSeries: T, sampling: Option[Int], maxPoints: Option[Int]): T = {
    val adjustedSampling = {
      val size = timeSeries.points.size / sampling.getOrElse(1)
      val max = math.min(configMaxPoints, maxPoints.getOrElse(Int.MaxValue))
      if (size > max) {
        Some(math.ceil(timeSeries.points.size.toDouble / max).toInt)
      } else {
        sampling
      }
    }

    adjustedSampling match {
      case None    ⇒ timeSeries
      case Some(1) ⇒ timeSeries
      case Some(samplingFactor) ⇒
        val sampledPoints =
          for {
            (point, i) ← timeSeries.points.zipWithIndex
            if (i % samplingFactor == 0)
          } yield sampledPoint(point, samplingFactor)
        // TODO is it possible to avoid asInstanceOf with some better type parameters?
        timeSeries.withPoints(sampledPoints).asInstanceOf[T]
    }
  }

  def sampledPoint(point: P, adjustedSamplingFactor: Int): P = point

}
