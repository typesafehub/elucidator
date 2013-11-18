/**
 * Copyright (C) 2011-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package activator.analytics

import akka.actor.ActorSystem
import akka.testkit.TestKit
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.util.concurrent.TimeoutException
import org.scalatest.matchers.MustMatchers
import org.scalatest.{ WordSpec, BeforeAndAfterAll }
import scala.concurrent.duration._
import akka.event.{ LoggingAdapter, Logging }
import akka.event.Logging._
import akka.event.slf4j.Slf4jLogger
import com.typesafe.trace.util.ExpectedFailureException

object AnalyticsSpec {
  val testConf: Config = ConfigFactory.parseString("""
      akka {
        # TestLogger suppresses "simulated" errors
        loggers = ["activator.analytics.TestLogger"]
        loglevel = WARNING
        stdout-loglevel = WARNING
        logger-startup-timeout = 10s

        actor {
          default-dispatcher {
            executor = "fork-join-executor"
            fork-join-executor {
              parallelism-min = 12
              parallelism-max = 12
            }
          }
        }
      }

      activator {
        analytics {
          ignore-span-types = []
          ignore-span-time-series = []
          store-time-interval = 0
          percentiles-store-time-interval = 0
          store-limit = 30000
          store-use-random-interval = false
          store-use-all-time = true
          store-use-duplicates-cache = true
          store-flush-delay = 2
          actor-path-time-ranges = ["minutes", "hours", "days", "months", "all"]

          save-spans = on
        }
        subscribe.notification-event-log-size = 500000

        test.time-factor = 1
      }""")

  def getCallerName: String = {
    val s = Thread.currentThread.getStackTrace map (_.getClassName) drop 1 dropWhile (_ matches ".*AnalyticsSpec.?$")
    s.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }

}

abstract class AnalyticsSpec(_system: ActorSystem) extends TestKit(_system) with WordSpec with MustMatchers with BeforeAndAfterAll {

  def this(config: Config) =
    this(ActorSystem(AnalyticsSpec.getCallerName,
      ConfigFactory.defaultOverrides
        .withFallback(config)
        .withFallback(AnalyticsSpec.testConf)
        .withFallback(ConfigFactory.defaultReference)))

  def this(conf: String) = this(ConfigFactory.parseString(conf))

  def this() = this(ActorSystem(AnalyticsSpec.getCallerName, AnalyticsSpec.testConf))

  val log: LoggingAdapter = Logging(system, this.getClass)

  def config: Config = system.settings.config

  def timeoutHandler = TimeoutHandler(config.getInt("activator.test.time-factor"))

  val timeFactor = timeoutHandler.factor

  override def beforeAll(): Unit = {
    atStartup()
  }

  override def afterAll(): Unit = {
    shutdownSystem()
    atTermination()
  }

  def shutdownSystem(): Unit = {
    system.shutdown()
    try {
      system.awaitTermination(timeoutHandler.duration)
    } catch {
      case _: TimeoutException ⇒ println("Failed to stop [%s] within expected shutdown time".format(system.name))
    }
  }

  protected def atStartup(): Unit = {}

  protected def atTermination(): Unit = {}
}

case class TimeoutHandler(factor: Int) {
  import java.util.concurrent.{ CountDownLatch, TimeUnit }
  private final val defaultTimeoutTime = 5000L

  def duration: FiniteDuration = Duration(defaultTimeoutTime * factor, TimeUnit.MILLISECONDS)

  def time: Long = defaultTimeoutTime * factor

  def unit: TimeUnit = TimeUnit.MILLISECONDS

  def timeoutify(originalDuration: Duration): Duration = originalDuration.*(factor)

  def awaitLatch(latch: CountDownLatch, timeout: Long, unit: TimeUnit): Boolean =
    latch.await(timeout * factor, unit)
}

/**
 * Event handler that suppresses all errors with cause ExpectedFailureException
 * or errors and warnings with expected messages.
 */
class TestLogger extends Slf4jLogger {
  override def receive = suppress.orElse(super.receive)

  def suppress: Receive = {
    case event @ Error(cause: ExpectedFailureException, logSource, logClass, message) ⇒
      receive(Debug(logSource, logClass, message))
    case event @ Error(cause, logSource, logClass, message) if message.toString.startsWith("Expected error") ⇒
      receive(Debug(logSource, logClass, message))
    case event @ Warning(logSource, logClass, message) if message.toString.startsWith("Expected warning") ⇒
      receive(Debug(logSource, logClass, message))
  }
}
