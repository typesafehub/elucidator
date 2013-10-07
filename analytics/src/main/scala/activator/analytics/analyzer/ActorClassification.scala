/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import activator.analytics.data.Scope

object ActorClassification {
  final val anonymousSymbol = "$"
  final val temporarySymbol = "/temp/"

  def containsAnonymousPath(scope: Scope): Boolean = scope.path.isDefined && scope.path.get.contains(ActorClassification.anonymousSymbol)

  def containsTemporaryPath(scope: Scope): Boolean = scope.path.isDefined && scope.path.get.contains(ActorClassification.temporarySymbol)

  def filterNotTemporary(paths: Set[String]): Set[String] = paths.filterNot(_.contains(ActorClassification.temporarySymbol))

  def filterNotAnonymous(paths: Set[String]): Set[String] = paths.filterNot(_.contains(ActorClassification.anonymousSymbol))
}
