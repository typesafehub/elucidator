/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

trait ChunkQueryBuilder {
  def extractNumberOfChunks(path: String): Option[(Int, Int)] = {
    val minChunks = path match {
      case ChunkQueryBuilder.MinChunksPattern(min) ⇒ Some(min.toInt)
      case _                                       ⇒ None
    }

    val maxChunks = path match {
      case ChunkQueryBuilder.MaxChunksPattern(max) ⇒ Some(max.toInt)
      case _                                       ⇒ None
    }

    for {
      min ← minChunks
      max ← maxChunks
    } yield (min, max)
  }
}

object ChunkQueryBuilder {
  val MinChunksPattern = """^.*minChunks=([1-9][0-9]?).*""".r
  val MaxChunksPattern = """^.*maxChunks=([1-9][0-9]?).*""".r
}

