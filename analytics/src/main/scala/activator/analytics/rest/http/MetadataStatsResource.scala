/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.{ ActorSystem, ActorPath, RootActorPath }
import activator.analytics.data._
import activator.analytics.rest.http.MetadataStatsResource._
import activator.analytics.repository._
import java.io.StringWriter
import java.util.TreeMap
import org.codehaus.jackson.JsonGenerator
import scala.annotation.tailrec
import scala.concurrent._
import scala.concurrent.duration._
import spray.http.{ StatusCodes, HttpResponse, HttpRequest }
import activator.analytics.AnalyticsExtension

class MetadataStatsResource(
  metadataStatsRepository: MetadataStatsRepository,
  summarySpanStatsRepository: SummarySpanStatsRepository,
  errorStatsRepository: ErrorStatsRepository,
  actorStatsRepository: ActorStatsRepository) extends RestResourceActor {

  final val queryBuilder = new QueryBuilder(AnalyticsExtension(context.system).DefaultLimit)
  final val jsonRepresentation = new MetadataJsonRepresentation(context.system)
  final val retriever = new MetadataStatsRetriever(context.system, metadataStatsRepository, summarySpanStatsRepository, errorStatsRepository, actorStatsRepository)

  def handle(req: HttpRequest): HttpResponse = {
    val analyticsExtension = AnalyticsExtension(context.system)

    queryBuilder.build(req.uri.query.toString) match {
      case Right(query) ⇒
        val path = req.uri.path.toString
        val headers = HeadersBuilder.headers(query.timeRange.endTime)

        if (path.endsWith(PathsName)) {
          val metadata = retriever.retrieveActorPathTreeMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(metadata), headers = headers).asJson
        } else if (path.endsWith(DispatchersName)) {
          val metadata = retriever.retrieveDispatchersMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(metadata), headers = headers).asJson
        } else if (path.endsWith(NodesName)) {
          val metadata = retriever.retrieveNodesMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(metadata), headers = headers).asJson
        } else if (path.endsWith(ActorSystemName)) {
          val metadata = retriever.retrieveActorSystemsMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(metadata), headers = headers)
        } else if (path.endsWith(SpanTypesName)) {
          val metadata = retriever.retrieveMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          val formattedSpanTypes: Set[String] = metadata.metrics.spanTypes.map(SpanType.formatSpanTypeName(_))
          HttpResponse(entity = jsonRepresentation.toJson(SpanTypesName, formattedSpanTypes), headers = headers).asJson
        } else if (path.endsWith(TagsName)) {
          val metadata = retriever.retrieveMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(TagsName, metadata.metrics.tags), headers = headers).asJson
        } else if (path.endsWith(PlayPatternsName)) {
          val metadata = retriever.retrieveMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(PlayPatternsName, metadata.metrics.playPatterns), headers = headers).asJson
        } else if (path.endsWith(PlayControllersName)) {
          val metadata = retriever.retrieveMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(PlayControllersName, metadata.metrics.playControllers), headers = headers).asJson
        } else {
          // No match above -> use all metadata in response
          val metadata = retriever.retrieveFullMetadata(
            query.timeRange,
            query.scope,
            analyticsExtension.IncludeAnonymousActorPathsInMetadata,
            analyticsExtension.IncludeTempActorPathsInMetadata,
            query.limit)
          HttpResponse(entity = jsonRepresentation.toJson(metadata), headers = headers).asJson
        }
      case Left(message) ⇒
        HttpResponse(status = StatusCodes.BadRequest, entity = message)
    }
  }
}

