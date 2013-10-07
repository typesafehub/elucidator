/**
 *  Copyright (C) 2012 Typesafe <http://typesafe.com/>
 */

package activator.analytics.analyzer

import com.typesafe.config._

object AnalyzerMain {
  def main(args: Array[String]): Unit = {
    val main = new AnalyzerMain()
    main.startup()
    main.awaitTermination()
    main.shutdown()
    System.exit(0)
  }
}

class AnalyzerMain {
  def startup() {
    val config = ConfigFactory.load()
    config.checkValid(ConfigFactory.defaultReference, "atmos")
    AnalyzerManager.create(config)
  }

  def awaitTermination() = AnalyzerManager.awaitTermination()

  def shutdown() = AnalyzerManager.delete()
}
