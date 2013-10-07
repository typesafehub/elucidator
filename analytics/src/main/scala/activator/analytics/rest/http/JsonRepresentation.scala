/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.BasicTypes.{ Bytes, DurationNanos, Timestamp }
import activator.analytics.data.{ TimeRange, Scope, BasicTypes }
import activator.analytics.rest.RestExtension
import com.typesafe.atmos.uuid.UUID
import java.io.Writer
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MICROSECONDS
import java.util.concurrent.TimeUnit.NANOSECONDS
import java.util.Date
import org.codehaus.jackson.JsonFactory
import org.codehaus.jackson.JsonGenerator
import scala.language.implicitConversions

trait JsonRepresentation {
  import JsonRepresentation._

  val jsonFactory = new JsonFactory

  def baseUrl: Option[String] = None
  def useLinks = baseUrl.isDefined
  def formatTimestamps = true

  def createJsonGenerator(writer: Writer, usePrettyPrint: Boolean): JsonGenerator = {
    val gen = jsonFactory.createJsonGenerator(writer)
    if (usePrettyPrint) gen.useDefaultPrettyPrinter()
    gen
  }

  implicit def toJsonGeneratorExtension(gen: JsonGenerator): JsonGeneratorExtension = {
    new JsonGeneratorExtension(gen)
  }

  def writeJson(timeRange: TimeRange, gen: JsonGenerator) {
    gen.writeObjectFieldStart("timeRange")
    gen.writeTimestampField("startTime", timeRange.startTime)
    gen.writeTimestampField("endTime", timeRange.endTime)
    gen.writeStringField("rangeType", timeRange.rangeType.toString)
    gen.writeEndObject()
  }

  def writeJson(scope: Scope, gen: JsonGenerator) {
    if (scope != Scope()) {
      gen.writeObjectFieldStart("scope")
      for (x ← scope.node) gen.writeStringField("node", x)
      for (x ← scope.actorSystem) gen.writeStringField("actorSystem", x)
      for (x ← scope.path) gen.writeStringField("actorPath", x)
      for (x ← scope.dispatcher) gen.writeStringField("dispatcher", x)
      for (x ← scope.tag) gen.writeStringField("tag", x)
      for (x ← scope.playPattern) gen.writeStringField("pattern", x)
      for (x ← scope.playController) gen.writeStringField("controller", x)
      gen.writeEndObject()
    }
  }

  class JsonGeneratorExtension(gen: JsonGenerator) {

    def writeTimestampField(name: String, timestamp: Timestamp) {
      if (formatTimestamps) gen.writeStringField(name, TimeParser.format(new Date(timestamp)))
      else gen.writeNumberField(name, timestamp)
    }

    def writeTimestamp(timestamp: Timestamp) {
      if (formatTimestamps) gen.writeString(TimeParser.format(new Date(timestamp)))
      else gen.writeNumber(timestamp)
    }

    def writeDateField(name: String, timestamp: Timestamp): Unit = {
      gen.writeStringField(name, new SimpleDateFormat("MMM dd, yyyy").format(new Date(timestamp)))
    }

    def writeDate(timestamp: Timestamp): Unit = {
      gen.writeString(new SimpleDateFormat("MMM dd, yyyy").format(new Date(timestamp)))
    }

    def writeDurationField(
      name: String,
      duration: DurationNanos,
      convertToTimeUnit: TimeUnit = DefaultOutputDurationTimeUnit,
      unitField: Boolean = true) {

      gen.writeNumberField(name, convertToTimeUnit.convert(duration, NANOSECONDS))
      if (unitField) {
        writeDurationTimeUnitField(name + "Unit", convertToTimeUnit)
      }
    }

    def writeDuration(duration: DurationNanos, convertToTimeUnit: TimeUnit = DefaultOutputDurationTimeUnit) {
      gen.writeNumber(convertToTimeUnit.convert(duration, NANOSECONDS))
    }

    def writeDurationTimeUnitField(name: String, timeUnit: TimeUnit = DefaultOutputDurationTimeUnit) {
      gen.writeStringField(name, timeUnit.name.toLowerCase)
    }

    def writeBytesField(
      name: String,
      bytes: Bytes,
      convertToKiloBytes: Boolean = false,
      convertToMegaBytes: Boolean = false,
      unitField: Boolean = true) {

      if (convertToKiloBytes)
        gen.writeNumberField(name, bytes / 1024.0)
      else if (convertToMegaBytes)
        gen.writeNumberField(name, bytes / (1024.0 * 1024.0))
      else
        gen.writeNumberField(name, bytes)

      if (unitField) {
        writeBytesUnitField(name + "Unit", convertToKiloBytes, convertToMegaBytes)
      }
    }

    def writeBytesUnitField(
      name: String, kiloBytes: Boolean = false,
      megaBytes: Boolean = false) {
      val unit = if (kiloBytes) "kB" else if (megaBytes) "MB" else "bytes"
      gen.writeStringField(name, unit)
    }

    def writeLink(name: String, uri: String) {
      val url = baseUrl.getOrElse("") + uri
      gen.writeStringField(name, url)
    }

    def writeLinkOrId(name: String, uri: String, id: UUID) {
      val value = baseUrl match {
        case Some(base) ⇒ base + uri + id.toString
        case _          ⇒ id.toString
      }
      gen.writeStringField(name, value)
    }

  }

}

object JsonRepresentation {
  val DefaultOutputDurationTimeUnit = MICROSECONDS
}

