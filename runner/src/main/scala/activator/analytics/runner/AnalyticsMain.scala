/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.runner

/**
 * Starts up the collect, analyze, query modules for development mode.
 */
object AnalyticsMain {

  def main(args: Array[String]) {
    activator.analytics.rest.http.RestMain.main(Array())

    com.typesafe.trace.ReceiveMain.main(Array())

    // must be last as it uses awaitTermination
    activator.analytics.analyzer.AnalyzerMain.main(Array())
  }
}