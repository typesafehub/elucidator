/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest

import akka.actor._

class StandardRestExtension(system: ExtendedActorSystem) extends Extension {
  // Sets mode of system (local memory or MongoDB repository)
  val config = system.settings.config

  val Mode = config.getString("atmos.mode")

  final val UseLocalMemory = config.getString("atmos.mode") == "local"

  // Query related
  final val HtmlFileResources = config.getString("atmos.analytics.html-file-resources")

  final val JsonPrettyPrint = config.getBoolean("atmos.analytics.json-pretty-print")
  final val MaxTimeriesPoints = config.getInt("atmos.analytics.max-timeseries-points")
  final val MaxSpanTimeriesPoints = config.getInt("atmos.analytics.max-span-timeseries-points")

  final val PagingSize = config.getInt("atmos.analytics.paging-size")
  final val DefaultLimit = config.getInt("atmos.analytics.default-limit")
  final val IncludeAnonymousActorPathsInMetadata = config.getBoolean("atmos.analytics.include-anonymous-paths-in-metadata")
  final val IncludeTempActorPathsInMetadata = config.getBoolean("atmos.analytics.include-temp-paths-in-metadata")
}

object RestExtension extends ExtensionId[StandardRestExtension] with ExtensionIdProvider {
  def lookup() = RestExtension
  def createExtension(system: ExtendedActorSystem) = new StandardRestExtension(system)
}
