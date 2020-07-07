package me.bwest.switcharoo

import java.io.IOException
import sttp.client._
import sttp.client.asynchttpclient.zio.SttpClient
import zio._
import zio.clock._
import zio.console._
import zio.duration._

case class Token(access: String, expiresIn: Long, tokenType: String, scope: String)

object Switcharoo {
  sealed trait RooException extends Exception
  case class RooIoException(inner: IOException) extends RooException
  case object RooNoNextComment extends RooException
  case class RooErrorResponse(error: String) extends RooException

  lazy val redditClientId: String = sys.env("REDDIT_CLIENT_ID")
  lazy val redditClientSecret: String = sys.env("REDDIT_CLIENT_SECRET")
  lazy val redditUsername: String = sys.env("REDDIT_USERNAME")
  lazy val redditPassword: String = sys.env("REDDIT_PASSWORD")

  val fetchToken: ZIO[Clock with Console with SttpClient, RooException, Token] =
    Http
      .json(
        basicRequest
          .post(uri"https://api.reddit.com/api/v1/access_token")
          .auth
          .basic(redditClientId, redditClientSecret)
          .body(
            "grant_type" -> "password",
            "username" -> redditUsername,
            "password" -> redditPassword,
          ),
      )
      .refineOrDie[Exception] {
        case HttpException(e) => RooErrorResponse(e)
      }
      .map { r =>
        Token(
          r("access_token").str,
          r("expires_in").num.toLong,
          r("token_type").str,
          r("scope").str,
        )
      }
      .tapError(error => putStrLn(error.toString))
      .refineToOrDie[RooException]
      .retry(Schedule.exponential(1.second))

  trait Service {
    def fetchComment(commentId: String): ZIO[Console, Throwable, Comment]
  }

  class Live(val token: RefM[Token], backend: SttpClient) extends Service {

    override def fetchComment(id: String): ZIO[Console, Throwable, Comment] = {
      val commentId = if (id.startsWith("t1_")) id else s"t1_$id"
      for {
        response <-
          Http
            .json(
              basicRequest.get(uri"https://www.reddit.com/api/info.json?id=$commentId"),
            )
            .provide(backend)
        json = response("data")("children")(0)("data")
        comment = Comment(
          json("name").str,
          json("author_fullname").str,
          json("parent_id").str,
          json("body").str,
          json("body_html").str,
          json("subreddit").str,
        )
      } yield comment
    }

  }

  def live: ZLayer[SttpClient with Clock with Console, RooException, Switcharoo] = {
    val liveManaged = ZManaged.accessManaged[SttpClient with Clock with Console] {
      backend =>
        for {
          initToken <-
            fetchToken
              .tap(t => putStrLn(t.toString))
              .toManaged_
          token <- RefM.makeManaged(initToken)
          _ <-
            fetchToken
              .map(token.set)
              .repeat(Schedule.spaced(5.seconds))
              .forkManaged
        } yield new Live(token, backend)
    }
    ZLayer.fromManaged(liveManaged)
  }

}
