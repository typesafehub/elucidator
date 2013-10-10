/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import com.typesafe.trace.uuid.UUID

case class HostStats(
  host: String,
  node: String,
  actorSystem: String,
  created: Long = System.currentTimeMillis,
  id: UUID = new UUID())