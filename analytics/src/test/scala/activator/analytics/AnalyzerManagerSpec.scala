/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics

import com.typesafe.atmos.trace.ReceiveMain
import com.typesafe.atmos.util.AtmosSpec
import org.scalatest.matchers.MustMatchers
import scala.concurrent._
import scala.concurrent.duration._
import activator.analytics.analyzer.AnalyzerManager

@org.junit.runner.RunWith(classOf[org.scalatest.junit.JUnitRunner])
class AnalyzerManagerSpec extends AtmosSpec with MustMatchers {

  override def atStartup(): Unit = {
    ReceiveMain.startReceiver(system.settings.config)
  }

  override def atTermination(): Unit = {
    ReceiveMain.shutdownReceiver()
  }

  "An AnalyzerManager" must {

    "have a working awaitTermination" in {
      AnalyzerManager.create(system.settings.config) must be(true)

      import system.dispatcher

      val awaiter = Future[Unit]({ AnalyzerManager.awaitTermination() })

      val e = intercept[TimeoutException] {
        Await.result(awaiter, timeoutHandler.timeoutify(50.milliseconds))
      }
      awaiter.isCompleted must be(false)

      AnalyzerManager.delete() must be(true)

      // this should throw if awaitTermination did and fail the test
      Await.result(awaiter, timeoutHandler.timeoutify(2.seconds))

      awaiter.isCompleted must be(true)
    }
  }
}
