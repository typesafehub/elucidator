/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class PercentilesMetricsSpec extends WordSpec with MustMatchers {

  "PercentilesMetrics" must {

    "calculate correct percentiles for one value" in {
      val metrics = new PercentilesMetrics(new UniformSample(100))
      metrics += 10
      val percentiles = metrics.percentiles(List(0.25, 0.5, 0.75, 0.99))

      percentiles(0) must be(10.0 plusOrMinus 0.01)
      percentiles(1) must be(10.0 plusOrMinus 0.01)
      percentiles(2) must be(10.0 plusOrMinus 0.01)
    }

    "calculate correct percentiles for a few values" in {
      val metrics = new PercentilesMetrics(new UniformSample(100))
      metrics += 10
      metrics += 20
      metrics += 30
      metrics += 40
      metrics += 50
      val percentiles = metrics.percentiles(List(0.25, 0.5, 0.75, 0.99))

      percentiles(0) must be(15.0 plusOrMinus 0.01)
      percentiles(1) must be(30.0 plusOrMinus 0.01)
      percentiles(2) must be(45.0 plusOrMinus 0.01)
    }

    "calculate correct percentiles for many values" in {
      val metrics = new PercentilesMetrics(new UniformSample(10000))
      for (n ← 1 to 10000) {
        metrics += n
      }
      val percentiles = metrics.percentiles(List(0.5, 0.75, 0.99))

      percentiles(0) must be(5000.5 plusOrMinus 0.01)
      percentiles(1) must be(7500.75 plusOrMinus 0.01)
      percentiles(2) must be(9900.99 plusOrMinus 0.01)
    }

    "use -1 when no values" in {
      val metrics = new PercentilesMetrics(new UniformSample(100))
      val percentiles = metrics.percentiles(List(0.25, 0.5, 0.75, 0.99))

      percentiles(0) must be(-1.0 plusOrMinus 0.01)
      percentiles(1) must be(-1.0 plusOrMinus 0.01)
      percentiles(2) must be(-1.0 plusOrMinus 0.01)
    }

  }

}

