package actors

import akka.actor._
import play.api.Logger
import shared.ChatTypes.{Channel, User}
import shared.Utils.currentSeconds
import shared._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import scala.collection.mutable

/**
  * A ServerChannelActor is responsible for one channel
  * it knows who is connected to it and has a list of banned users and last messages
  * If a new user comes into the channel, it sends its lastMessages to this user
  * If a new message is passed to the channel, it distributes this message to all users
  */
class ServerChannelActor(val channel: Channel) extends Actor {

  /** the server checks this often if he is empty */
  final val CHECK_INTERVAL = 5.seconds

  /** the time the channel stays open even if no user is connected to it */
  final val CHANNEL_TIMEOUT_SECONDS = 30

  /** the length of messages that are stored on the server */
  final val MESSAGE_STORAGE_LENGTH = 100

  /** a vector of last received messages. Useful for new joining users to directly get all relevant news */
  var lastPublicMessages = mutable.ArrayBuffer[ServerToClient]()

  /** a map of all connected clients for this channel */
  var connectedClients = Map[User, ActorRef]()

  /** a list of blocked users */
  var bannedUsers = Set[User]()

  /** a list of moderators */
  var moderatorUsers = Set[User]()

  /** this will be send upon a GetHelp or wrong Command message*/
  val help = "Allowed commands: [[/leave | /quit] | /join &lt;channelname&gt; | /ban &lt;user&gt; &lt;reason&gt; | /unban &lt;user&gt; | /whoisbanned | /clear]"

  /** white star represents moderator rights */
  val modStar = "&#9734; "

  /** the last time the check was successful and there were users in this chatRoom */
  var lastUsedTime = currentSeconds

  /**
    * starts a scheduler that checks every CHECK_INTERVAL if somebody is connected to the channel
    * if a channel is not used for CHANNEL_TIMEOUT_SECONDS it shuts itself down
    *
    * lazy val because of "forward reference extends over definition"
    * */
  def keepClientAlive(serverActor : ActorRef) : Unit = {

    lazy val keepClientAliveActor: Cancellable = ActorSystem("keepClientAliveActor").scheduler.schedule(0.seconds, CHECK_INTERVAL) {
      if (connectedClients.nonEmpty){
        lastUsedTime = currentSeconds
      } else if (currentSeconds - lastUsedTime >= CHANNEL_TIMEOUT_SECONDS) {
        keepClientAliveActor.cancel()
        serverActor ! ShuttingDown(channel)
        Logger.warn(s"$channel is shutting down now!")
        lastPublicMessages.clear
        context.stop(self)
      }
    }
    keepClientAliveActor
    Logger.warn(s"Starting keepClientAlive for $channel...")
  }

