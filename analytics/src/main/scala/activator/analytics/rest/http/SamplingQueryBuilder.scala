/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

trait SamplingQueryBuilder {
  def extractSampling(queryParams: String): Option[Int] = {
    queryParams match {
      case SamplingQueryBuilder.SamplingPattern(sampling) ⇒ Some(sampling.toInt)
      case _ ⇒ None
    }
  }

  def extractMaxPoints(queryParams: String): Option[Int] = {
    queryParams match {
      case SamplingQueryBuilder.MaxPointsPattern(maxPoints) ⇒ Some(maxPoints.toInt)
      case _ ⇒ None
    }
  }
}

object SamplingQueryBuilder {
  val SamplingPattern = """^.*sampling=([1-9][0-9]*)&?.*?""".r
  val MaxPointsPattern = """^.*maxPoints=([1-9][0-9]*)&?.*?""".r
}
