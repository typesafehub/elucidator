name := "activator-analytics"

parallelExecution in GlobalScope := false

Defaults.defaultSettings ++ Seq(
  publish := {},
  publishLocal := {}
)

//publishTo := Some(privateRepo)

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

// *** DEFAULT SETTINGS ***

def defaultSettings = Seq(
    organization := "com.typesafe.activator",
    version := "0.1-SNAPSHOT",
    scalaVersion := "2.10.3",
    publishTo := Some(privateRepo),
    publishArtifact in packageSrc := false,
    publishArtifact in packageDoc := false
  )

// *** DISTRIBUTION SETTINGS ***

val privateRepo = Resolver.url("activator-analytics-snapshot", new URL("https://private-repo.typesafe.com/typesafe/activator-analytics-snapshot"))

// *** ANALYTICS PROJECT ***

lazy val analytics =
		project.in( file("analytics") )
    .settings(defaultSettings:_*)
	.settings(formatSettings:_*)
	.settings(Dependencies.analyticsDependencies:_*)
    .settings(ProguardConf.analyticsSettings:_*)

// *** RUNNER PROJECT ***

mainClass in (Compile, run) := Some("activator.analytics.runner.AnalyticsMain")

lazy val runner =
        project.in( file("runner") )
    .dependsOn(analytics)
    .settings(defaultSettings:_*)
	.settings(formatSettings:_*)
