package actors

import akka.actor._
import play.api.Logger
import shared.ChatTypes.{Channel, User}
import shared._


/**
  * This is the main Actor on the server
  * every message should go through this single actor
  * Here we also keep the state (as refs to other actors)
  */
class ServerActor extends Actor {

  /** a map of all currently logged in users */
  var allUsers = Map[User, ActorRef]()
  /** map of all channels we currently have online */
  var allChannels = Map[Channel, ActorRef]()

  /**
    * a helper function used to assure that a message will only be handled if user is loggedIn
    * we use the implicit context of self to get the sender
    */
  def ifLoggedIn(runIfLoggedIn: (String => Unit))(implicit context: ActorContext) = {
    val mySender = context.sender()
    allUsers.map(_.swap).get(mySender) match {
      case Some(x) => runIfLoggedIn(x)
      case None    => mySender ! SNotAuthorized("You are not logged in")
    }
  }

  /**
    * in case we get a message for a channel we are NOT already part of
    * we can assume that we want to join this channel (e.g. private messaging)
    * and create a channel and forward the message to it.
    * else just forward the message
    *
    * if the channel is created the responsible user is given moderator rights
    */
  def forwardOrCreateChannel(channel: Channel, user: User, m: NetworkMessage): Unit = {
    allChannels.get(channel) match {
      case Some(channelActor) =>
        channelActor forward AuthedMessage(user, m)
      case None =>
        val channelActor = context.actorOf(ServerChannelActor.props(channel), s"channel-$channel")
        allChannels += channel -> channelActor
        channelActor forward AuthedMessage(user, m)
        channelActor forward ChannelCreationModRights(user, self)
    }
  }

  def receive: Receive = {

    /** ServerInternalMessages */
    case ShuttingDown(channel) =>
      allChannels = allChannels - channel

    /** Client Messages */
    case CLoginRequest(requestedUsername) =>
      allUsers.get(requestedUsername) match {
        case None =>
          //we escape the name from html related characters
          val saveName = Utils.escape(requestedUsername)
          allUsers += User(saveName) -> sender
          sender ! SLoginSuccessFul(saveName)
        case Some(x) =>
          sender ! SLoginFailed("Username taken")
      }

    case m@CInviteUser(channelName, partner) =>
      ifLoggedIn(user => {
        allUsers.get(partner) match {
          case Some(ref) => ref    ! SInviteToRoom(channelName)
          case None      => sender ! SNotAuthorized(s"# Can't create custom room with '$partner' for the following reason: User is not logged in!")
        }
      })

    case m@CUserDisconnected() =>
      ifLoggedIn(user => {
        for ((name, ref) <- allChannels){
          ref forward AuthedMessage(user, m)
        }
      })

    case m@CRequestModRights(channelName, potMod) =>
      ifLoggedIn(user => {
        if(user.toString == potMod.toString)
          sender ! SNotAuthorized("You can't give or remove moderator rights for yourself")
        else
          allUsers.get(potMod) match {
            case Some(_) => forwardOrCreateChannel(channelName, user, m)
            case None    => sender ! SNotAuthorized(s"The user $potMod can't be given moderator rights as he's not online")
          }
      })

    case m:AuthedRequiredMessage =>
      ifLoggedIn(user => {
        forwardOrCreateChannel(m.channelName, user, m)
      })

    case _ => Logger.warn("Unknown packet in MainServerActor")
  }
}
