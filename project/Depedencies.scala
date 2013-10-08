import sbt._

object Dependencies {

  lazy val analyticsDependencies = Seq(
    "com.typesafe.atmos"		  %% "atmos-event" 		    % "1.4.0-SNAPSHOT",
    "com.typesafe.atmos" 		  % "atmos-collect" 		  % "1.4.0-SNAPSHOT",
    "com.typesafe.inkan"    	%% "inkan"              % "0.1.2",
    "org.codehaus.jackson" 		% "jackson-core-asl"    % "1.9.9",
    "org.codehaus.jackson"  	% "jackson-mapper-asl"  % "1.9.9",
    "io.spray"                % "spray-can"           % "1.1-M8",
    "com.typesafe.akka"       %% "akka-testkit"       % "2.1.4",
    "junit"                   % "junit"               % "4.5",
    "ch.qos.logback"          % "logback-classic"     % "1.0.13",
    "com.typesafe.atmos" 		  % "atmos-collect" 		  % "1.4.0-SNAPSHOT"    % "test",
    "org.scalatest"           %% "scalatest"          % "1.9.1"             % "test")
}