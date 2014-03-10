/**
 *  Copyright (C) 2011-2014 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

trait RepositoryLifecycle {
  var startTime = System.currentTimeMillis

  /**
   * Clears data from all underlying repositories.
   * Typically there are multiple repos containing data, one per statistical type,
   * and this method will make sure that data is wiped from all of them.
   */
  def clear(): Unit

  /**
   * Returns how long the cache has been running in milliseconds.
   *
   * Note: this method should be overridden by any persistent repository implementation.
   */
  def currentStorageTime: Long = (System.currentTimeMillis - startTime).min(RepositoryLifecycle.maxRunningTime)
}

object RepositoryLifecycle {
  // Hard coded value for how much data the cache can contain: 20 minutes
  final val maxRunningTime = 1000 * 60 * 20
}

class RepositoryLifecycleHandler extends RepositoryLifecycle {
  var repos = Set.empty[RepositoryLifecycle]

  def register(repo: RepositoryLifecycle) {
    repos += repo
  }

  def clear() {
    repos foreach { _.clear() }
    startTime = System.currentTimeMillis
  }
}

object LocalRepositoryLifecycleHandler extends RepositoryLifecycleHandler
