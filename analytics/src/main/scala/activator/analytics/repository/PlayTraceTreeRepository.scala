/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data._
import com.typesafe.atmos.uuid.UUID
import com.typesafe.atmos.trace.TraceEvent

trait PlayTraceTreeRepository {
  def save(trace: UUID, batch: Seq[TraceEvent]): Unit
  def find(trace: UUID): Option[Seq[TraceEvent]]
  def purgeOld(): Unit
}

class MemoryPlayTraceTreeRepository(maxAge: Long) extends PlayTraceTreeRepository {
  import java.util.concurrent.ConcurrentHashMap
  import scala.collection.JavaConversions._

  case class Value(traces: Seq[TraceEvent], timestamp: Long = System.currentTimeMillis)

  private val internalMap = new ConcurrentHashMap[UUID, Value]

  def save(trace: UUID, batch: Seq[TraceEvent]): Unit =
    internalMap.put(trace, Value(batch))

  def find(trace: UUID): Option[Seq[TraceEvent]] =
    Option(internalMap.get(trace)).map(_.traces)

  def clear(): Unit = internalMap.clear()

  def purgeOld(): Unit = {
    val top = System.currentTimeMillis - maxAge
    internalMap.entrySet.filter(e ⇒ e.getValue.timestamp <= top).foreach { e ⇒
      internalMap.remove(e.getKey, e.getValue)
    }
  }
}

object LocalMemoryPlayTraceTreeRepository extends MemoryPlayTraceTreeRepository(30 * 1000)
