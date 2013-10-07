/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import akka.actor.ActorSystem
import com.typesafe.atmos.uuid.UUID
import java.lang.management.ManagementFactory
import net.sf.ehcache.management.ManagementService
import net.sf.ehcache.{ Element, Cache, CacheManager }

// for now, this repository is unlike the others since it always uses memory
trait DuplicatesRepository {
  def contains(key: DuplicatesKey): Boolean
  def store(key: DuplicatesKey): Unit
  def store(keys: Seq[DuplicatesKey]): Unit
}

class DuplicatesRepositoryCache(system: ActorSystem) extends DuplicatesRepository {

  // TODO : See #281
  private final val CacheName = "DuplicatesCache"
  private final val CacheValue: AnyRef = java.lang.Boolean.TRUE

  lazy val cacheManager: CacheManager = {
    try {
      val manager = CacheManager.create
      val cacheName = CacheName + system.name
      if (!manager.cacheExists(cacheName)) {
        val memoryOnlyCache = new Cache(cacheName, 100000, false, false, 900, 900)
        manager.addCache(memoryOnlyCache)
        try {
          val mBeanServer = ManagementFactory.getPlatformMBeanServer();
          ManagementService.registerMBeans(manager, mBeanServer, false, false, false, true);
        } catch {
          case e: Exception ⇒ // not fatal, ignore
        }
      }

      manager
    } catch {
      case t: Throwable ⇒
        system.log.error(t, "Failed to initialize cache, due to %s".format(t.getMessage))
        throw t
    }
  }

  def contains(key: DuplicatesKey): Boolean = {
    val cache = cacheManager.getCache(CacheName + system.name)
    val element: Element = cache.get(key)
    val value = if (element eq null) null else element.getObjectValue
    value ne null
  }

  def store(key: DuplicatesKey) = {
    val cache = cacheManager.getCache(CacheName + system.name)
    cache.put(new Element(key, CacheValue))
  }

  def store(keys: Seq[DuplicatesKey]) = {
    val cache = cacheManager.getCache(CacheName + system.name)
    for (key ← keys) cache.put(new Element(key, CacheValue))
  }
}

sealed trait DuplicatesKey

case class DuplicatesTraceKey(name: String, eventId: UUID) extends DuplicatesKey

case class DuplicatesSpanKey(name: String, spanTypeName: String, startEvent: UUID, endEvent: UUID) extends DuplicatesKey
