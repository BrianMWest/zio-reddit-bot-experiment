package me.bwest.switcharoo

import scala.util.matching.Regex
import scala.xml.XML
import zio._
import zio.console._

case class RabbitHolePeriscope(depth: Int, comments: List[Comment])

case class Comment(
    id: String,
    authorId: String,
    parentId: String,
    body: String,
    bodyHtml: String,
    subreddit: String,
) {

  lazy val roo: Option[String] = {
    val html = bodyHtml.replaceAll("&lt;", "<").replaceAll("&gt;", ">")
    val xml = XML.loadString(s"<root>$html</root>")
    val anchors = xml \\ "a"
    anchors
      .map(_.attribute("href").toString)
      .flatMap { href =>
        Comment.rooRe.findAllMatchIn(href).map {
          case Comment.rooRe(commentId, _*) => s"t1_$commentId"
        }
      }
      .headOption
  }

  def rooDepth(
      maxDepth: Option[Int],
  ): ZIO[Switcharoo with Console, Throwable, RabbitHolePeriscope] = {
    val maxDepthReached = maxDepth match {
      case Some(d) => d <= 0
      case None => false
    }
    if (maxDepthReached) ZIO.fail(Comment.MaxDepthReached(this))
    else
      roo match {
        case Some(roo) =>
          for {
            comment <- ZIO.accessM[Switcharoo with Console](_.get.fetchComment(roo))
            periscope <- comment.rooDepth(maxDepth.map(_ - 1))
          } yield periscope.copy(
            depth = periscope.depth + 1,
            comments = this +: periscope.comments,
          )
        case None => ZIO.succeed(RabbitHolePeriscope(1, List(this)))
      }
  }

}

object Comment {
  case class MaxDepthReached(comment: Comment) extends Exception

  val rooRe: Regex = """reddit.com/r/\w+/comments/\w+/\w+/(\w+)(\?.*)?""".r

}
