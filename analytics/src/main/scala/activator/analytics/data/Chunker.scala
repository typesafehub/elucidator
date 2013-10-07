/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

/**
 * Partitions number of stats into equal size groups, and concatenate
 * each group.
 */
trait Chunker[A] {

  def chunk(
    minNumberOfChunks: Int,
    maxNumberOfChunks: Int,
    stats: Iterable[A],
    timeRange: TimeRange,
    statsTimeRange: A ⇒ TimeRange,
    empty: TimeRange ⇒ A,
    concat: (TimeRange, Iterable[A]) ⇒ A): Seq[A] = {

    assert(timeRange.rangeType != TimeRangeType.AllTime)

    val statsByTime = Map.empty[Long, A] ++
      stats.map(s ⇒ (statsTimeRange(s).startTime -> s))
    val ranges = split(timeRange)
    val numberOfChunks = bestNumberOfChunks(minNumberOfChunks, maxNumberOfChunks, ranges.size)
    if (numberOfChunks >= ranges.size) {
      for (t ← ranges) yield statsByTime.get(t.startTime).getOrElse(empty(t))
    } else {
      val groupedRanges = ranges.drop(ranges.size % numberOfChunks).grouped(ranges.size / numberOfChunks).toIndexedSeq
      for (chunkRanges ← groupedRanges) yield {
        val groupTimeRange = TimeRange(chunkRanges.head.startTime, chunkRanges.last.endTime, timeRange.rangeType)
        val chunkStats = for (t ← chunkRanges) yield statsByTime.get(t.startTime).getOrElse(empty(t))
        concat(groupTimeRange, chunkStats)
      }
    }
  }

  def bestNumberOfChunks(minNumberOfChunks: Int, maxNumberOfChunks: Int, size: Int): Int = {
    val mods = for (n ← minNumberOfChunks to maxNumberOfChunks) yield (n, size % n)
    // find smallest modulo
    val bestMod = mods.minBy(_._2)._2
    // pick the largest of the best
    mods.filter(_._2 == bestMod).maxBy(_._1)._1
  }

  private def split(timeRange: TimeRange): IndexedSeq[TimeRange] = {
    val end = timeRange.endTime
    var result = IndexedSeq.empty[TimeRange]
    var t = TimeRange.rangeFor(timeRange.startTime, timeRange.rangeType)
    while (t.startTime < end) {
      result :+= TimeRange.rangeFor(t.startTime, timeRange.rangeType)
      t = next(t)
    }
    result
  }

  private def next(timeRange: TimeRange): TimeRange =
    TimeRange.rangeFor(timeRange.endTime + 1, timeRange.rangeType)
}