class MetadataStatsRetriever(
  system: ActorSystem,
  metadataStatsRepository: MetadataStatsRepository,
  summarySpansStatsRepository: SummarySpanStatsRepository,
  errorStatsRepository: ErrorStatsRepository,
  actorStatsRepository: ActorStatsRepository) {

  def retrieveNodesMetadata(timeRange: TimeRange, scope: Scope, includeAnonymous: Boolean, includeTemp: Boolean, limit: Int): Metadata = {
    val metadataStats = retrieveMetadata(timeRange, scope, includeAnonymous, includeTemp, limit)

    val nodesMetadata = for {
      node ← metadataStats.metrics.nodes
      scope = Scope(node = Some(node))
      deviationCount = retrieveDeviationCount(timeRange, scope)
    } yield NodeMetadata(node = node, deviationCount = deviationCount)

    Metadata(nodes = nodesMetadata.toSet)
  }

  def retrieveActorSystemsMetadata(timeRange: TimeRange, scope: Scope, includeAnonymous: Boolean, includeTemp: Boolean, limit: Int): Metadata = {
    val actorSystemsByNode = retrieveActorSystemsByNode(timeRange, scope, includeAnonymous, includeTemp, limit)

    val actorSystemsMetadata = for {
      (node, actorSystems) ← actorSystemsByNode
      actorSystem ← actorSystems
      scope = Scope(node = Some(node), actorSystem = Some(actorSystem))
      deviationCount = retrieveDeviationCount(timeRange, scope)
    } yield ActorSystemMetadata(actorSystem = actorSystem, node = node, deviationCount = deviationCount)

    Metadata(actorSystems = actorSystemsMetadata.toSet)
  }

  def retrieveActorSystemsByNode(timeRange: TimeRange, scope: Scope, includeAnonymous: Boolean, includeTemp: Boolean, limit: Int): Map[String, Set[String]] = {
    val metadataStats = retrieveMetadata(timeRange, scope, includeAnonymous, includeTemp, limit)
    (for {
      node ← metadataStats.metrics.nodes
    } yield {
      val actorSystems = retrieveMetadata(timeRange, scope.copy(node = Some(node)), includeAnonymous, includeTemp, limit).metrics.actorSystems
      (node -> actorSystems)
    }).toMap
  }

  def retrieveDispatchersMetadata(timeRange: TimeRange, scope: Scope, includeAnonymous: Boolean, includeTemp: Boolean, limit: Int): Metadata = {
    val actorSystemsByNode = retrieveActorSystemsByNode(timeRange, scope, includeAnonymous, includeTemp, limit)

    val dispatchersByNodeAndActorSystem: Map[(String, String), Set[String]] =
      (for {
        (node, actorSystems) ← actorSystemsByNode
        as ← actorSystems
      } yield {
        val dispatchers = retrieveMetadata(timeRange, Scope(node = Some(node), actorSystem = Some(as)), includeAnonymous, includeTemp, limit).metrics.dispatchers
        ((node, as) -> dispatchers)
      }).toMap

    val dispatchersMetadata = for {
      (node, actorSystems) ← actorSystemsByNode
      actorSystem ← actorSystems
      dispatcher ← dispatchersByNodeAndActorSystem((node, actorSystem))
      scope = Scope(node = Some(node), actorSystem = Some(actorSystem), dispatcher = Some(dispatcher))
      deviationCount = retrieveDeviationCount(timeRange, scope)
    } yield DispatcherMetadata(dispatcher = dispatcher, actorSystem = actorSystem, node = node, deviationCount = deviationCount)

    Metadata(dispatchers = dispatchersMetadata.toSet)
  }

  def retrieveDeviationCount(timeRange: TimeRange, scope: Scope): Long = {
    if (scope.dispatcher.isDefined) {
      // use actor stats as dispatcher scope is not defined for error stats
      val actorStats = actorStatsRepository.findWithinTimePeriod(timeRange, scope)
      ActorStats.concatenate(actorStats, timeRange, scope).metrics.counts.deviationCount
    } else {
      val errorStats = errorStatsRepository.findWithinTimePeriod(timeRange, scope.node, scope.actorSystem)
      ErrorStats.concatenate(errorStats, timeRange, scope.node, scope.actorSystem).metrics.counts.total
    }
  }

  private def sortPaths(paths: Set[String]): Seq[String] = paths.toSeq.sortWith((a, b) ⇒ a < b)

  // distribute a limit over all available actors
  private def distributeLimit(stats: Map[String, MetadataStats], limit: Int): Map[String, Int] = {
    val limitClaims = stats.toSeq map {
      case (key, stats) ⇒ (key, stats.metrics.totalActorCount.getOrElse(0), 0)
    }
    distribute(limitClaims, limit).map(claim ⇒ claim._1 -> claim._3).toMap
  }

  // distribute a limit over claimants, recurse until the limit is used up
  // claim is a tuple of (name/key, free to assign count, assigned count)
  private def distribute(claims: Seq[(String, Int, Int)], remaining: Int): Seq[(String, Int, Int)] = {
    if (remaining <= 0) return claims
    val claimants = claims.count(_._2 > 0)
    if (claimants == 0) return claims
    val share = math.max(remaining / claimants, 1)
    var available = remaining
    val distributed = claims.map {
      case claim @ (name, free, assigned) ⇒
        if (available >= share && free > 0) {
          val taken = math.min(free, share)
          available -= taken
          (name, free - taken, assigned + taken)
        } else claim
    }
    distribute(distributed, available)
  }

  def retrieveActorPathTreeMetadata(
    timeRange: TimeRange,
    scope: Scope,
    includeAnonymous: Boolean,
    includeTemp: Boolean,
    limit: Int): Metadata = {

    def key(node: String, actorSystem: String) = node + "/" + actorSystem

    val actorSystemsByNode = retrieveActorSystemsByNode(timeRange, scope, includeAnonymous, includeTemp, limit)

    val stats = for {
      (node, actorSystems) ← actorSystemsByNode
      actorSystem ← actorSystems
    } yield {
      key(node, actorSystem) -> retrieveMetadata(timeRange, scope.copy(node = Some(node), actorSystem = Some(actorSystem)), includeAnonymous, includeTemp, limit)
    }

    val limits = distributeLimit(stats, limit)

    var totalActors = 0
    val nodeTree: Map[String, PathTreeNodeMetadata] = Map.empty ++
      (for ((node, actorSystems) ← actorSystemsByNode) yield {
        val actorSystemTree: Map[String, PathTreeActorSystemMetadata] = Map.empty ++
          (for (as ← actorSystems) yield {
            val statsKey = key(node, as)
            val metadataStats = stats(statsKey)
            val givenLimit = limits(statsKey)
            val paths = sortPaths(metadataStats.metrics.paths).take(givenLimit)
            val actorCount = metadataStats.metrics.totalActorCount.getOrElse(0)
            totalActors += actorCount
            val actorTree = PathTree.build(paths)
            as -> PathTreeActorSystemMetadata(math.min(givenLimit, actorCount), actorCount, actorTree)
          })
        node -> PathTreeNodeMetadata(actorSystemTree)
      })

    Metadata(paths = Some(PathTreeMetadata(totalActors, nodeTree)))
  }

  def retrieveMetadata(
    timeRange: TimeRange,
    scope: Scope,
    includeAnonymous: Boolean,
    includeTemp: Boolean,
    limit: Int): MetadataStats = {
    implicit val s = system
    implicit val ec = system.dispatcher
    val spanStatFuture = Future { summarySpansStatsRepository.findMetadata(timeRange, scope, includeAnonymous, includeTemp) }
    val metadataStats = metadataStatsRepository.findFiltered(timeRange, scope, includeAnonymous, includeTemp)
    val spanStatMetadata = Await.result(spanStatFuture, 5.seconds)
    mergeMetadata(spanStatMetadata, metadataStats, limit)
  }

  def mergeMetadata(spanStatMetadata: MetadataStats, metadataStats: MetadataStats, limit: Int): MetadataStats = {
    val allPaths = (spanStatMetadata.metrics.paths ++ metadataStats.metrics.paths)
    val limitedPaths = sortPaths(allPaths).take(limit).toSet
    new MetadataStats(
      timeRange = metadataStats.timeRange,
      scope = metadataStats.scope,
      metrics = MetadataStatsMetrics(
        spanTypes = spanStatMetadata.metrics.spanTypes,
        paths = limitedPaths,
        totalActorCount = Some(allPaths.size),
        dispatchers = spanStatMetadata.metrics.dispatchers ++ metadataStats.metrics.dispatchers,
        nodes = spanStatMetadata.metrics.nodes ++ metadataStats.metrics.nodes,
        actorSystems = spanStatMetadata.metrics.actorSystems ++ metadataStats.metrics.actorSystems,
        tags = spanStatMetadata.metrics.tags ++ metadataStats.metrics.tags,
        playPatterns = metadataStats.metrics.playPatterns,
        playControllers = metadataStats.metrics.playControllers))
  }

  def retrieveFullMetadata(timeRange: TimeRange, scope: Scope, includeAnonymous: Boolean, includeTemp: Boolean, limit: Int): MetadataStats = {
    val metadataStats = retrieveMetadata(timeRange, scope, includeAnonymous, includeTemp, limit)
    val actorSystemsMetadata = retrieveActorSystemsMetadata(timeRange, scope, includeAnonymous, includeTemp, limit)
    val dispatchersMetadata = retrieveDispatchersMetadata(timeRange, scope, includeAnonymous, includeTemp, limit)
    new MetadataStats(
      timeRange = metadataStats.timeRange,
      scope = metadataStats.scope,
      metrics = metadataStats.metrics.copy(
        actorSystemCount = Some(actorSystemsMetadata.actorSystems.size),
        dispatcherCount = Some(dispatchersMetadata.dispatchers.size)))
  }
}

