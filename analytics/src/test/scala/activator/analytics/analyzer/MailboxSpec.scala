/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import activator.analytics.data.{ MailboxKey, Mailbox }
import activator.analytics.metrics.PairMetric
import com.typesafe.atmos.util.Uuid
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import org.scalatest.matchers.MustMatchers
import org.scalatest.WordSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MailboxSpec extends WordSpec with MustMatchers {

  val actorSystemName = "MailboxSpec"

  "Mailbox" must {
    "reset by node" in {
      val mailbox = new Mailbox(Map.empty)
      val node1 = "Node1"
      val node2 = "Node2"
      for (n ← 1 to 1000) {
        val m = mailbox.pairMetricsFor(MailboxKey(node1, actorSystemName, "path" + n))
        m.add(PairMetric.Single(Uuid(), expectedFirst = true, System.currentTimeMillis, System.nanoTime, 1))
      }
      for (n ← 1 to 1234) {
        val m = mailbox.pairMetricsFor(MailboxKey(node2, actorSystemName, "path" + n))
        m.add(PairMetric.Single(Uuid(), expectedFirst = true, System.currentTimeMillis, System.nanoTime, 1))
      }

      mailbox.numberOfIndividualMailboxes must be(2234)
      mailbox.resetAll(node2, actorSystemName)

      mailbox.toMap.keys.foreach(k ⇒ {
        val m = mailbox.pairMetricsFor(k)
        if (k.node == node1) {
          m.count must equal(1)
        } else {
          m.count must equal(0)
        }
      })

      mailbox.resetAll(node1, actorSystemName)
      mailbox.toMap.keys.foreach(k ⇒ mailbox.pairMetricsFor(k).count must equal(0))

    }

    "clear old mailboxes" in {
      val mailbox = new Mailbox(Map.empty)
      for (n ← 1 to 2000) {
        val path = "path" + n
        val metrics = mailbox.pairMetricsFor(MailboxKey("node1", "MailboxSpec", path))
        val id = Uuid()
        metrics.add(PairMetric.Single(id, expectedFirst = true, System.currentTimeMillis, System.nanoTime, 1))
        metrics.add(PairMetric.Single(id, expectedFirst = false, System.currentTimeMillis, System.nanoTime, 1))
      }
      mailbox.numberOfIndividualMailboxes must be(2000)
      mailbox.clearOld(System.currentTimeMillis + MILLISECONDS.convert(62, SECONDS))
      mailbox.numberOfIndividualMailboxes must be(0)
    }
  }
}
