package me.bwest.switcharoo

import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio._
import zio.clock._
import zio.console._
import zio.duration._

object Main extends App {

  def run(args: List[String]): URIO[Clock with Console, ExitCode] =
    appLogic.provideLayer(env).exitCode

  def findCommentDepth(
      commentId: String,
  ): ZIO[Switcharoo with Console with Switcharoo, Throwable, RabbitHolePeriscope] =
    for {
      comment <- ZIO.accessM[Switcharoo with Console](_.get.fetchComment(commentId))
      depth <- comment.rooDepth(None)
    } yield depth

  val switcharooEnv: ZLayer[Any with Clock with Console, Throwable, Switcharoo] =
    (AsyncHttpClientZioBackend.layer() ++ ZLayer
      .identity[Clock with Console]) >>> Switcharoo.live

  val env: ZLayer[Clock with Console, Throwable, Clock with Console with Switcharoo] =
    Clock.live ++ Console.live ++ switcharooEnv

  val appLogic: ZIO[Switcharoo with Console, Throwable, Unit] = for {
    _ <- putStr("Comment Id: ")
    commentId <- getStrLn
    periscope <- findCommentDepth(commentId)
    depth = periscope.depth
    subreddits = periscope.comments.map(_.subreddit).mkString("\n  ")
    _ <- putStrLn(s"Switcharoo depth: $depth")
    _ <- putStrLn(s"Subreddits touched:\n  $subreddits")
  } yield ()

}