object MetadataStatsResource {

  val MetadataUri = GatewayActor.MetadataStatsUri
  val NodesUri = MetadataUri + "/nodes"
  val ActorSystemsUri = MetadataUri + "/actorSystems"
  val SpanTypesUri = MetadataUri + "/spanTypes"
  val PathsUri = MetadataUri + "/actorPaths"
  val PlayPatternsUri = MetadataUri + "/playPatterns"
  val PlayControllersUri = MetadataUri + "/playControllers"
  val DispatchersUri = MetadataUri + "/dispatchers"
  val TagsUri = MetadataUri + "/tags"

  case class Metadata(
    spanTypes: Set[String] = Set.empty,
    paths: Option[PathTreeMetadata] = None,
    tags: Set[String] = Set.empty,
    nodes: Set[NodeMetadata] = Set.empty,
    actorSystems: Set[ActorSystemMetadata] = Set.empty,
    dispatchers: Set[DispatcherMetadata] = Set.empty,
    playPatterns: Set[PlayPatternMetadata] = Set.empty,
    playControllers: Set[PlayControllerMetadata] = Set.empty)

  case class NodeMetadata(
    node: String,
    deviationCount: Long)

  case class ActorSystemMetadata(
    node: String,
    actorSystem: String,
    deviationCount: Long)

