/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.util

import akka.actor.{ Actor, ActorLogging }
import activator.analytics.data.{ Scope, TimeRange, MetadataStats, HostStats }
import activator.analytics.rest.http.MetadataStatsRetriever
import activator.analytics.rest.RestExtension
import activator.analytics.repository._
import com.typesafe.inkan.TypesafeLicense
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.Date
import scala.concurrent.duration._
import scala.util.matching.Regex

case object CheckFullInfo

case object CheckActorSystemInfo

class LicenseHandlerActor(
  license: LicenseInformation,
  hostStatsRepository: HostStatsRepository,
  metadataStatsRepository: MetadataStatsRepository,
  summarySpanStatsRepository: SummarySpanStatsRepository,
  errorStatsRepository: ErrorStatsRepository,
  actorStatsRepository: ActorStatsRepository) extends Actor with ActorLogging {

  val nodeHandler = new LicenseNodeHandler(license)
  val actorSystemHandler = new LicenseActorSystemHandler(license, nodeHandler)
  val actorPathHandler = new LicenseActorPathHandler(license, actorSystemHandler)
  val retriever = new MetadataStatsRetriever(context.system, metadataStatsRepository, summarySpanStatsRepository, errorStatsRepository, actorStatsRepository)
  var scheduler = context.system.scheduler.schedule(0.seconds, 10.seconds, self, CheckFullInfo)(context.system.dispatcher)
  val includeAnonymous = RestExtension(context.system).IncludeAnonymousActorPathsInMetadata
  val includeTemp = RestExtension(context.system).IncludeTempActorPathsInMetadata

  def receive = {
    case CheckFullInfo ⇒
      updateHostInfo()
      updateActorPathInfo()
    case CheckActorSystemInfo ⇒
      updateActorPathInfo()
  }

  def updateHostInfo() {
    if (nodeHandler.hostsLimitReached) {
      // No more hosts allowed since limit is reached - let scheduler check for new actor systems only at a slower pace
      scheduler.cancel()
      scheduler = context.system.scheduler.schedule(1.minute, 1.minute, self, CheckActorSystemInfo)(context.system.dispatcher)
    } else {
      hostStatsRepository.findAll foreach {
        nodeHandler.addHostStats(_)
      }
    }
  }

  def updateActorPathInfo() {
    actorSystemHandler.setActorSystems(
      retriever.retrieveActorSystemsByNode(
        TimeRange.monthRange(System.currentTimeMillis, 1),
        Scope(),
        includeAnonymous,
        includeTemp,
        limit = 1000))
  }
}

class LicenseNodeHandler(license: LicenseInformation) extends Limiter {
  private final val readWriteLock = new ReentrantReadWriteLock
  private final val readLock = readWriteLock.readLock
  private final val writeLock = readWriteLock.writeLock
  private var hostsCount = 0
  private var mappedNodesToHosts = Map.empty[String, Set[String]]
  var hostsLimitReached = license.licensedServers == 0

  // Register this limiter in the global scope
  RestLimiter.registerLimiter(NodeTypeLimiter, this)

  def addHostStats(hs: HostStats) {
    writeLock.lock()
    try {
      if (!hostsLimitReached && !mappedNodesToHosts.values.foldLeft(false)((r, c) ⇒ c.contains(hs.host))) {
        hostsCount += 1
        if (hostsCount == license.licensedServers) hostsLimitReached = true
      }

      mappedNodesToHosts += hs.node -> (mappedNodesToHosts.getOrElse(hs.node, Set.empty[String]) ++ Set(hs.host))
    } finally {
      writeLock.unlock()
    }
  }

  def isValid(node: String): ValidationMessage = {
    readLock.lock()
    try {
      if (license.expiry < System.currentTimeMillis) {
        ValidationMessage(false, Seq("License: Expired since (" + new Date(license.expiry) + ")"))
      } else {
        mappedNodesToHosts.get(node) match {
          case None if (hostsLimitReached) ⇒ ValidationMessage(false, Seq("License: Node [" + node + "] is not valid."))
          case _                           ⇒ ValidationMessage()
        }
      }
    } finally {
      readLock.unlock()
    }
  }
}

/**
 * Specific handler for actor paths. It uses the fact that the path always contains the actor system and
 * via this actor system we can determine what potential nodes the actor is on.
 * (Although the actor will only be on one node the same name of an actor system may be used on multiple nodes hence the non-determinism)
 * Via these nodes we can then check if there's a valid license with the LicenseNodeHandler.
 */
class LicenseActorPathHandler(license: LicenseInformation, actorSystemHandler: LicenseActorSystemHandler) extends Limiter {
  private final val actorSystemReg = """^.*akka://([^/]*).*""".r

  // Register this limiter in the global scope
  RestLimiter.registerLimiter(ActorPathTypeLimiter, this)

  def isValid(path: String): ValidationMessage = {
    if (license.expiry < System.currentTimeMillis) ValidationMessage(false, Seq("License: Expired since (" + new Date(license.expiry) + ")"))
    else
      extractActorSystemFromPath(path) match {
        case Some(as) ⇒ actorSystemHandler.isValid(as)
        case None     ⇒ ValidationMessage(false, Seq("License: Incorrect actor path: %s".format(path)))
      }
  }

  private def extractActorSystemFromPath(path: String): Option[String] = path match {
    case actorSystemReg(as) ⇒ Some(as)
    case _                  ⇒ None
  }
}

class LicenseActorSystemHandler(license: LicenseInformation, licenceNodeHandler: LicenseNodeHandler) extends Limiter {
  private final val readWriteLock = new ReentrantReadWriteLock
  private final val readLock = readWriteLock.readLock
  private final val writeLock = readWriteLock.writeLock
  private var mappedActorSystemsToNode = Map.empty[Set[String], String]

  // Register this limiter in the global scope
  RestLimiter.registerLimiter(ActorSystemTypeLimiter, this)

  def setActorSystems(actorSystems: Map[String, Set[String]]) {
    writeLock.lock()
    try {
      mappedActorSystemsToNode = actorSystems map {
        _.swap
      }
    } finally {
      writeLock.unlock()
    }
  }

  def isValid(actorSystem: String): ValidationMessage = {
    readLock.lock()
    try {
      if (license.expiry < System.currentTimeMillis) {
        ValidationMessage(false, Seq("License: Expired since (" + new Date(license.expiry) + ")"))
      } else {
        val nodes = mappedActorSystemsToNode.filterKeys { _.contains(actorSystem) }.values
        if (nodes.isEmpty && licenceNodeHandler.hostsLimitReached) {
          ValidationMessage(false, Seq("License: Unable to use actor system information from actor path."))
        } else {
          val results = nodes map { licenceNodeHandler.isValid(_) } filterNot { v ⇒ v.valid }
          if (results.isEmpty) ValidationMessage()
          else ValidationMessage(false, results.flatMap { _.errors }.toSeq)
        }
      }
    } finally {
      readLock.unlock()
    }
  }
}

object LicenseConverter {
  private final val fiveYearsInMillis = 1000 * 60 * 60 * 24 * 365 * 5L
  private final val defaultLicensedServers = 10

  def convert(license: Option[TypesafeLicense]): LicenseInformation = license match {
    case None    ⇒ LicenseInformation(name = "undefined", licensedServers = defaultLicensedServers, expiry = (System.currentTimeMillis() + fiveYearsInMillis))
    case Some(l) ⇒ LicenseInformation(name = l.name, licensedServers = l.servers, expiry = l.expiry)
  }
}
