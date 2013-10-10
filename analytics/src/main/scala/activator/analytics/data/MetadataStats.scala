/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import com.typesafe.trace.uuid.UUID

case class MetadataStats(
  timeRange: TimeRange,
  scope: Scope,
  metrics: MetadataStatsMetrics = MetadataStatsMetrics(),
  id: UUID = new UUID())

case class MetadataStatsMetrics(
  spanTypes: Set[String] = Set(),
  paths: Set[String] = Set(),
  totalActorCount: Option[Int] = None,
  tags: Set[String] = Set(),
  nodes: Set[String] = Set(),
  actorSystems: Set[String] = Set(),
  actorSystemCount: Option[Int] = None,
  dispatchers: Set[String] = Set(),
  dispatcherCount: Option[Int] = None,
  playPatterns: Set[String] = Set(),
  playControllers: Set[String] = Set())
