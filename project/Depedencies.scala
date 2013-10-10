import sbt._
import sbt.Keys._

object Dependencies {

  val traceRepo = "traceRepo" at "http://repo.typesafe.com/typesafe/releases"//com/typesafe/trace/trace-collect/0.1-94cb22e7439b3537ebb0669d9486e5b2121e1d5c"

  val analyticsLibs = Seq(
    "com.typesafe.trace" % "trace-collect" % "0.1-94cb22e7439b3537ebb0669d9486e5b2121e1d5c",
    "org.codehaus.jackson" % "jackson-core-asl"   % "1.9.9",
    "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.9",
    "io.spray"             % "spray-can"          % "1.1-M8",
    "ch.qos.logback"       % "logback-classic"    % "1.0.13",

    "com.typesafe.akka"    %% "akka-testkit"      % "2.1.4"          % "test",
    "junit"                % "junit"              % "4.5"            % "test",
    "org.scalatest"        %% "scalatest"         % "1.9.1"          % "test")

  def analyticsDependencies: Seq[Setting[_]] =
    Seq(
      resolvers += traceRepo,
      libraryDependencies ++= analyticsLibs)

}
