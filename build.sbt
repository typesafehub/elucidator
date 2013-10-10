name := "activator-analytics"

organization := "com.typesafe.activator"

version := "0.1-SNAPSHOT"

scalaVersion := "2.10.3"

parallelExecution in GlobalScope := false

// *** FORMATTING ***

lazy val formatSettings = scalariformSettings ++ Seq(
    ScalariformKeys.preferences in Compile := formattingPreferences,
    ScalariformKeys.preferences in Test    := formattingPreferences)

def formattingPreferences = {
    import scalariform.formatter.preferences._
    FormattingPreferences()
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
}

// *** ANALYTICS ***

lazy val analytics =
		project.in( file("analytics") )
	.settings(formatSettings:_*)
	.settings(Dependencies.analyticsDependencies:_*)
    .settings(ProguardConf.analyticsSettings:_*)

// *** RUNNER ***

mainClass in (Compile, run) := Some("activator.analytics.runner.AnalyticsMain")

lazy val runner =
        project.in( file("runner") )
    .dependsOn(analytics)
	.settings(formatSettings:_*)