  case class DispatcherMetadata(
    node: String,
    actorSystem: String,
    dispatcher: String,
    deviationCount: Long)

  case class PlayPatternMetadata(
    pattern: String,
    node: String)

  case class PlayControllerMetadata(
    pattern: String,
    node: String)

  case class PathTreeMetadata(totalActors: Int, nodes: Map[String, PathTreeNodeMetadata])

  case class PathTreeNodeMetadata(actorSystems: Map[String, PathTreeActorSystemMetadata])

  case class PathTreeActorSystemMetadata(retrievedActors: Int, totalActors: Int, actorTree: Seq[PathTree])

  class PathTree(val name: String, val children: Seq[PathTree])

  object PathTree {
    def pathElements(path: ActorPath): Seq[String] = path match {
      case root: RootActorPath ⇒ Seq.empty
      case p                   ⇒ p.elements.toSeq
    }

    def build(paths: Seq[String]): Seq[PathTree] = {
      val treeBuilder = PathTreeBuilder.root
      paths foreach { path ⇒ treeBuilder.add(pathElements(ActorPath.fromString(path))) }
      treeBuilder.build
    }
  }

  class PathTreeBuilder(val name: String, val map: TreeMap[String, PathTreeBuilder], var active: Boolean) {
    def add(path: Seq[String]): Unit = PathTreeBuilder.add(this, path)

    def build(): Seq[PathTree] = PathTreeBuilder.build(this, Seq.empty, name, 1)

    def child(key: String): PathTreeBuilder = {
      if (map.containsKey(key)) {
        map.get(key)
      } else {
        val builder = PathTreeBuilder.empty(key)
        map.put(key, builder)
        builder
      }
    }
  }

  object PathTreeBuilder {
    val maxDepth = 1000

    def root: PathTreeBuilder = empty("/")

    def empty(name: String): PathTreeBuilder = new PathTreeBuilder(name, new TreeMap[String, PathTreeBuilder](), false)

    @tailrec
    def add(tree: PathTreeBuilder, path: Seq[String]): Unit = {
      if (path.isEmpty) tree.active = true
      else add(tree.child(path.head), path.tail)
    }

    def build(tree: PathTreeBuilder, path: Seq[String], prefix: String, depth: Int): Seq[PathTree] = {
      import scala.collection.JavaConverters._
      if (depth > maxDepth) {
        Seq(new PathTree(prefix + path.mkString("/"), Nil))
      } else if (tree.active) {
        val children = tree.map.values.asScala.toList flatMap { c ⇒ build(c, Seq(c.name), "", depth + 1) }
        Seq(new PathTree(prefix + path.mkString("/"), children))
      } else {
        tree.map.values.asScala.toList flatMap { c ⇒ build(c, path :+ c.name, prefix, depth + 1) }
      }
    }
  }

