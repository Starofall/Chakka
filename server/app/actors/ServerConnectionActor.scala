package actors

import akka.actor._
import akka.event.LoggingReceive
import play.api.Logger
import play.api.libs.iteratee.Concurrent.Channel
import prickle.Pickle
import shared._
import shared.Utils.currentSeconds
import akka.actor.Cancellable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


/**
 *  For each open webSocket one of this actors is created.
 *  Every message that comes from the webSocket passes this actor.
 *  Messages to the actor are either send to the webSocket or to the MainServerActor.
 */
class ServerConnectionActor(backChannel: Channel[String], mainServerActor: ActorRef) extends Actor with ActorLogging {

  /** the interval for which we send the keepAlive */
  final val SEND_INTERVAL = 30.seconds

  /** the maximum number of seconds the client waits for a keepAlive from the client */
  final val CONNECTION_TIMEOUT_SECONDS = 60

  /** the last time we got a keepAlive from the client */
  var lastResponseTime = currentSeconds

  /** this explains the pickler how to pickle and unpickle objects */
  implicit val messagePickler = ChatPickler.messagePickler

  /**
    * Pings the ClientConnectionActor every 'sendTime' seconds
    * If he doesn't ping back in 'maxTime' seconds, we lost the connection
    * Lets all the other rooms know and kills himself
    * */
  val keepAlive : Cancellable = ActorSystem(s"keepAlive").scheduler.schedule(0.seconds, SEND_INTERVAL){
    backChannel push Pickle.intoString(CKeepAlive() : NetworkMessage)
    if (currentSeconds - lastResponseTime >= CONNECTION_TIMEOUT_SECONDS) {
      keepAlive.cancel()
      mainServerActor ! CUserDisconnected()
      //this prevents that in the last run there could be an nullPointer
      if(context != null && self != null){
        Logger.error(s"My Client ${context.self.toString()} didn't send anything for ${currentSeconds - lastResponseTime}s...")
        context.stop(self)
      }
    }
  }

  def receive = LoggingReceive {
    //Messages to the client go out through the backChannel
    case n: ServerToClient =>
      Logger.error("ServerConnectionActor-toClient-" + n)
      backChannel push Pickle.intoString(n: NetworkMessage)

    //Messages from the client go the the mainServerActor
    case m: ClientToServer =>
      Logger.error("ServerConnectionActor-fromClient-" + m)
      m match {
        case CKeepAlive() => lastResponseTime = currentSeconds
        case _            => mainServerActor ! m
      }

    case x => Logger.error("Unknown packet in ConnectionHandlerActor - " + x)
  }

}

object ServerConnectionActor {
  def props(backChannel: Channel[String], mainServerActor: ActorRef): Props = Props(new ServerConnectionActor(backChannel, mainServerActor))
}

