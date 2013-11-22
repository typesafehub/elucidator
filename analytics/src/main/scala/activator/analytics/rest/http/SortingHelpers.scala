package activator.analytics.rest.http

import java.lang.IllegalArgumentException

object SortingHelpers {
  trait UnapplyHelper[I, O] {
    def unapply(input: I): Option[O]
  }

  object SortDirection {
    def apply(in: String): SortDirection = in match {
      case "asc"  ⇒ Ascending
      case "desc" ⇒ Descending
      case _      ⇒ throw new IllegalArgumentException(s"'$in' is not a valid sort direction.  Must be 'asc' or 'desc'")
    }
  }
  sealed class SortDirection(val direction: String)
  case object Ascending extends SortDirection("asc")
  case object Descending extends SortDirection("desc")

  val SortDirectionArgumentPattern = """^.*sortDirection=([\w\+])&?.*?""".r
  val SortDirectionValuePattern = """(asc|desc)""".r

  def sortDirectionExtractor(default: SortDirection): UnapplyHelper[String, Either[String, SortDirection]] = new UnapplyHelper[String, Either[String, SortDirection]] {
    def unapply(input: String): Option[Either[String, SortDirection]] =
      input match {
        case SortDirectionArgumentPattern(arg) ⇒
          arg match {
            case SortDirectionValuePattern(d) ⇒ Some(Right(SortDirection(d)))
            case _                            ⇒ Some(Left(s"the value '$arg' is not a valid sort direction.  Must be 'asc' or 'desc'"))
          }
        case _ ⇒ Some(Right(default))
      }
  }

  final val DefaultDescendingExtractor = sortDirectionExtractor(Descending)
  final val DefaultAscendingExtractor = sortDirectionExtractor(Ascending)
}