  case class Query(
    timeRange: TimeRange,
    scope: Scope,
    offset: Int,
    limit: Int)

  class QueryBuilder(defaultLimit: Int) extends TimeRangeQueryBuilder with ScopeQueryBuilder with PagingQueryBuilder {
    def build(queryPath: String): Either[String, Query] = {
      extractTime(queryPath) match {
        case Left(message) ⇒ Left(message)
        case Right(timeRange) ⇒
          extractNode(queryPath) match {
            case Left(message) ⇒ Left(message)
            case Right(node) ⇒
              val dispatcher = extractDispatcher(queryPath)
              val tag = extractTag(queryPath)
              val offset = extractOffset(queryPath) getOrElse 1
              val limit = extractLimit(queryPath) getOrElse defaultLimit
              val playPattern = extractPlayPattern(queryPath)
              val playController = extractPlayController(queryPath)
              extractActorSystem(queryPath) match {
                case Left(message) ⇒ Left(message)
                case Right(actorSystem) ⇒
                  Right(Query(
                    timeRange = timeRange,
                    scope = Scope(node = node, actorSystem = actorSystem, tag = tag, dispatcher = dispatcher, playPattern = playPattern, playController = playController),
                    offset = offset,
                    limit = limit))
              }
          }
      }
    }
  }

  val PathsName = "actorPaths"
  val DispatchersName = "dispatchers"
  val NodesName = "nodes"
  val ActorSystemName = "actorSystems"
  val SpanTypesName = "spanTypes"
  val TagsName = "tags"
  val PlayPatternsName = "playPatterns"
  val PlayControllersName = "playControllers"
}

class MetadataJsonRepresentation(system: ActorSystem) extends JsonRepresentation {

