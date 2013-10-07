/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import com.typesafe.atmos.util.AtmosSpec
import com.typesafe.atmos.uuid.UUID
import org.scalatest.matchers.MustMatchers

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class DuplicatesRepositoryCacheSpec extends AtmosSpec with MustMatchers {
  val repo = new DuplicatesRepositoryCache(system)

  "DuplicatesRepository" must {
    "cache and store keys and values" in {
      val analyzer1 = "a1"
      val analyzer2 = "a2"
      val uuid1 = new UUID
      val uuid2 = new UUID
      val uuid3 = new UUID
      val traceKey1 = DuplicatesTraceKey(analyzer1, uuid1)
      val traceKey2 = DuplicatesTraceKey(analyzer2, uuid2)
      val traceKey3 = DuplicatesTraceKey(analyzer1, uuid2)
      val spanKey1 = DuplicatesSpanKey(analyzer1, "span1", uuid1, uuid2)
      val spanKey2 = DuplicatesSpanKey(analyzer2, "span1", uuid1, uuid2)
      val spanKey3 = DuplicatesSpanKey(analyzer2, "span2", uuid1, uuid2)
      val spanKey4 = DuplicatesSpanKey(analyzer2, "span2", uuid1, uuid3)

      repo.contains(traceKey1) must be(false)
      repo.contains(traceKey2) must be(false)
      repo.contains(traceKey3) must be(false)
      repo.contains(spanKey1) must be(false)
      repo.contains(spanKey2) must be(false)
      repo.contains(spanKey3) must be(false)
      repo.contains(spanKey4) must be(false)

      repo.store(traceKey1)
      repo.store(traceKey3)
      repo.store(spanKey1)
      repo.store(spanKey4)

      repo.contains(traceKey1) must be(true)
      repo.contains(traceKey2) must be(false)
      repo.contains(traceKey3) must be(true)
      repo.contains(spanKey1) must be(true)
      repo.contains(spanKey2) must be(false)
      repo.contains(spanKey3) must be(false)
      repo.contains(spanKey4) must be(true)

      repo.store(traceKey2)
      repo.store(spanKey2)
      repo.store(spanKey3)

      repo.contains(traceKey2) must be(true)
      repo.contains(spanKey1) must be(true)
      repo.contains(spanKey2) must be(true)
      repo.contains(spanKey3) must be(true)
    }
  }
}
