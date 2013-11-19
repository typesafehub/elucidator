/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import org.scalatest.matchers.MustMatchers
import activator.analytics.data._
import activator.analytics.AnalyticsSpec
import scala.Some

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class ActorStatsRepositorySpec extends AnalyticsSpec with MustMatchers {
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

      val sortedDescName = repo.findSorted(
        timeRange = TimeRange.minuteRange(now),
        scope = Scope(),
        includeAnonymous = true,
        includeTemp = false,
        limit = 100,
        sortOn = ActorStatsSorts.ActorName,
        sortDirection = Sorting.descendingSort)

      sortedDescName.stats.size must equal(6)
      sortedDescName.stats(0).scope.path.get must equal("akka://AS/user/A/A")
      sortedDescName.stats(1).scope.path.get must equal("akka://AS/user/A1/A1")
      sortedDescName.stats(2).scope.path.get must equal("akka://AS/user/B/AB")
      sortedDescName.stats(3).scope.path.get must equal("akka://AS/user/A/Z")
      sortedDescName.stats(4).scope.path.get must equal("akka://AS/user/A/$A")
      sortedDescName.stats(5).scope.path.get must equal("akka://AS/user/A/$Z")

      val sortedAscName = repo.findSorted(
        timeRange = TimeRange.minuteRange(now),
        scope = Scope(),
        includeAnonymous = true,
        includeTemp = false,
        limit = 100,
        sortOn = ActorStatsSorts.ActorName,
        sortDirection = Sorting.ascendingSort)

      sortedAscName.stats.size must equal(6)
      sortedAscName.stats(0).scope.path.get must equal("akka://AS/user/A/$Z")
      sortedAscName.stats(1).scope.path.get must equal("akka://AS/user/A/$A")
      sortedAscName.stats(2).scope.path.get must equal("akka://AS/user/A/Z")
      sortedAscName.stats(3).scope.path.get must equal("akka://AS/user/B/AB")
      sortedAscName.stats(4).scope.path.get must equal("akka://AS/user/A1/A1")
      sortedAscName.stats(5).scope.path.get must equal("akka://AS/user/A/A")

      val sortedPath = repo.findSorted(
        timeRange = TimeRange.minuteRange(now),
        scope = Scope(),
        includeAnonymous = true,
        includeTemp = false,
        limit = 100,
        sortOn = ActorStatsSorts.ActorPath,
        sortDirection = Sorting.descendingSort)

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
