/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import activator.analytics.data.BasicTypes.{ DurationNanos, Timestamp }
import activator.analytics.metrics.PairMetric
import java.util.concurrent.TimeUnit
import scala.collection.mutable.{ Map ⇒ MutableMap }

case class MailboxKey(node: String, actorSystem: String, path: String)

class Mailbox(previousPairMetricsHistory: Map[MailboxKey, Iterable[PairMetric.Single]]) {

  // by actor uuid
  private val pairMetrics = MutableMap[MailboxKey, PairMetric]()

  def pairMetricsFor(key: MailboxKey): PairMetric = {
    pairMetrics.getOrElseUpdate(key, {
      new PairMetric(initialValues = previousPairMetricsHistory.getOrElse(key, Seq.empty))
    })
  }

  def reset(key: MailboxKey) {
    pairMetricsFor(key).reset()
  }

  def resetAll(node: String, actorSystem: String) = {
    (for {
      key ← pairMetrics.keys
      if key.node == node
      if (key.actorSystem == actorSystem)
    } yield key).foreach(
      key ⇒ pairMetrics(key).reset())
  }

  def maxMailboxSize: (Int, Timestamp, MailboxKey) = {
    if (pairMetrics.isEmpty) {
      (0, 0L, MailboxKey("", "", ""))
    } else {
      val (mKey, m) = pairMetrics.maxBy(entry ⇒ entry._2.maxCount)
      (m.maxCount, m.maxCountTimestamp, mKey)
    }
  }

  def maxTimeInMailbox: (DurationNanos, Timestamp, MailboxKey) = {
    if (pairMetrics.isEmpty) {
      (0L, 0L, MailboxKey("", "", ""))
    } else {
      val (mKey, m) = pairMetrics.maxBy(entry ⇒ entry._2.maxDuration)
      (m.maxDuration, m.maxDurationTimestamp, mKey)
    }
  }

  def mailboxSize = {
    if (pairMetrics.isEmpty) 0 else pairMetrics.values.map(_.count).max
  }

  def timeInMailbox: DurationNanos = {
    if (pairMetrics.isEmpty) 0L else pairMetrics.values.map(_.previousDuration).max
  }

  def numberOfIndividualMailboxes: Int = pairMetrics.size

  def toMap: Map[MailboxKey, PairMetric] = pairMetrics.toMap

  def clearOld(latestMessageTimestamp: Timestamp) {
    val oldTime = latestMessageTimestamp - TimeUnit.MILLISECONDS.convert(10, TimeUnit.SECONDS)
    val keys = for {
      key ← pairMetrics.keys
      if pairMetrics(key).previousDurationTimestamp < oldTime
    } yield key

    pairMetrics --= keys
  }
}
