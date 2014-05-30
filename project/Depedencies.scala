import sbt._
import sbt.Keys._

object Dependencies {

  val traceRepo = "trace repo" at "http://repo.typesafe.com/typesafe/releases"
  val sprayRepo = "spray repo" at "http://repo.spray.io"

  val analyticsLibs = Seq(
    "com.typesafe.trace"   % "trace-collect211"   % "0.1.2",
    "org.codehaus.jackson" % "jackson-core-asl"   % "1.9.9",
    "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.9",
    "com.typesafe.akka"    %% "akka-actor"        % "2.3.3",
    "io.spray"             % "spray-can"          % "1.3.1",
    "ch.qos.logback"       % "logback-classic"    % "1.0.13",

    "com.typesafe.akka"    %% "akka-testkit"      % "2.3.3"          % "test",
    "junit"                % "junit"              % "4.5"            % "test",
    "org.scalatest"        %% "scalatest"         % "2.1.6"          % "test")

  def analyticsDependencies: Seq[Setting[_]] =
    Seq(
      resolvers ++= Seq(traceRepo, sprayRepo),
      libraryDependencies ++= analyticsLibs)

}
