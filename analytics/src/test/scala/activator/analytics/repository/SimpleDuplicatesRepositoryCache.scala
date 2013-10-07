/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.repository

/**
 * Used to mock "away" the normal cache behavior.
 */
class SimpleDuplicatesRepositoryCache extends DuplicatesRepository {
  def contains(key: DuplicatesKey): Boolean = false

  def store(key: DuplicatesKey) = {}

  def store(keys: Seq[DuplicatesKey]) = {}
}
