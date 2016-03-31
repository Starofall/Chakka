package controllers

import actors.{ServerConnectionActor, ServerActor}
import javax.inject._
import com.google.inject.Inject
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import play.api.libs.iteratee._
import play.api.Logger
import akka.actor._
import scala.concurrent.Future
import scala.util.{Success, Failure}
import shared._
import prickle._

@Singleton
class Application @Inject()(system: ActorSystem) extends Controller {

  /** This is the main access point - here we serve the HTML+JSApp */
  def index = Action {
    Ok(views.html.index())
  }

  /** this is the serverActor, every massage will pass this actor */
  val serverActor = system.actorOf(Props(classOf[ServerActor]))

  /**
    * this is the main entry for the webSocket connection
    * the clients connects to this and the session is connected to the ServerConnectionActor
    * this node will than handle the connection to the user
    */
  def webSocketChatEntry = WebSocket.tryAccept[String] { request =>
    Logger.warn(s"New WebSocket connetion from: $request")

    /** this explains the pickler how to pickle and unpickle objects */
    implicit val messagePickler = ChatPickler.messagePickler

    //here we create a connection between the webSocket and the serverConnectionActor
    val (out, channel) = Concurrent.broadcast[String]
    val connectionHandler = system.actorOf(ServerConnectionActor.props(channel, serverActor))

    //for each incoming message try to unpickle it and handle it to the ServerConnectionActor
    val in = Iteratee.foreach[String] { msg =>
      Unpickle[NetworkMessage].fromString(msg) match {
        case Failure(exception) => Logger.error("UNKNOWN PACKET FORMAT")
        case Success(value) => connectionHandler ! value
      }
    }
    //return the webSocketHandle
    Future(Right(in, out))
  }
}



