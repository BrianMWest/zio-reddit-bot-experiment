package me.bwest.switcharoo

import sttp.client._
import sttp.client.asynchttpclient.zio.SttpClient
import ujson.Value
import zio._

case class HttpException(error: String) extends Exception

object Http {

  def json(
      request: Request[Either[String, String], Nothing],
  ): ZIO[SttpClient, Throwable, Value] =
    ZIO.accessM[SttpClient] {
      _.get.send(request).flatMap {
        _.body.fold(
          error => ZIO.fail(HttpException(error)),
          json => ZIO.succeed(ujson.read(json)),
        )
      }
    }

}