  /** as we are in a protected environment, we only accept AuthedMessages or ServerInternalMessages here */
  def receive: Actor.Receive = {

    //ServerInternalMessages
    case ChannelCreationModRights(user, serverActorRef) =>
      moderatorUsers += user
      sender ! SSystemNotification(s"You have been given moderator rights in $channel by creation")
      keepClientAlive(serverActorRef)

    //we only allow authorized messages from client
    case AuthedMessage(user, packet) => packet match {

      //check if user is banned for this channel
      case CJoinChannelRequest(_) =>
        if (bannedUsers.contains(user)) {
          sender ! SBlockedUser(channel)
        } else {
          connectedClients.get(user) match {
              case Some(ref) => ref ! SNotAuthorized(s"You are already in channel '$channel'")
              case None      =>
                  connectedClients += user -> sender
                  sender ! SClearChatArea(channel)
                  sender ! SLastChannelMessages(channel, lastPublicMessages.toList)
                  sender ! SShowChannel(channel)
                  sendToAll(SUserJoined(channel, User(user.toString), connectedClients.keySet))
          }
        }

      //check if user has joined before he sends messages to channel
      case CSendMessage(_, msg) =>
        (connectedClients.get(user), moderatorUsers.contains(user)) match {
          case (Some(x),  true) => sendToAll(SChatMessage(channel, user, modStar + msg))
          case (Some(x), false) => sendToAll(SChatMessage(channel, user,           msg))
          case (None   ,     _) => sender ! SNotAuthorized(s"You are not part of channel '$channel'")
        }

      // check if the user is in the room is not needed, because -= is implemented as filter
      case CLeaveChannelRequest(_) =>
        connectedClients -= user
        sender !  SYouLeft(channel)
        sendToAll(SUserLeft(channel, user, connectedClients.keySet))


      /*
       * requesting the ban of the potential User
       * requesting user has to be moderator
       * potential user has not to be a moderator
       */
      case CBanUserRequest(_, potBan, reason) =>
        (moderatorUsers.contains(user), moderatorUsers.contains(potBan), connectedClients.get(potBan)) match {
          case (true, false, Some(ref)) =>
              bannedUsers += potBan
              ref ! SYouAreBanned(channel, potBan, reason)
              connectedClients -= potBan
              sendToAll(SUserBanned(channel, potBan, reason, connectedClients.keySet))
          case (    _,    _, None) => sender ! SNotAuthorized(s"You can't ban '$potBan' because he's not logged in")
          case (false,    _,    _) => sender ! SNotAuthorized(s"You can't ban people if you are not a moderator")
          case (_    , true,    _) => sender ! SNotAuthorized(s"You can't ban moderator '$potBan'")
        }

      /*
       * user to request the unban has to be moderator
       * user to be unbanned has to be banned
       */
      case CUnbanUserRequest(_, potUnban) =>
        (moderatorUsers.contains(user), bannedUsers.contains(potUnban)) match {
          case (true, false) => sender ! SNotAuthorized("You can't unban people which are not banned")
          case (false,    _) => sender ! SNotAuthorized("You can't unban people if you are not a moderator")
          case (    _,    _) =>
            bannedUsers -= potUnban
            sender ! SSystemNotification(s"You unbanned '$potUnban' from '$channel'")
            sendToAll(SUserUnbanned(channel, potUnban))
        }

      //this will only be shown to the user who mistyped a command
      case CGetHelp(_) => sender ! SHelpCommand(channel, user, help)

      //this will only be shown to the who accessed all banned people in the channel
      case CGetBannedPplRequest(_) => sender ! SShowBanned(channel, bannedUsers)

      case CClearChatAreaRequest(_) => sender ! SClearChatArea(channel)

      case CUserDisconnected() =>
        connectedClients -= user
        sendToAll(SUserLeft(channel, user, connectedClients.keySet))

      //checks privileges for the user who tried to give mod rights as for the potential moderator
      case CRequestModRights(_, potMod) =>
        (connectedClients.get(potMod), moderatorUsers.contains(user), moderatorUsers.contains(potMod)) match {
          // not a mod yet? give mod rights
          case (Some(potModRef),  true, false) =>
            moderatorUsers += potMod
            sendToAll(SModRights(hasMod = true, channel, potMod))
            potModRef ! SSystemNotification(s"You have been given moderator rights in '$channel'")

          // already a mod? erase mod rights
          case (Some(potModRef),  true,  true) =>
            moderatorUsers -= potMod
            sendToAll(SModRights(hasMod = false, channel, potMod))
            potModRef ! SSystemNotification(s"You have been stripped from your moderator rights in '$channel'")

          case (Some(potModRef), false,     _) => sender ! SNotAuthorized(s"You can't give moderator rights, because you're not a moderator")
          case (None           ,     _,     _) => sender ! SNotAuthorized(s"The User $potMod can't be given moderator rights as he is not in the channel $channel")
        }


      case _ => Logger.warn("Invalid Method in ChannelActor - unknown")
    }
    case _ => Logger.warn("Invalid Method in ChannelActor - not authed")
  }

  def sendToAll(message: ServerToClient) = {
    if(!message.isInstanceOf[SUserJoined]){
      lastPublicMessages :+= message
      if (lastPublicMessages.size > MESSAGE_STORAGE_LENGTH) {
        lastPublicMessages.remove(0)
      }
    }
    //Send message to all users
    for (userConnection <- connectedClients.values) {
      userConnection ! message
    }
  }
}

object ServerChannelActor {
  def props(channelName: String): Props = Props(new ServerChannelActor(channelName))
}