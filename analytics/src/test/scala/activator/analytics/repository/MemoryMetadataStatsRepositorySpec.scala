/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data.TimeRange._
import activator.analytics.data.{ MetadataStatsMetrics, TimeRange, Scope, MetadataStats }
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.MustMatchers
import activator.analytics.AnalyticsSpec

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class MemoryMetadataStatsRepositorySpec extends AnalyticsSpec with MustMatchers with BeforeAndAfterEach {
  var repository: MemoryMetadataStatsRepository = _

  val time = System.currentTimeMillis
  val minute = minuteRange(time)
  val hour = hourRange(time)
  val allTime = TimeRange()
  val spanTypeName = "test"
  val scope1 = Scope(path = Some("akka://MemoryActorStatsRepositorySpec/user/actor1"), node = Some("node1"), tag = Some("tag1"))
  val scope2 = Scope(path = Some("akka://MemoryActorStatsRepositorySpec/user/actor2"), node = Some("node1"), tag = Some("tag2"))
  val scope3 = Scope(path = Some("akka://MemoryActorStatsRepositorySpec/user/actor3"), node = Some("node2"), tag = Some("tag1"))
  val scope4 = Scope(playPattern = Some("/path"))
  val scope5 = Scope(playController = Some("controllers.Application.index"))
  val scope6 = Scope(playPattern = Some("/path"), playController = Some("controllers.Application.index"))

  override def beforeEach() {
    repository = new MemoryMetadataStatsRepository
    repository.saveOne(MetadataStats(timeRange = minute, scope = scope1,
      metrics = MetadataStatsMetrics(paths = Set("akka://MemoryActorStatsRepositorySpec/user/actor1"))))
    repository.saveOne(MetadataStats(timeRange = minute, scope = scope2,
      metrics = MetadataStatsMetrics(paths = Set("akka://MemoryActorStatsRepositorySpec/user/actor2"))))
    repository.saveOne(MetadataStats(timeRange = minute, scope = scope3,
      metrics = MetadataStatsMetrics(paths = Set("akka://MemoryActorStatsRepositorySpec/user/actor3"))))
    repository.saveOne(MetadataStats(timeRange = minute, scope = scope4,
      metrics = MetadataStatsMetrics(playPatterns = Set("/path"))))
    repository.saveOne(MetadataStats(timeRange = minute, scope = scope5,
      metrics = MetadataStatsMetrics(playControllers = Set("controllers.Application.index"))))
    repository.saveOne(MetadataStats(timeRange = minute, scope = scope6,
      metrics = MetadataStatsMetrics(playPatterns = Set("/path"), playControllers = Set("controllers.Application.index"))))

    repository.saveOne(MetadataStats(timeRange = hour, scope = scope1,
      metrics = MetadataStatsMetrics(paths = Set("akka://MemoryActorStatsRepositorySpec/user/actor1"))))
    repository.saveOne(MetadataStats(timeRange = hour, scope = scope2,
      metrics = MetadataStatsMetrics(paths = Set("akka://MemoryActorStatsRepositorySpec/user/actor2"))))
    repository.saveOne(MetadataStats(timeRange = hour, scope = scope3,
      metrics = MetadataStatsMetrics(paths = Set("akka://MemoryActorStatsRepositorySpec/user/actor3"))))
    repository.saveOne(MetadataStats(timeRange = hour, scope = scope4,
      metrics = MetadataStatsMetrics(playPatterns = Set("/path"))))
    repository.saveOne(MetadataStats(timeRange = hour, scope = scope5,
      metrics = MetadataStatsMetrics(playControllers = Set("controllers.Application.index"))))
    repository.saveOne(MetadataStats(timeRange = hour, scope = scope6,
      metrics = MetadataStatsMetrics(playPatterns = Set("/path"), playControllers = Set("controllers.Application.index"))))

    repository.saveOne(MetadataStats(timeRange = allTime, scope = scope1,
      metrics = MetadataStatsMetrics(paths =
        Set("akka://MemoryActorStatsRepositorySpec/user/actor1",
          "akka://MemoryActorStatsRepositorySpec/user/actor1/$a",
          "akka://MemoryActorStatsRepositorySpec/temp/$a"))))
  }

  override def afterEach() {
    repository.clear()
    repository = null
  }

  "MemoryStatsRepository" must {
    "filter based on scope and time range" in {
      val r1 = repository.findFiltered(minute, Scope(), false, false)
      r1.metrics.paths.size must be(3)
      r1.metrics.playPatterns.size must be(1)
      r1.metrics.playControllers.size must be(1)

      val r2 = repository.findFiltered(hour, Scope(), false, false)
      r2.metrics.paths.size must be(3)
      r2.metrics.playPatterns.size must be(1)
      r2.metrics.playControllers.size must be(1)

      repository.findFiltered(allTime, Scope(), false, false).metrics.paths.size must be(1)
      repository.findFiltered(allTime, Scope(), includeAnonymousActors = true, includeTempActors = false).metrics.paths.size must be(2)
      repository.findFiltered(allTime, Scope(), includeAnonymousActors = true, includeTempActors = true).metrics.paths.size must be(3)
      // temp actors consist of anonymous actors
      repository.findFiltered(allTime, Scope(), includeAnonymousActors = false, includeTempActors = true).metrics.paths.size must be(1)

      val r3 = repository.findFiltered(minute, Scope(node = Some("node1")), false, false)
      r3.metrics.paths.size must be(2)
      r3.metrics.playPatterns.size must be(0)
      r3.metrics.playControllers.size must be(0)

      repository.findFiltered(minute, Scope(node = Some("node2")), false, false).metrics.paths.size must be(1)
      repository.findFiltered(minute, Scope(node = Some("node1"), tag = Some("tag1")), false, false).metrics.paths.size must be(1)
      repository.findFiltered(minute, Scope(node = Some("node2"), tag = Some("tag1")), false, false).metrics.paths.size must be(1)

      repository.findFiltered(hour, Scope(node = Some("node1")), false, false).metrics.paths.size must be(2)
      repository.findFiltered(hour, Scope(tag = Some("tag1")), false, false).metrics.paths.size must be(2)

      val r4 = repository.findFiltered(hour, Scope(playPattern = Some("/path")), false, false)
      r4.metrics.playPatterns.size must be(1)
      r4.metrics.playControllers.size must be(1)

      val r5 = repository.findFiltered(minute, Scope(playPattern = Some("/path")), false, false)
      r4.metrics.playPatterns.size must be(1)
      r4.metrics.playControllers.size must be(1)

      val r6 = repository.findFiltered(hour, Scope(playController = Some("controllers.Application.index")), false, false)
      r6.metrics.playPatterns.size must be(1)
      r6.metrics.playControllers.size must be(1)

      val r7 = repository.findFiltered(minute, Scope(playController = Some("controllers.Application.index")), false, false)
      r7.metrics.playPatterns.size must be(1)
      r7.metrics.playControllers.size must be(1)

      val r8 = repository.findFiltered(hour, Scope(playPattern = Some("/path"), playController = Some("controllers.Application.index")), false, false)
      r8.metrics.playPatterns.size must be(1)
      r8.metrics.playControllers.size must be(1)

      val r9 = repository.findFiltered(minute, Scope(playPattern = Some("/path"), playController = Some("controllers.Application.index")), false, false)
      r9.metrics.playPatterns.size must be(1)
      r9.metrics.playControllers.size must be(1)
    }
  }
}
