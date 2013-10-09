/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ TimeRange, DispatcherTimeSeriesPoint, DispatcherTimeSeries }
import activator.analytics.repository.DispatcherTimeSeriesRepository
import java.io.{ Writer, StringWriter }
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpRequest, HttpResponse }
import activator.analytics.AnalyticsExtension

class DispatcherTimeSeriesResource(dispatcherTimeSeriesRepository: DispatcherTimeSeriesRepository)
  extends RestResourceActor with TimeSeriesSampling[DispatcherTimeSeriesPoint] {

  import GatewayActor._
  import DispatcherTimeSeriesResource._

  def jsonRepresentation(request: HttpRequest) = new DispatcherTimeSeriesRepresentation(formatTimestamps(request), context.system)

  final val queryBuilder = new QueryBuilder
  override val configMaxPoints = AnalyticsExtension(context.system).MaxTimeriesPoints

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val timeSeries = dispatcherTimeSeriesRepository.findWithinTimePeriod(query.timeRange, query.node, query.actorSystem, query.dispatcher)
        val result = DispatcherTimeSeries.concatenate(timeSeries, query.timeRange, query.node, query.actorSystem, query.dispatcher)
        val sampledResult = sample(result, query.sampling, query.maxPoints)
        HttpResponse(entity = jsonRepresentation(req).toJson(sampledResult), headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }
}

object DispatcherTimeSeriesResource {
  import GatewayActor._
  import GatewayActor.PatternChars._
  private val NodeSystemDispatcher = "/(" + NodeChars + ")/(" + ActorSystemChars + ")/(" + DispatcherChars + ")"
  val DispatcherPattern = ("""^.*""" + DispatcherTimeSeriesUri + NodeSystemDispatcher).r

  case class Query(
    timeRange: TimeRange,
    node: String,
    actorSystem: String,
    dispatcher: String,
    sampling: Option[Int],
    maxPoints: Option[Int])

  class QueryBuilder extends TimeRangeQueryBuilder with SamplingQueryBuilder {
    def build(url: String, queryParams: String): Either[String, Query] = {
      def buildMore(queryParams: String, node: String, actorSystem: String, dispatcher: String): Either[String, Query] = {
        extractTime(queryParams) match {
          case Left(msg) ⇒ Left(msg)
          case Right(timeRange) ⇒
            val sampling = extractSampling(queryParams)
            val maxPoints = extractMaxPoints(queryParams)
            Right(Query(timeRange, node, actorSystem, dispatcher, sampling, maxPoints))
        }
      }

      url match {
        case DispatcherPattern(node, actorSystem, dispatcher) ⇒ buildMore(queryParams, node, actorSystem, dispatcher)
        case _ ⇒ Left("Invalid URI: " + url)
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

class DispatcherTimeSeriesRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  val pointRepresentation = new DispatcherTimeSeriesPointRepresentation(formatTimestamps, system)

  def toJson(stats: DispatcherTimeSeries, writer: Writer) = {
    val generator = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(stats, generator)
    generator.flush()
  }

  def toJson(stats: DispatcherTimeSeries): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(stats, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(stats: DispatcherTimeSeries, generator: JsonGenerator) {
    generator.writeStartObject()
    generator.writeStringField("node", stats.node)
    generator.writeStringField("actorSystem", stats.actorSystem)
    generator.writeStringField("dispatcher", stats.dispatcher)
    generator.writeStringField("dispatcherType", stats.dispatcherType)
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

class DispatcherTimeSeriesPointRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {
  def toJson(point: DispatcherTimeSeriesPoint): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(point, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(point: DispatcherTimeSeriesPoint, generator: JsonGenerator) {
    val metrics = point.metrics
    generator.writeTimestampField("timestamp", point.timestamp)
    generator.writeNumberField("corePoolSize", metrics.corePoolSize)
    generator.writeNumberField("maximumPoolSize", metrics.maximumPoolSize)
    generator.writeNumberField("keepAliveTime", metrics.keepAliveTime)
    generator.writeStringField("rejectedHandler", metrics.rejectedHandler)
    generator.writeNumberField("activeThreadCount", metrics.activeThreadCount)
    generator.writeNumberField("taskCount", metrics.taskCount)
    generator.writeNumberField("completedTaskCount", metrics.completedTaskCount)
    generator.writeNumberField("largestPoolSize", metrics.largestPoolSize)
    generator.writeNumberField("poolSize", metrics.poolSize)
    generator.writeNumberField("queueSize", metrics.queueSize)
  }
}