  def toJson(metadata: MetadataStats): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(metadata, gen)
    gen.flush()
    writer.toString
  }

  def writeJson(metadata: MetadataStats, gen: JsonGenerator) {
    gen.writeStartObject()

    writeSet(MetadataStatsResource.PathsName, metadata.metrics.paths, gen)
    gen.writeNumberField("actorPathCount", metadata.metrics.totalActorCount.getOrElse(0))
    writeSet(MetadataStatsResource.NodesName, metadata.metrics.nodes, gen)
    gen.writeNumberField("nodeCount", metadata.metrics.nodes.size)
    writeSet(MetadataStatsResource.ActorSystemName, metadata.metrics.actorSystems, gen)
    gen.writeNumberField("actorSystemCount", metadata.metrics.actorSystemCount.getOrElse(metadata.metrics.actorSystems.size))
    writeSet(MetadataStatsResource.DispatchersName, metadata.metrics.dispatchers, gen)
    gen.writeNumberField("dispatcherCount", metadata.metrics.dispatcherCount.getOrElse(metadata.metrics.dispatchers.size))
    writeSet(MetadataStatsResource.PlayPatternsName, metadata.metrics.playPatterns, gen)
    gen.writeNumberField("playPatternCount", metadata.metrics.playPatterns.size)
    writeSet(MetadataStatsResource.PlayControllersName, metadata.metrics.playControllers, gen)
    gen.writeNumberField("playControllerCount", metadata.metrics.playControllers.size)

    val formattedSpanTypes: Set[String] = metadata.metrics.spanTypes.map(SpanType.formatSpanTypeName(_))
    writeSet(MetadataStatsResource.SpanTypesName, formattedSpanTypes, gen)
    writeSet(MetadataStatsResource.TagsName, metadata.metrics.tags, gen)
    gen.writeNumberField("tagCount", metadata.metrics.tags.size)

    gen.writeEndObject()
  }

  def toJson(metadata: Metadata): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    writeJson(metadata, gen)
    gen.flush()
    writer.toString
  }

  def writeJson(metadata: Metadata, gen: JsonGenerator) {
    gen.writeStartObject()

    val formattedSpanTypes: Set[String] = metadata.spanTypes.map(SpanType.formatSpanTypeName(_))
    if (metadata.spanTypes.nonEmpty) writeSet(MetadataStatsResource.SpanTypesName, formattedSpanTypes, gen)
    if (metadata.tags.nonEmpty) writeSet(MetadataStatsResource.TagsName, metadata.tags, gen)
    writeNodes(metadata, gen)
    writeActorSystems(metadata, gen)
    writeDispatchers(metadata, gen)
    writeActorPathTree(metadata, gen)

    gen.writeEndObject()
  }

  private def writeNodes(metadata: Metadata, gen: JsonGenerator): Unit = {
    if (metadata.nodes.nonEmpty) {
      gen.writeArrayFieldStart(MetadataStatsResource.NodesName)
      for (nodeMetadata ← metadata.nodes.toSeq.sortBy(_.node)) {
        gen.writeStartObject()
        gen.writeStringField("node", nodeMetadata.node)
        gen.writeNumberField("deviationCount", nodeMetadata.deviationCount)
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }
  }

  private def writeActorSystems(metadata: Metadata, gen: JsonGenerator): Unit = {
    if (metadata.actorSystems.nonEmpty) {
      gen.writeArrayFieldStart(MetadataStatsResource.ActorSystemName)
      for (actorSystemMetadata ← metadata.actorSystems.toSeq.sortBy(as ⇒ as.node + "/" + as.actorSystem)) {
        gen.writeStartObject()
        gen.writeStringField("node", actorSystemMetadata.node)
        gen.writeStringField("actorSystem", actorSystemMetadata.actorSystem)
        gen.writeNumberField("deviationCount", actorSystemMetadata.deviationCount)
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }
  }

  private def writeDispatchers(metadata: Metadata, gen: JsonGenerator): Unit = {
    if (metadata.dispatchers.nonEmpty) {
      gen.writeArrayFieldStart(MetadataStatsResource.DispatchersName)
      for (dispatcherMetadata ← metadata.dispatchers.toSeq.sortBy(d ⇒ d.node + "/" + d.actorSystem + "/" + d.dispatcher)) {
        gen.writeStartObject()
        gen.writeStringField("node", dispatcherMetadata.node)
        gen.writeStringField("actorSystem", dispatcherMetadata.actorSystem)
        gen.writeStringField("dispatcher", dispatcherMetadata.dispatcher)
        gen.writeNumberField("deviationCount", dispatcherMetadata.deviationCount)
        gen.writeEndObject()
      }
      gen.writeEndArray()
    }
  }

  private def writeActorPathTree(metadata: Metadata, gen: JsonGenerator) {

    def writePath(tree: PathTree): Unit = {
      gen.writeStartObject()
      gen.writeStringField("name", tree.name)
      if (tree.children.nonEmpty) {
        gen.writeArrayFieldStart("children")
        for (child ← tree.children) writePath(child)
        gen.writeEndArray()
      }
      gen.writeEndObject()
    }

    if (metadata.paths.isDefined) {
      for (p ← metadata.paths) gen.writeNumberField("totalActors", p.totalActors)
      gen.writeObjectFieldStart(MetadataStatsResource.PathsName)
      gen.writeArrayFieldStart("nodes")
      for (tree ← metadata.paths; (node, nodeTree) ← tree.nodes.toSeq.sortBy(_._1)) {
        gen.writeStartObject()
        gen.writeStringField("node", node)
        gen.writeArrayFieldStart("actorSystems")
        for ((as, asTree) ← nodeTree.actorSystems.toSeq.sortBy(_._1)) {
          gen.writeStartObject()
          gen.writeStringField("actorSystem", as)
          gen.writeNumberField("totalActorSystemActors", asTree.totalActors)
          gen.writeNumberField("retrievedActorSystemActors", asTree.retrievedActors)
          gen.writeArrayFieldStart("paths")
          for (roots ← asTree.actorTree) writePath(roots)
          gen.writeEndArray()

          gen.writeEndObject()
        }
        gen.writeEndArray()
        gen.writeEndObject()
      }
      gen.writeEndArray()
      gen.writeEndObject()
    }
  }

  def toJson(name: String, values: Set[String]): String = {
    val writer = new StringWriter
    val gen = createJsonGenerator(writer, AnalyticsExtension(system).JsonPrettyPrint)
    gen.writeStartObject()

    writeSet(name, values, gen)

    gen.writeEndObject()
    gen.flush()
    writer.toString
  }

  def writeSet(name: String, values: Set[String], gen: JsonGenerator) = {
    gen.writeArrayFieldStart(name)
    for (value ← values.toSeq.sorted) gen.writeString(value)
    gen.writeEndArray()
  }
}
