/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ TimeRange, SystemMetricsTimeSeriesPoint, SystemMetricsTimeSeries, BasicTypes }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.SystemMetricsTimeSeriesRepository
import GatewayActor._
import java.io.StringWriter
import java.io.Writer
import java.util.concurrent.TimeUnit
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }

class SystemMetricsTimeSeriesResource(systemMetricsTimeSeriesRepository: SystemMetricsTimeSeriesRepository)
  extends RestResourceActor with TimeSeriesSampling[SystemMetricsTimeSeriesPoint] {
  import SystemMetricsTimeSeriesResource._

  final val queryBuilder = new QueryBuilder

  def jsonRepresentation(request: HttpRequest) = new SystemMetricsTimeSeriesRepresentation(formatTimestamps(request), context.system)

  override val configMaxPoints = RestExtension(context.system).MaxTimeriesPoints

  def handle(req: HttpRequest): HttpResponse = {
    val path = req.uri.path.toString
    path match {
      case SystemMetricsNodePattern(node) ⇒
        handle(node, req.uri.query.toString) match {
          case Right((timeRange, series)) ⇒
            HttpResponse(entity = jsonRepresentation(req).toJson(series), headers = HeadersBuilder.headers(timeRange.endTime)).asJson
          case Left(message) ⇒
            HttpResponse(status = StatusCodes.BadRequest, entity = "Could not parse query string: " + req.uri.query.toString).asJson
        }
      case _ ⇒ HttpResponse(status = StatusCodes.BadRequest, entity = "Invalid URI: " + path).asJson
    }
  }

  def handle(node: String, queryParams: String): Either[String, (TimeRange, SystemMetricsTimeSeries)] = {
    queryBuilder.build(queryParams) match {
      case Left(message) ⇒ Left(message)
      case Right(query) ⇒
        val timeSeries = systemMetricsTimeSeriesRepository.findWithinTimePeriod(query.timeRange, node)
        val result = SystemMetricsTimeSeries.concatenate(timeSeries, query.timeRange, node)
        val sampledResult = sample(result, query.sampling, query.maxPoints)
        Right(Tuple2(query.timeRange, sampledResult))
    }
  }
}

object SystemMetricsTimeSeriesResource {
  import GatewayActor.PatternChars._
  val SystemMetricsNodePattern = ("""^.*/systemmetrics/timeseries/(""" + NodeChars + ")").r

  case class Query(
    timeRange: TimeRange,
    sampling: Option[Int],
    maxPoints: Option[Int])

  class QueryBuilder extends TimeRangeQueryBuilder with SamplingQueryBuilder {
    def build(queryParams: String): Either[String, Query] = {
      extractTime(queryParams) match {
        case Left(msg) ⇒ Left(msg)
        case Right(timeRange) ⇒
          Right(Query(timeRange, extractSampling(queryParams), extractMaxPoints(queryParams)))
      }
    }

    // override to convert to always use minuteRange
    override def extractTime(path: String): Either[String, TimeRange] = {
      super.extractTime(path) match {
        case left @ Left(_)   ⇒ left
        case Right(timeRange) ⇒ Right(TimeRange.minuteRange(timeRange.startTime, timeRange.endTime))
      }
    }
  }

}

class SystemMetricsTimeSeriesRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  val pointRepresentation = new SystemMetricsTimeSeriesPointRepresentation(formatTimestamps, system)

  def toJson(stats: SystemMetricsTimeSeries, writer: Writer) = {
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(stats, generator)
    generator.flush()
  }

  def toJson(stats: SystemMetricsTimeSeries): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    writeJson(stats, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(stats: SystemMetricsTimeSeries, generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeStringField("node", stats.node)
    writeJson(stats.timeRange, generator)
    generator.writeArrayFieldStart("points")
    for (point ← stats.points) {
      generator.writeStartObject()
      pointRepresentation.writeJson(point, generator)
      generator.writeEndObject()
    }
    generator.writeEndArray()
    generator.writeEndObject()
  }
}

class SystemMetricsTimeSeriesPointRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  def toJson(points: Seq[SystemMetricsTimeSeriesPoint]): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    generator.writeStartObject()
    generator.writeArrayFieldStart("points")
    for (point ← points) {
      generator.writeStartObject()
      writeJson(point, generator)
      generator.writeEndObject()
    }
    generator.writeEndArray()
    generator.writeEndObject()
    generator.flush()
    writer.toString
  }

  def writeJson(point: SystemMetricsTimeSeriesPoint, generator: JsonGenerator) {
    val metrics = point.metrics
    generator.writeTimestampField("timestamp", point.timestamp)
    generator.writeTimestampField("startTime", metrics.startTime)
    generator.writeNumberField("upTime", metrics.upTime)
    generator.writeDurationTimeUnitField("upTimeUnit", TimeUnit.MILLISECONDS)
    generator.writeNumberField("runningActors", metrics.runningActors)
    generator.writeNumberField("availableProcessors", metrics.availableProcessors)
    generator.writeNumberField("daemonThreadCount", metrics.daemonThreadCount)
    generator.writeNumberField("threadCount", metrics.threadCount)
    generator.writeNumberField("peakThreadCount", metrics.peakThreadCount)
    generator.writeBytesField("committedHeap", metrics.committedHeap, convertToMegaBytes = true, unitField = false)
    generator.writeBytesField("maxHeap", metrics.maxHeap, convertToMegaBytes = true, unitField = false)
    generator.writeBytesField("usedHeap", metrics.usedHeap, convertToMegaBytes = true, unitField = false)
    generator.writeBytesField("committedNonHeap", metrics.committedNonHeap, convertToMegaBytes = true, unitField = false)
    generator.writeBytesField("maxNonHeap", metrics.maxNonHeap, convertToMegaBytes = true, unitField = false)
    generator.writeBytesField("usedNonHeap", metrics.usedNonHeap, convertToMegaBytes = true, unitField = false)
    generator.writeBytesUnitField("heapMemoryUnit", megaBytes = true)
    generator.writeNumberField("gcCountPerMinute", metrics.gcCountPerMinute)
    generator.writeNumberField("gcTimePercent", metrics.gcTimePercent)
    generator.writeNumberField("loadAverage", metrics.systemLoadAverage)

    for (additional ← metrics.additional) {
      val cpu = additional.cpu
      generator.writeNumberField("loadAverage5min", cpu.loadAverage5min)
      generator.writeNumberField("loadAverage15min", cpu.loadAverage15min)
      generator.writeNumberField("cpuCombined", cpu.cpuCombined)
      generator.writeNumberField("cpuSys", cpu.cpuSys)
      generator.writeNumberField("cpuUser", cpu.cpuUser)
      generator.writeNumberField("pidCpu", cpu.pidCpu)
      if (cpu.contextSwitches > 0) {
        generator.writeNumberField("contextSwitches", cpu.contextSwitches)
      }

      val mem = additional.memory
      generator.writeNumberField("memUsage", mem.memUsage)
      generator.writeStringField("memUsageUnit", "%")
      generator.writeNumberField("memSwapPageIn", mem.memSwapPageIn)
      generator.writeNumberField("memSwapPageOut", mem.memSwapPageOut)

      val net = additional.network
      generator.writeNumberField("tcpCurrEstab", net.tcpCurrEstab)
      generator.writeNumberField("tcpEstabResets", net.tcpEstabResets)
      generator.writeNumberField("netRxBytesRate", net.netRxBytesRate)
      generator.writeStringField("netRxBytesRateUnit", "bytes/second")
      generator.writeNumberField("netTxBytesRate", net.netTxBytesRate)
      generator.writeStringField("netTxBytesRateUnit", "bytes/second")
      generator.writeNumberField("netRxErrors", net.netRxErrors)
      generator.writeNumberField("netTxErrors", net.netTxErrors)
    }

  }
}

