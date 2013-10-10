import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtProguard
import com.typesafe.sbt.SbtProguard.{ Proguard, ProguardKeys, ProguardMerge, ProguardOptions, proguardSettings }
import com.typesafe.sbt.SbtProguard.ProguardKeys.proguard

object ProguardConf {

  def analyticsSettings: Seq[Setting[_]] = proguardSettings ++ Seq(
    javaOptions in (Proguard, proguard) := Seq("-Xms1024m", "-Xmx1024m"),
    ProguardKeys.options in Proguard += "-dontoptimize", // problems with optimize
    ProguardKeys.options in Proguard += analyticsConf,
    ProguardKeys.merge in Proguard := true,
    ProguardKeys.mergeStrategies in Proguard += ProguardMerge.append("reference.conf")
  )

  def analyticsConf =
    """
      |# full obfuscation
      |-repackageclasses ''
      |-allowaccessmodification
      |
      |# keep line numbers
      |-renamesourcefileattribute analytics
      |-keepattributes SourceFile,LineNumberTable
      |
      |# obfuscation mapping
      |-printmapping proguard.map
      |
      |# note for dynamic access to java.lang.Thread.parkBlocker
      |-dontnote scala.concurrent.forkjoin.ForkJoinPool
      |
      |# discard remaining notes for these packages
      |-dontnote akka.**
      |-dontnote ch.qos.logback.**
      |-dontnote org.codehaus.jackson.map.deser.**
      |-dontnote scala.runtime.**
      |-dontnote scala.xml.**
      |-dontnote spray.http.**
      |
      |# discard remaining warnings for these packages
      |-dontwarn ch.qos.logback.**
      |-dontwarn org.codehaus.jackson.map.ext.**
      |-dontwarn scala.**
      |-dontwarn spray.can.**
      |-dontwarn spray.io.**
      |-dontwarn org.scalatools.**
      |-dontwarn org.apache.**
      |
      |# scala
      |-keepclassmembers class * { ** MODULE$; }
      |-keepclassmembernames class scala.concurrent.forkjoin.ForkJoinPool {
      |  long stealCount;
      |  long ctl;
      |  int plock;
      |  int indexSeed;
      |}
      |-keepclassmembernames class scala.concurrent.forkjoin.ForkJoinPool$WorkQueue {
      |  int runState;
      |  int qlock;
      |}
      |-keepclassmembernames class scala.concurrent.forkjoin.ForkJoinTask { int status; }
      |-keepclassmembernames class scala.concurrent.forkjoin.LinkedTransferQueue {
      |  scala.concurrent.forkjoin.LinkedTransferQueue$Node head;
      |  scala.concurrent.forkjoin.LinkedTransferQueue$Node tail;
      |  int sweepVotes;
      |}
      |-keepclassmembernames class scala.concurrent.forkjoin.LinkedTransferQueue$Node {
      |  java.lang.Object item;
      |  scala.concurrent.forkjoin.LinkedTransferQueue$Node next;
      |  java.lang.Thread waiter;
      |}
      |
      |# akka
      |-keepclassmembernames class * implements akka.actor.Actor {
      |  akka.actor.ActorContext context;
      |  akka.actor.ActorRef self;
      |}
      |-keep class * implements akka.actor.ActorRefProvider { public <init>(...); }
      |-keep class * implements akka.actor.Extension { public <init>(...); }
      |-keep class * implements akka.actor.ExtensionId { public <init>(...); }
      |-keep class * implements akka.actor.ExtensionIdProvider { public <init>(...); }
      |-keep class akka.actor.SerializedActorRef { *; }
      |-keep class * implements akka.actor.SupervisorStrategyConfigurator { public <init>(...); }
      |-keep class * extends akka.dispatch.ExecutorServiceConfigurator { public <init>(...); }
      |-keep class * implements akka.dispatch.MailboxType { public <init>(...); }
      |-keep class * extends akka.dispatch.MessageDispatcherConfigurator { public <init>(...); }
      |-keep class akka.event.slf4j.Slf4jEventHandler
      |-keep class * implements akka.routing.RouterConfig { public <init>(...); }
      |-keep class * implements akka.serialization.Serializer { public <init>(...); }
      |-keep class akka.event.Logging*
      |-keep class akka.event.Logging$LogExt { public <init>(...); }
      |
      |# spray
      |-keep public class spray.http.Http* { *; }
      |-keep public class spray.http.parser.** { *; }
      |
      |# protobuf
      |-keepclassmembernames class * extends com.google.protobuf.GeneratedMessage {
      |  ** newBuilder();
      |}
      |
      |# logback and slf4j
      |-keep public class ch.qos.logback.** { *; }
      |-keep public class org.slf4j.** { *; }
      |
      |# trace
      |-keepnames class * implements com.typesafe.trace.Annotation
      |-keepnames class * implements com.typesafe.trace.Info
      |-keepnames class * implements com.typesafe.trace.SysMsg
      |-keep class * extends com.typesafe.trace.TraceEventListener { public <init>(...); }
    """.stripMargin
}
