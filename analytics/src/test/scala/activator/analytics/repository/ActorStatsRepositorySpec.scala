/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import com.typesafe.atmos.util.AtmosSpec
import org.scalatest.matchers.MustMatchers
import activator.analytics.data.{ ActorStatsSorts, Scope, TimeRange, ActorStats }

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ActorStatsRepositorySpec extends AtmosSpec with MustMatchers {
  val repo = new MemoryActorStatsRepository

  "ActorStatsRepo must" must {
    "sort on actor name and path" in {
      val now = System.currentTimeMillis
      val stats = Seq(
        ActorStats(TimeRange.minuteRange(now), Scope(path = Some("akka://AS/user/B/AB"))),
        ActorStats(TimeRange.minuteRange(now), Scope(path = Some("akka://AS/user/A1/A1"))),
        ActorStats(TimeRange.minuteRange(now), Scope(path = Some("akka://AS/user/A/A"))),
        ActorStats(TimeRange.minuteRange(now), Scope(path = Some("akka://AS/user/A/Z"))),
        ActorStats(TimeRange.minuteRange(now), Scope(path = Some("akka://AS/user/A/$A"))),
        ActorStats(TimeRange.minuteRange(now), Scope(path = Some("akka://AS/user/A/$Z"))))
      repo.save(stats)

      val sortedName = repo.findSorted(
        timeRange = TimeRange.minuteRange(now),
        scope = Scope(),
        sortOn = ActorStatsSorts.ActorName,
        includeAnonymous = true,
        includeTemp = false,
        limit = 100)

      sortedName.stats.size must equal(6)
      sortedName.stats(0).scope.path.get must equal("akka://AS/user/A/A")
      sortedName.stats(1).scope.path.get must equal("akka://AS/user/A1/A1")
      sortedName.stats(2).scope.path.get must equal("akka://AS/user/B/AB")
      sortedName.stats(3).scope.path.get must equal("akka://AS/user/A/Z")
      sortedName.stats(4).scope.path.get must equal("akka://AS/user/A/$A")
      sortedName.stats(5).scope.path.get must equal("akka://AS/user/A/$Z")

      val sortedPath = repo.findSorted(
        timeRange = TimeRange.minuteRange(now),
        scope = Scope(),
        sortOn = ActorStatsSorts.ActorPath,
        includeAnonymous = true,
        includeTemp = false,
        limit = 100)

      sortedPath.stats.size must equal(6)
      sortedPath.stats(0).scope.path.get must equal("akka://AS/user/A/A")
      sortedPath.stats(1).scope.path.get must equal("akka://AS/user/A/Z")
      sortedPath.stats(2).scope.path.get must equal("akka://AS/user/A1/A1")
      sortedPath.stats(3).scope.path.get must equal("akka://AS/user/B/AB")
      sortedPath.stats(4).scope.path.get must equal("akka://AS/user/A/$A")
      sortedPath.stats(5).scope.path.get must equal("akka://AS/user/A/$Z")
    }
  }
}
