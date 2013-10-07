/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.util

import spray.http.{ HttpHeader, HttpCharsetRange, Rendering }

object QueryHttpHeaders {

  case class AccessControlAllowOrigin(v: String) extends HttpHeader {
    def name = "Access-Control-Allow-Origin"
    def lowercaseName = "access-control-allow-origin"
    def value = v
    def render[R <: Rendering](r: R): r.type = r ~~ name ~~ ':' ~~ ' ' ~~ value
  }

}