/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.util

import scala.collection.mutable.{ Map ⇒ MMap }
import java.util.concurrent.locks.ReentrantReadWriteLock

trait Limiter {
  def isValid(payload: String): ValidationMessage
}

object RestLimiter {
  private final val readWriteLock = new ReentrantReadWriteLock
  private final val readLock = readWriteLock.readLock
  private final val writeLock = readWriteLock.writeLock
  private var limiters = MMap.empty[LimiterType, Seq[Limiter]]

  def registerLimiter(limiterType: LimiterType, limiter: Limiter) = {
    writeLock.lock()
    try {
      limiters += limiterType -> (limiters.getOrElse(limiterType, Seq.empty[Limiter]) ++ Seq(limiter))
    } finally {
      writeLock.unlock()
    }
  }

  def isValid(limiterType: LimiterType, payload: String): ValidationMessage = {
    readLock.lock()
    try {
      limiters.get(limiterType) match {
        case None ⇒ ValidationMessage()
        case Some(ls) ⇒
          val messages = ls flatMap {
            _.isValid(payload).errors
          }
          if (messages.isEmpty) ValidationMessage()
          else ValidationMessage(false, messages)
      }
    } finally {
      readLock.unlock()
    }
  }

  // Used from tests
  def reset() {
    writeLock.lock()
    try {
      limiters = MMap.empty[LimiterType, Seq[Limiter]]
    } finally {
      writeLock.unlock()
    }
  }
}

sealed trait LimiterType
case object NodeTypeLimiter extends LimiterType
case object ActorSystemTypeLimiter extends LimiterType
case object ActorPathTypeLimiter extends LimiterType
case class ValidationMessage(valid: Boolean = true, errors: Seq[String] = Seq.empty[String])