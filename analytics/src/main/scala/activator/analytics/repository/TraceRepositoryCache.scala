/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import com.typesafe.atmos.trace.store.TraceRetrievalRepository
import com.typesafe.atmos.trace.TraceEvent

import com.typesafe.atmos.uuid.UUID
import java.lang.management.ManagementFactory
import net.sf.ehcache.Cache
import net.sf.ehcache.CacheManager
import net.sf.ehcache.Element
import net.sf.ehcache.management.ManagementService

trait TraceRepositoryCache extends TraceRetrievalRepository {
  // TODO : See #281
  private final val CacheName = "TraceRepositoryCacheAnalyzer"
  private lazy val cacheManager: CacheManager = {
    val manager = CacheManager.create
    if (!manager.cacheExists(CacheName)) {
      val memoryOnlyCache = new Cache(CacheName, 100000, false, false, 900, 900)
      manager.addCache(memoryOnlyCache)
    }
    try {
      val mBeanServer = ManagementFactory.getPlatformMBeanServer();
      ManagementService.registerMBeans(manager, mBeanServer, false, false, false, true);
    } catch {
      case e: Exception ⇒ // not fatal, ignore
    }
    manager
  }

  abstract override def event(id: UUID): Option[TraceEvent] = {
    val cache = cacheManager.getCache(CacheName)
    val element: Element = cache.get(id)
    val value = if (element eq null) null else element.getObjectValue
    if (value eq null) {
      val result = super.event(id)
      cache.put(new Element(id, result))
      result
    } else {
      value.asInstanceOf[Option[TraceEvent]]
    }
  }

  abstract override def events(ids: Seq[UUID]): Seq[TraceEvent] = {
    val result = super.events(ids)
    // populate cache
    val cache = cacheManager.getCache(CacheName)
    for (event ← result) {
      cache.put(new Element(event.id, Some(event)))
    }
    result
  }
}
