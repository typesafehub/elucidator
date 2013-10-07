/**
 * Copyright (C) 2011-2013 Typesafe <http://typesafe.com/>
 */
package activator.analytics.rest.http

import akka.actor.ActorSystem
import activator.analytics.rest.RestExtension
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import spray.http._

class DashboardResource extends RestResourceActor with FileContentResource {

  def handle(req: HttpRequest): HttpResponse =
    handleFileRequest(req, context.system) match {
      case Some(r) ⇒ r
      case _       ⇒ HttpResponse(status = StatusCodes.BadRequest)
    }
}

trait FileContentResource {
  def handleFileRequest(req: HttpRequest, system: ActorSystem): Option[HttpResponse] = {
    if (isFileRequest(req)) Some(handleFile(req, system))
    else None
  }

  def isFileRequest(req: HttpRequest): Boolean = {
    if (req.method.toString().toUpperCase != "GET") false
    else {
      val path = req.uri.path.toString
      path.endsWith(".html") || path.endsWith(".css") || path.endsWith(".js")
    }
  }

  def handleFile(req: HttpRequest, system: ActorSystem): HttpResponse = {
    def fileReader(fileName: String): Either[String, BufferedReader] = {
      val file = new File(RestExtension(system).HtmlFileResources, fileName)
      val in =
        if (file.exists) new FileInputStream(file)
        else getClass.getResourceAsStream("/html/" + fileName)

      if (in == null) Left("Missing content: " + fileName)
      else Right(new BufferedReader(new InputStreamReader(in, "UTF-8")))
    }

    val path = req.uri.path.toString
    val baseUri = if (path.contains(GatewayActor.StaticUri)) GatewayActor.StaticUri + "/" else "/"
    val i = path.lastIndexOf(baseUri)
    val (_, fileName) = path.splitAt(i + baseUri.length)
    val readerResult = fileReader(fileName)
    readerResult match {
      case Left(errMsg) ⇒ HttpResponse(status = StatusCodes.NotFound)
      case Right(reader) ⇒
        try {
          val headers = HeadersBuilder.headers(useCacheControl = true, contentType = contentType(fileName))
          val builder = new StringBuilder
          var line = reader.readLine
          while (line != null) {
            builder.append(line)
            builder.append("\n")
            line = reader.readLine
          }

          HttpResponse(entity = HttpEntity(contentType = contentType(fileName), string = builder.toString()), headers = headers)
        } finally {
          reader.close()
        }
    }
  }

  def contentType(fileName: String): MediaType = {
    if (fileName.endsWith(".css")) MediaTypes.`text/css`
    else if (fileName.endsWith(".js")) MediaTypes.`application/javascript`
    else MediaTypes.`text/html`
  }
}
