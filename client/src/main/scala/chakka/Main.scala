package chakka

import akka.actor.{Props, ActorSystem}
import scala.scalajs.js
import org.scalajs.jquery.jQuery

/** this is the main JS entry point */
object Main extends js.JSApp {
  def main(): Unit = {
    //Load once dom is loaded, start the akka framework
    jQuery(() => {
      ActorSystem("ActorSystem").actorOf(Props(new ClientActor), name = "ClientActor")
    })
  }
}



