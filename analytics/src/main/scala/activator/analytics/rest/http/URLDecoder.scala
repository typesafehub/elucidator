/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

object URLDecoder {
  def decode(str: String) = {
    if (str eq null) null
    else java.net.URLDecoder.decode(str, "utf-8")
  }
}
