/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

import activator.analytics.data.HostStats

trait HostStatsRepository {
  def save(hostStats: HostStats)
  def findAll: Set[HostStats]
}

class MemoryHostStatsRepository extends HostStatsRepository {
  private var hosts = Set.empty[HostStats]

  def save(hostStats: HostStats) {
    hosts += hostStats
  }

  def findAll: Set[HostStats] = hosts
}

object LocalMemoryHostStatsRepository extends MemoryHostStatsRepository
