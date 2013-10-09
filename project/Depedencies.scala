import sbt._

object Dependencies {

  lazy val analyticsDependencies = Seq(
    "com.typesafe.atmos"   % "atmos-collect"      % "1.4.0-SNAPSHOT" artifacts(Artifact("atmos-collect", "jar", "jar")),
    "org.codehaus.jackson" % "jackson-core-asl"   % "1.9.9",
    "org.codehaus.jackson" % "jackson-mapper-asl" % "1.9.9",
    "io.spray"             % "spray-can"          % "1.1-M8",
    "ch.qos.logback"       % "logback-classic"    % "1.0.13",

    "com.typesafe.akka"    %% "akka-testkit"      % "2.1.4"          % "test",
    "junit"                % "junit"              % "4.5"            % "test",
    "org.scalatest"        %% "scalatest"         % "1.9.1"          % "test")
}
