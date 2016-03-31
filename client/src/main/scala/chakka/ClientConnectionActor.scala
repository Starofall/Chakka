package chakka

import akka.actor._
import org.scalajs.dom._
import org.scalajs.dom.raw.{Event, WebSocket}
import prickle.{Pickle, Unpickle}
import shared._
import shared.Utils.currentSeconds
import scala.util.{Failure, Success}
import akka.actor.Cancellable

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

/**
 * This Actor hides the implementation details of websocket
 * Messages send to this actor are forwarded to the server
 */
class ClientConnectionActor(clientActor:ActorRef, uIHeader: UIHeader) extends Actor {

  /** the url of our server */
  final var SERVER_URL = "ws://localhost:9000/chat"
  /** the interval for which we send the keepAlive */
  final val SEND_INTERVAL = 30.seconds
  /** the maximum number of seconds the client waits for a keepAlive from the server */
  final val CONNECTION_TIMEOUT_SECONDS  = 60
  /** this explains the pickler how to pickle and unpickle objects */
  implicit val messagePickler = ChatPickler.messagePickler
  /** the last time we got a keepAlive from the server */
  var lastResponseTime = currentSeconds
  /* webSocket used to Server-Client communication */
  var webSocket = connectToServer()

  /**
    * responsible for keeping clients "logged in"
    * Sends every SEND_INTERVAL keepAlive Messages
    * If the server doesn't respond in CONNECTION_TIMEOUT_SECONDS the clients tries to reconnect */
  val keepAlive : Cancellable = ActorSystem("keepAlive").scheduler.schedule(0.seconds, SEND_INTERVAL){
    if(webSocket.readyState ==2 ){
      //we are connected
      webSocket.send(Pickle.intoString(SKeepAlive() : NetworkMessage))
      if (currentSeconds - lastResponseTime >= CONNECTION_TIMEOUT_SECONDS) {
        println(s"My Server didn't send anything for ${currentSeconds - lastResponseTime}s...")
        //reconnecting to the webSocket
        clientActor ! SSystemNotification("Lost connection... trying to reconnect")
        connectToServer()
      }
    }
  }

  /** creating a new webSocket instance is used to connect to Server */
  def connectToServer(): WebSocket = {
    webSocket = new WebSocket(SERVER_URL)
    webSocket.onopen    = onConnect _
    webSocket.onmessage = onMessage _
    webSocket.onerror   = onError   _
    webSocket.onclose   = onClose   _
    webSocket
  }

  /** webSocket is trying to connect */
  def onConnect(e: Event) {
    updateConnectionStatus()
  }

  /** webSocket error in connection */
  def onError(e: Event): Unit = {
    clientActor ! SSystemNotification("Connection Error... trying to reconnect")
    updateConnectionStatus()
  }

  /** webSocket connection closed */
  def onClose(e: Event) {
    updateConnectionStatus()
    connectToServer()
  }

  /**
    * This handles the messages that are send through the WebSocket instance.
    * The message is unpickled into objects and send to the akka instance
    */
  def onMessage(msgEvent: MessageEvent) = {

    Unpickle[NetworkMessage].fromString(msgEvent.data.toString) match {
      case Failure(exception) => println("Was not able to unpickle server message: " + exception)
      case Success(value) => self ! value
    }
  }


  /** updates the connection status in the UI */
  def updateConnectionStatus(): Unit = {
    def getStatus: String = webSocket.readyState match {
      case 0 => "Connecting"
      case 1 => "Connected"
      case 2 => "Closing"
      case 3 => "Disconnected"
    }
    uIHeader.setStatus(getStatus)
  }

  /** the behaviour as an akka instance */
  def receive: Receive = {

    case n: ClientToServer =>
      println("Outgoing: " + n.toString)
      webSocket.send(Pickle.intoString(n: NetworkMessage))

    case m: ServerToClient =>
      println("Incoming: " + m.toString)
      m match {
        case SKeepAlive() => lastResponseTime = currentSeconds
        case _            => clientActor ! m
      }

    case _ => println("WARNING UNKNOWN MESSAGE")
  }
}

