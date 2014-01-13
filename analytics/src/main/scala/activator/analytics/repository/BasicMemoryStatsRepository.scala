/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data.{ BasicStats, TimeRangeType, TimeRange, Scope }
import java.util.concurrent.CountDownLatch
import scala.collection.mutable.{ Map ⇒ MutableMap }
import scala.concurrent.duration._

abstract class BasicMemoryStatsCustomizer[STAT, SCOPE] {
  def scope(stat: STAT): SCOPE
  def timeRange(stat: STAT): TimeRange
  val clearOlderThan: Map[TimeRangeType.Value, Duration] = Map(
    TimeRangeType.Minutes -> 20.minutes,
    TimeRangeType.Hours -> 20.minutes,
    TimeRangeType.Days -> 20.minutes,
    TimeRangeType.Months -> 20.minutes)
  def anonymous(stat: STAT): Boolean
}

trait BasicMemoryStatsRepository[STAT, SCOPE] extends RepositoryLifecycle {
  // Register this repository with the repository handler
  LocalRepositoryLifecycleHandler.register(this)

  protected def customizer: BasicMemoryStatsCustomizer[STAT, SCOPE]

  private val byScopeByTimeRange = MutableMap[SCOPE, MutableMap[TimeRange, STAT]]()

  private var latch: Option[CountDownLatch] = None
  private var latchCondition: ((STAT) ⇒ Boolean) = _

  def save(stats: Iterable[STAT]) = synchronized {
    for (each ← stats) {
      saveOne(each)
    }
    clearOld()
  }

  def saveOne(stat: STAT): Unit = synchronized {
    val byScope = byScopeByTimeRange.getOrElseUpdate(customizer.scope(stat), MutableMap[TimeRange, STAT]())
    byScope(customizer.timeRange(stat)) = stat
    for (cdl ← latch if latchCondition(stat)) {
      cdl.countDown
    }
  }

  def findBy(timeRange: TimeRange, scope: SCOPE): Option[STAT] = synchronized {
    for {
      byScope ← byScopeByTimeRange.get(scope)
      result ← byScope.get(timeRange)
    } yield result
  }

  def findWithinTimePeriod(tRange: TimeRange, scope: SCOPE): Seq[STAT] = synchronized {
    val stats: Seq[STAT] = byScopeByTimeRange.get(scope).toSeq.flatMap(_.values)
    val matchingStats = for {
      s ← stats
      statTimeRange = customizer.timeRange(s)
      if statTimeRange.rangeType == tRange.rangeType
      if statTimeRange.startTime < tRange.endTime
      if statTimeRange.endTime > tRange.startTime
    } yield s
    matchingStats.sortBy(customizer.timeRange(_).startTime)
  }

  def getAllStats: Seq[STAT] = synchronized {
    byScopeByTimeRange.values.map(_.values).flatten.toSeq
  }

  def clear(): Unit = synchronized {
    byScopeByTimeRange.clear()
  }

  private var previousClearOldTimestamp = System.currentTimeMillis

  private def clearOld(): Unit = {
    val now = System.currentTimeMillis
    if ((now - previousClearOldTimestamp) > 60 * 1000) {
      previousClearOldTimestamp = now

      val throwAwayOld = for {
        m ← byScopeByTimeRange.values
        s ← m.values
        timeRange = customizer.timeRange(s)
        oldDuration ← customizer.clearOlderThan.get(timeRange.rangeType)
        oldTime = now - oldDuration.toMillis
        if timeRange.endTime < oldTime
      } yield s

      for (each ← throwAwayOld) byScopeByTimeRange(customizer.scope(each)).remove(customizer.timeRange(each))
    }
  }

  /**
   * For test purpose. The latch will be counted down for each TraceEvents
   * that is stored 'when' the condition is fulfilled.
   */
  def useLatch(cdl: CountDownLatch)(when: (STAT) ⇒ Boolean): Unit = synchronized {
    latch = Some(cdl)
    latchCondition = when
  }

  def withByScopeByTimeRange[T](f: (MutableMap[SCOPE, MutableMap[TimeRange, STAT]]) ⇒ T) = synchronized {
    f(byScopeByTimeRange)
  }
}

// this scope object is shared among some of the subclasses
case class SpanScope(scope: Scope, spanTypeName: String)

// a customizer that works for any stat that implements BasicStats (scope, spanTypeName, timeRange fields)
class BasicStatsCustomizer[STAT <: BasicStats] extends BasicMemoryStatsCustomizer[STAT, SpanScope] {
  override def scope(stat: STAT): SpanScope = SpanScope(stat.scope, stat.spanTypeName)
  override def timeRange(stat: STAT): TimeRange = stat.timeRange
  override def anonymous(stat: STAT): Boolean = stat.scope.anonymous
}
