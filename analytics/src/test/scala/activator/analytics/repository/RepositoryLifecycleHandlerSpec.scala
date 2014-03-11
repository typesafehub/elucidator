/**
 * Copyright (C) 2011-2014 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import org.scalatest.matchers.MustMatchers
import activator.analytics.data._
import activator.analytics.AnalyticsSpec
import activator.analytics.data.TimeRange._

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class RepositoryLifecycleHandlerSpec extends AnalyticsSpec with MustMatchers {
  val lifecycleHandler = LocalRepositoryLifecycleHandler
  val actorRepo = new MemoryActorStatsRepository
  val metadataRepo = new MemoryMetadataStatsRepository

  "RepositoryLifecycleHandler" must {
    "clear all repositories when cleared is invoked" in {
      val now = System.currentTimeMillis
      actorRepo.save(Seq(ActorStats(TimeRange.minuteRange(now), Scope())))

      metadataRepo.saveOne(MetadataStats(timeRange = minuteRange(now), scope = Scope(),
        metrics = MetadataStatsMetrics(paths = Set("akka://MemoryActorStatsRepositorySpec/user/actor1"))))

      // Verify data existence
      metadataRepo.findFiltered(minuteRange(now), Scope(), false, false).metrics.paths.size must be > 0
      actorRepo.findWithinTimePeriod(minuteRange(now), Scope()).size must be > 0

      // Clear repository data
      lifecycleHandler.clear()

      // Verify that data has been removed
      metadataRepo.findFiltered(minuteRange(now), Scope(), false, false).metrics.paths.size must equal(0)
      actorRepo.findWithinTimePeriod(minuteRange(now), Scope()).size must equal(0)
    }

    "reset time when clear is invoked" in {
      val startTime1 = lifecycleHandler.startTime
      Thread.sleep(100)
      lifecycleHandler.clear()
      val startTime2 = lifecycleHandler.startTime
      startTime2 > startTime1 must be(true)
    }
  }
}
