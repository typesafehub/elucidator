/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.metrics

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.math.abs
import scala.util.Random

/**
 * A statistically representative sample of a data stream.
 */
trait Sample {
  /**
   * Clears all recorded values.
   */
  def clear()

  /**
   * Returns the number of values recorded.
   */
  def size: Int

  /**
   * Adds a new recorded value to the sample.
   */
  def update(value: Long)

  /**
   * Returns a copy of the sample's values.
   */
  def values: Seq[Long]
}

object UniformSample {
  private[metrics] val rnd = new Random
}

/**
 * A random sample of a stream of numbers. Uses Vitter's Algorithm R to
 * produce a statistically representative sample.
 *
 * @see <a href="http://www.cs.umd.edu/~samir/498/vitter.pdf">Random Sampling
 *      with a Reservoir</a>
 * @param reservoirSize the number of samples to keep in the sampling reservoir
 */
class UniformSample(reservoirSize: Int) extends Sample {
  private var count = 0
  // use two reservoirs to save memory, 1 will be used up to the reservoirSize and then switching over to 2
  private var reservoir1 = ArrayBuffer[Long]()
  private var reservoir2: Array[Long] = _

  override def clear() {
    count = 0
    for (i ← 0 until reservoir2.size) {
      reservoir2(i) = 0L
    }
  }

  override def size = {
    if (count > reservoirSize) reservoirSize else count
  }

  override def update(value: Long) {
    count += 1
    count match {
      case c if c < reservoirSize ⇒
        reservoir1 += value
      case c if c == reservoirSize ⇒
        reservoir1 += value
        reservoir2 = reservoir1.toArray[Long]
        reservoir2(c - 1) = value
        // now we don't use reservoir1 any more, GC
        reservoir1 = null
      case c ⇒
        val r = abs(UniformSample.rnd.nextLong % c)
        if (r < reservoir2.size) {
          reservoir2(r.toInt) = value
        }
    }
  }

  override def values = {
    if (reservoir1 ne null) {
      reservoir1.toIndexedSeq
    } else {
      reservoir2.toIndexedSeq.take(size)
    }
  }

}

/**
 * Holds the latest samples. New values are added in a "rolling" manner,
 * with new values replacing the "oldest" values in the dataset.
 */
class LatestSample(reservoirSize: Int) extends Sample {
  private var reservoir = mutable.Queue[Long]()

  override def clear() {
    reservoir.clear()
  }

  override def size = {
    reservoir.size
  }

  override def update(value: Long) {
    reservoir += value
    if (reservoir.size > reservoirSize) {
      reservoir.dequeue
    }
  }

  override def values = reservoir.toIndexedSeq

}
