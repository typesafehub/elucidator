/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

trait BasicStats {
  def scope: Scope
  def spanTypeName: String
  def timeRange: TimeRange
}
