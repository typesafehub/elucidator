/**
 *  Copyright (C) 2011-2014 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

trait RepositoryLifecycle {
  /**
   * Clears data from all underlying repositories.
   * Typically there are multiple repos containing data, one per statistical type,
   * and this method will make sure that data is wiped from all of them.
   */
  def clear(): Unit
}

class RepositoryLifecycleHandler {
  var repos = Set.empty[RepositoryLifecycle]

  def register(repo: RepositoryLifecycle) {
    repos += repo
  }

  def clear() {
    repos foreach { _.clear() }
  }
}

object LocalRepositoryLifecycleHandler extends RepositoryLifecycleHandler
