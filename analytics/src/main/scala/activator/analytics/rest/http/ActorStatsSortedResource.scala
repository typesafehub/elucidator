/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.data._
import activator.analytics.repository.{ ActorStatsRepository, ActorStatsSorted }
import GatewayActor._
import java.io.StringWriter
import org.codehaus.jackson.JsonGenerator
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import activator.analytics.AnalyticsExtension
import activator.analytics.data.Sorting._
import java.lang.IllegalArgumentException

class ActorStatsSortedResource(repository: ActorStatsRepository) extends RestResourceActor {
  import ActorStatsSortedResource._

  final val analyticsExtension = AnalyticsExtension(context.system)

  final val queryBuilder = new QueryBuilder(analyticsExtension.DefaultLimit)

  def jsonRepresentation(request: HttpRequest) =
    new ActorStatsSortedJsonRepresentation(baseUrl(request), formatTimestamps(request), context.system)

  def handle(req: HttpRequest): HttpResponse = {
    queryBuilder.build(req.uri.path.toString, req.uri.query.toString) match {
      case Right(query) ⇒
        val stats = repository.findSorted(
          timeRange = query.timeRange,
          scope = query.scope,
          includeAnonymous = analyticsExtension.IncludeAnonymousActorPathsInMetadata,
          includeTemp = analyticsExtension.IncludeTempActorPathsInMetadata,
          offset = query.offset,
          limit = query.limit,
          sortOn = query.sortOn,
          sortDirection = query.sortDirection)
        HttpResponse(entity = jsonRepresentation(req).toJson(stats), headers = HeadersBuilder.headers(query.timeRange.endTime)).asJson
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message).asJson
    }
  }
}

object ActorStatsSortedResource {
  case class Query(timeRange: TimeRange, scope: Scope, offset: Int, limit: Int, sortOn: ActorStatsSort, sortDirection: SortDirection)

  final val SortOnPattern = """^.*sortOn=([\w\-]+)&?.*?""".r

  class QueryBuilder(defaultLimit: Int) extends TimeRangeQueryBuilder with ScopeQueryBuilder with ChunkQueryBuilder with PagingQueryBuilder {
    import SortingHelpers._

    def build(url: String, queryParams: String): Either[String, Query] = {
      def extractSortOn(queryParams: String): ActorStatsSort = queryParams match {
        case SortOnPattern(sort) ⇒ sort match {
          case "deviation"        ⇒ ActorStatsSorts.DeviationsSort
          case "maxTimeInMailbox" ⇒ ActorStatsSorts.MaxTimeInMailboxSort
          case "maxMailboxSize"   ⇒ ActorStatsSorts.MaxMailboxSizeSort
          case "actorPath"        ⇒ ActorStatsSorts.ActorPath
          case "actorName"        ⇒ ActorStatsSorts.ActorName
          case _                  ⇒ ActorStatsSorts.ProcessedMessagesSort
        }
        case _ ⇒ ActorStatsSorts.ProcessedMessagesSort
      }

      def extractSortDirection(queryPath: String): SortDirection = queryPath match {
        case DefaultDescendingExtractor(direction) ⇒ direction match {
          case Right(x)  ⇒ x
          case Left(msg) ⇒ throw new IllegalArgumentException(msg)
        }
      }

      extractTime(queryParams) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒
          extractScope(url, queryParams) match {
            case Left(message) ⇒ Left(message)
            case Right(scope) ⇒
              val limit = extractLimit(queryParams) getOrElse defaultLimit
              val offset = extractOffset(queryParams) getOrElse 0
              val sortOn = extractSortOn(queryParams)
              val sortDirection = extractSortDirection(queryParams)
              // Ideally, 'Query' should take a 'SortDirection' type, not a string
              // NO STRINGLY PROGRAMMING! It's Scala damnit! ;-)
              Right(Query(timeRange, scope, offset, limit, sortOn, sortDirection.direction))
          }
      }
    }
  }
}

class ActorStatsSortedJsonRepresentation(
  override val baseUrl: Option[String],
  override val formatTimestamps: Boolean,
  system: ActorSystem)
  extends JsonRepresentation {

  val actorStatsRepresentation = new ActorStatsJsonRepresentation(baseUrl, formatTimestamps, system)

  def toJson(sortedStats: ActorStatsSorted): String = {
    val writer = new StringWriter
    val generator = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(sortedStats, generator)
    generator.flush()
    writer.toString
  }

  def writeJson(sortedStats: ActorStatsSorted, generator: JsonGenerator) {
    generator.writeStartObject()

    writeJson(sortedStats.timeRange, generator)
    writeJson(sortedStats.scope, generator)

    generator.writeArrayFieldStart("actors")
    for (stats ← sortedStats.stats) {
      actorStatsRepresentation.writeJson(stats, generator)
    }
    generator.writeEndArray()

    generator.writeNumberField("offset", sortedStats.offset)
    generator.writeNumberField("limit", sortedStats.limit)
    generator.writeNumberField("total", sortedStats.total)
    generator.writeEndObject()
  }
}
