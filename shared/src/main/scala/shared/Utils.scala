package shared

import scala.annotation.tailrec


/**
  * general methods used for client and server
  */
object Utils {

  /** This is used for the keep-alive pings between Client <--> Server & Server <--> Serverchannels */
  def currentSeconds : Long = System.currentTimeMillis() / 1000

  /** Escaping tags to prevent injections */
  def escape(msg:String):String = {
    @tailrec
    def go(msg:List[Char], acc:String):String = msg match {
      case '&' :: xs => go(xs, acc + "&amp;")
      case '>' :: xs => go(xs, acc + "&gt;")
      case '<' :: xs => go(xs, acc + "&lt;")
      case   x :: xs => go(xs, acc + x)
      case       Nil => acc
    }
    go(msg.toList, "")
  }
}