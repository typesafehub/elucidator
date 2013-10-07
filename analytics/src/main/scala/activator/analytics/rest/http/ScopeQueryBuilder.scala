/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import activator.analytics.data.{ Scope, SpanType }
import activator.analytics.rest.util._

trait ScopeQueryBuilder {
  def extractSpanTypeName(path: String): Either[String, String] = path match {
    case ScopeQueryBuilder.SpanTypePattern(spanTypeName, extension) ⇒
      val predefinedSpanTypeName = SpanType.PredefinedSpanTypesNamePrefix + spanTypeName
      SpanType.forName(predefinedSpanTypeName) match {
        case Some(spanType) ⇒ Right(predefinedSpanTypeName)
        case None ⇒ SpanType.forName(spanTypeName) match {
          case Some(spanType) ⇒ Right(spanTypeName)
          case None           ⇒ Left("Unknown span type [%s]".format(path))
        }
      }
    case _ ⇒ Left("Span type not defined in [%s]".format(path))
  }

  def extractScope(path: String, queryParams: String): Either[String, Scope] = {
    extractNode(queryParams) match {
      case Right(node) ⇒
        extractActorSystem(queryParams) match {
          case Right(actorSystem) ⇒
            val dispatcher = extractDispatcher(queryParams)
            val tag = extractTag(queryParams)
            extractPath(queryParams) match {
              case Right(path) ⇒
                (extractPlayPattern(queryParams), extractPlayController(queryParams)) match {
                  case (pattern @ Some(_), controller @ Some(_)) ⇒ Right(Scope(node = node, actorSystem = actorSystem, dispatcher = dispatcher, tag = tag, path = path, playPattern = pattern, playController = controller))
                  case (pattern @ Some(_), None)                 ⇒ Right(Scope(node = node, actorSystem = actorSystem, dispatcher = dispatcher, tag = tag, path = path, playPattern = pattern))
                  case (None, controller @ Some(_))              ⇒ Right(Scope(node = node, actorSystem = actorSystem, dispatcher = dispatcher, tag = tag, path = path, playController = controller))
                  case (None, None)                              ⇒ Right(Scope(node = node, actorSystem = actorSystem, dispatcher = dispatcher, tag = tag, path = path))
                }
              case Left(message) ⇒ Left(message)
            }
          case Left(message) ⇒ Left(message)
        }
      case Left(message) ⇒ Left(message)
    }
  }

  def extractNode(queryParams: String): Either[String, Option[String]] = queryParams match {
    case ScopeQueryBuilder.NodePattern(node) ⇒ validate(NodeTypeLimiter, node)
    case _                                   ⇒ Right(None)
  }

  def extractActorSystem(queryParams: String): Either[String, Option[String]] = queryParams match {
    case ScopeQueryBuilder.ActorSystemPattern(actorSystem) ⇒ validate(ActorSystemTypeLimiter, actorSystem)
    case _ ⇒ Right(None)
  }

  def extractDispatcher(queryParams: String): Option[String] = queryParams match {
    case ScopeQueryBuilder.DispatcherPattern(dispatcher) ⇒ Some(dispatcher)
    case _ ⇒ None
  }

  def extractTag(queryParams: String): Option[String] = queryParams match {
    case ScopeQueryBuilder.TagPattern(tag) ⇒ Some(tag)
    case _                                 ⇒ None
  }

  def extractPath(queryParams: String): Either[String, Option[String]] = queryParams match {
    case ScopeQueryBuilder.PathPattern(path) ⇒ validate(ActorPathTypeLimiter, path)
    case _                                   ⇒ Right(None)
  }

  def extractPlayPattern(queryParams: String): Option[String] = queryParams match {
    case ScopeQueryBuilder.PlayPatternPattern(pattern) ⇒ Some(pattern)
    case _ ⇒ None
  }

  def extractPlayController(queryParams: String): Option[String] = queryParams match {
    case ScopeQueryBuilder.PlayControllerPattern(controller) ⇒ Some(controller)
    case _ ⇒ None
  }

  private def validate(limiter: LimiterType, payload: String): Either[String, Option[String]] = {
    val check = RestLimiter.isValid(limiter, payload)
    if (check.valid) Right(Some(payload))
    else Left(check.errors.mkString(" : "))
  }
}

object ScopeQueryBuilder {

  import GatewayActor.PatternChars._

  val SpanTypePattern = ("""^.*/span\/.*\/(""" + SpanTypeChars + """)(\.png)?""").r
  val NodePattern = ("""^.*node=(""" + NodeChars + """)&?.*?""").r
  val ActorSystemPattern = ("""^.*actorSystem=(""" + ActorSystemChars + """)&?.*?""").r
  val DispatcherPattern = ("""^.*dispatcher=(""" + DispatcherChars + """)&?.*?""").r
  val TagPattern = ("""^.*tag=(""" + TagChars + """)&?.*?""").r
  val PathPattern = ("""^.*actorPath=(""" + PathChars + """)&?.*?""").r
  val PlayPatternPattern = ("""^.*playPattern=(""" + PlayPatternChars + """)&?.*?""").r
  val PlayControllerPattern = ("""^.*playController=(""" + PlayControllerChars + """)&?.*?""").r
}

