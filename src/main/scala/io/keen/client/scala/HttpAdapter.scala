package io.keen.client.scala

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import grizzled.slf4j.Logging
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import spray.can.Http
import spray.http._
import spray.http.Uri._
import spray.http.HttpHeaders.RawHeader
import spray.httpx.RequestBuilding._

class HttpAdapter() extends Logging {

  implicit val system = ActorSystem()
  import system.dispatcher // execution context for futures
  implicit val timeout = Timeout(10, TimeUnit.SECONDS)

  /**
   * Perform the request with some debugging for good measure.
   *
   * @param req The request
   */
  def doRequest(
    scheme: String,
    authority: String,
    path: String,
    method: String,
    key: String,
    body: Option[String] = None,
    params: Map[String,Option[String]] = Map.empty): Future[Response] = {

    // Turn a map of string,opt[string] into a map of string,string which is
    // what Query wants
    val filteredParams = params.filter(
      // Filter out keys that are None
      _._2.isDefined
    ).map(
      // Convert the remaining tuples to str,str
      param => (param._1 -> param._2.get)
    )
    // Make a Uri
    val finalUrl = Uri(
      scheme = scheme,
      authority = Authority(host = Host(authority)),
      path = Path("/" + path),
      query = Query(filteredParams)
    )

    // Use the provided
    val httpMethod: HttpRequest = method match {
      case "DELETE" => HttpRequest(method = HttpMethods.DELETE, uri = finalUrl, entity = body)
      case "GET" => Get(finalUrl, body)
      case "POST" => Post(finalUrl, body)
      case _ => throw new IllegalArgumentException("Unknown HTTP method: " + method)
    }

    debug("%s: %s".format(method, finalUrl))
    (IO(Http) ? httpMethod.withHeaders(
      RawHeader("Content-type", "application/json"),
      RawHeader("Authorization", key)
    ))
      .mapTo[HttpResponse].map({ res =>
        Response(statusCode = res.status.intValue, res.entity.asString)
      })
  }

  def shutdown {
    Http.CloseAll
  }
}
