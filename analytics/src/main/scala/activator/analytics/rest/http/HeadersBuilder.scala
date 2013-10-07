/**
 *  Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import spray.http._

object HeadersBuilder {
  type Headers = List[Header]
  type Header = Tuple2[String, String]

  val useCacheControlWhenOlderThanMillis = 10 * 60 * 1000 // 10 minutes
  val cacheControlMaxAge = 4 * 3600 // 4 hours
  val JsonContentType = MediaTypes.`application/json`
  val PngContentType = MediaTypes.`image/png`

  def headers(RestEndTime: Long): List[HttpHeader] = {
    headers(RestEndTime, JsonContentType)
  }

  def headers(contentType: MediaType): List[HttpHeader] = {
    List(HttpHeaders.`Content-Type`(ContentType(contentType)))
  }

  def headers(RestEndTime: Long, contentType: MediaType): List[HttpHeader] = {
    headers(RestEndTime < (System.currentTimeMillis - useCacheControlWhenOlderThanMillis), contentType)
  }

  def headers(useCacheControl: Boolean, contentType: MediaType = JsonContentType): List[HttpHeader] = {
    if (useCacheControl)
      List(
        HttpHeaders.`Cache-Control`(CacheDirectives.`max-age`(cacheControlMaxAge)),
        HttpHeaders.`Content-Type`(ContentType(contentType)))
    else List()
  }
}
