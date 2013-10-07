/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.data

import akka.actor.ActorPath

object Scope {
  def apply(
    path: Option[String] = None,
    tag: Option[String] = None,
    node: Option[String] = None,
    dispatcher: Option[String] = None,
    actorSystem: Option[String] = None,
    playPattern: Option[String] = None,
    playController: Option[String] = None): Scope = {

    Scope(path.map(ActorPath.fromString(_).name.startsWith("$")).getOrElse(false),
      path,
      tag,
      node,
      dispatcher,
      actorSystem,
      playPattern,
      playController)
  }
}

/**
 * Aggregated statistics are typically grouped by a scope.
 * A scope can be a single actor, actors marked with a tag,
 * or actors running on a specific node or actorSystem.
 * Tag is a marker that is used to logically group actors. An actor
 * may have several tags.
 */
case class Scope(
  anonymous: Boolean,
  path: Option[String],
  tag: Option[String],
  node: Option[String],
  dispatcher: Option[String],
  actorSystem: Option[String],
  playPattern: Option[String],
  playController: Option[String])
