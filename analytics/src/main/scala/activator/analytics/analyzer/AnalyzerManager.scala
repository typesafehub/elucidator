/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.analyzer

import akka.actor.{ ActorSystem, ReflectiveDynamicAccess }
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

/**
 * Responsible for creating, keeping track of, and deleting Analyzer actor systems.
 */
object AnalyzerManager {
  private var system: Option[ActorSystem] = None

  def create(config: Config): Boolean = synchronized {
    system match {
      case Some(s) ⇒ false
      case None ⇒
        config.checkValid(ConfigFactory.defaultReference, "activator")
        val actorSystem = ActorSystem("Analyzer", config)
        new LocalMemoryAnalyzerBoot(actorSystem)
        system = Some(actorSystem)
        true
    }
  }

  def delete(): Boolean = synchronized {
    system match {
      case Some(s) ⇒
        s.shutdown()
        system = None
        true
      case None ⇒ false
    }
  }

  def awaitTermination() = for (s ← system) s.awaitTermination()
}
