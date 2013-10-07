/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class UniformSampleSpec extends WordSpec with MustMatchers {

  private val population = Range(0, 1000)
  private val sample = new UniformSample(100)
  population.foreach { i ⇒ sample.update(i) }

  "An UniformSample of 100 out of 1000 elements" must {

    "have 100 elements" in {
      sample.size must be(100)
      sample.values.size must be(100)
    }

    "only have elements from the population" in {
      (sample.values.toSet -- population.toSet).size must be(0)
    }

  }

  "An unfilled UniformSample" must {

    "hold the added values" in {

      val population2 = Range(0, 10)
      val sample2 = new UniformSample(100)
      population2.foreach { i ⇒ sample2.update(i) }

      sample2.size must be(10)
      sample2.values must be(population2.toSeq)
    }

  }

}

