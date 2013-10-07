/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data.{ TimeRange, MailboxTimeSeriesPoint, MailboxTimeSeries }
import activator.analytics.rest.RestExtension
import activator.analytics.repository.MailboxTimeSeriesRepository
import GatewayActor._
import java.io.StringWriter
import java.io.Writer
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }

class MailboxTimeSeriesResource(repository: MailboxTimeSeriesRepository)
  extends RestResourceActor with TimeSeriesSampling[MailboxTimeSeriesPoint] {

  final val queryBuilder = new MailboxTimeSeriesResource.QueryBuilder

  def jsonRepresentation(request: HttpRequest) =
    new MailboxTimeSeriesJsonRepresentation(formatTimestamps(request), context.system)

  override val configMaxPoints = RestExtension(context.system).MaxTimeriesPoints

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val timeSeries = repository.findWithinTimePeriod(query.timeRange, query.actorPath)
        val result = MailboxTimeSeries.concatenate(timeSeries, query.timeRange, query.actorPath)
        val sampledResult = sample(result, query.sampling, query.maxPoints)
        HttpResponse(entity = jsonRepresentation(req).toJson(sampledResult), headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }
}

object MailboxTimeSeriesResource {

  import GatewayActor._
  import GatewayActor.PatternChars._
  val ActorPathPattern = ("""^.*""" + MailboxTimeSeriesUri + "/(" + PathChars + ")").r

  case class Query(
    timeRange: TimeRange,
    actorPath: String,
    sampling: Option[Int],
    maxPoints: Option[Int])

  class QueryBuilder extends TimeRangeQueryBuilder with SamplingQueryBuilder {

    def build(url: String, queryParams: String): Either[String, Query] = {

      def buildMore(url: String, queryParams: String, actorPath: String): Either[String, Query] = {
        extractTime(queryParams) match {
          case Left(message) ⇒ Left(message)
          case Right(timeRange) ⇒
            val sampling = extractSampling(queryParams)
            val maxPoints = extractMaxPoints(queryParams)
            Right(Query(timeRange, actorPath, sampling, maxPoints))
        }
      }

      URLDecoder.decode(url) match {
        case ActorPathPattern(actorPath) ⇒
          buildMore(url, queryParams, actorPath)
        case _ ⇒ Left("actorPath not defined in [%s]".format(url))
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

class MailboxTimeSeriesJsonRepresentation(override val formatTimestamps: Boolean, system: ActorSystem) extends JsonRepresentation {

  def toJson(timeSeries: MailboxTimeSeries): String = {
    val writer = new StringWriter
    writeJson(timeSeries, writer)
    writer.toString
  }

  def writeJson(timeSeries: MailboxTimeSeries, writer: Writer) {
    val gen = createJsonGenerator(writer, RestExtension(system).JsonPrettyPrint)
    gen.writeStartObject()

    writeJson(timeSeries.timeRange, gen)
    gen.writeStringField("actorPath", timeSeries.path)
    gen.writeDurationTimeUnitField("waitTimeUnit")
    gen.writeArrayFieldStart("points")
    for (point ← timeSeries.points) {
      gen.writeStartObject()
      writeJson(point, gen)
      gen.writeEndObject()
    }
    gen.writeEndArray()

    gen.writeEndObject()
    gen.flush()
  }

  def writeJson(point: MailboxTimeSeriesPoint, generator: JsonGenerator) {
    generator.writeTimestampField("timestamp", point.timestamp)
    generator.writeNumberField("size", point.size)
    generator.writeDurationField("waitTime", point.waitTime, unitField = false)
  }

}
