package chakka

import akka.actor.{Actor, ActorRef, Props}
import shared.ChatTypes.{Channel, User}
import shared.Utils.escape
import shared._

/**
  * This is the main handler for anything that happens on the client
  * every inco ming message passes this gateway
  */
class ClientActor extends Actor {

  /** the HTML UI application */
  var ui = new UIApplication(self)

  /** the users login name */
  var owner : User = ""

  /** our clients creates a child actor that handles the webSocket communication */
  var networkIO = context.actorOf(Props(new ClientConnectionActor(self, ui.uiHeader)), name = "ClientConnectionActor")

  /** a map of all joined channels the client is part of */
  var joinedChannels = Map[Channel, ActorRef]()

  /** this will be send system channel start */
  val help = "Allowed commands: [/join &lt;channelname&gt; | /clear | /login &lt;unique name&gt;]"

  // add a systemChannel which is not a regular channel but a internal channel
  var systemChannel = createChannel("system")
  systemChannel ! SChatMessage("system", "System", "# This is the system channel, no messaging here")
  systemChannel ! SShowChannel("system")
  systemChannel ! SChatMessage("system", "System", s"# $help")
  systemChannel ! SChatMessage("system", "System", "# Please choose your username via /login <username>")
  joinedChannels += Channel("system") -> systemChannel

  /** if the actor starts or dies, this method is called to rebuild the UI in de DOM */
  override def preStart(): Unit = {
    ui.build()
  }

  /** creates a channel with actor and UI bindings */
  def createChannel(channel: Channel): ActorRef = {
    context.actorOf(
      Props(new ClientChannelActor(channel,
        ui.createUIChannelContainer(channel),
        ui.createUIChannelSelector(channel))),
        name = s"channel-$channel")
  }

  /**
    * in case we get a message for a channel we are NOT already part of
    * we can assume that we want to join this channel (e.g. private messaging)
    * and create a channel and forward the message to it.
    * else just forward the message
    */
  def forwardOrCreateChannel(channel: Channel, m: NetworkMessage): Unit = {
    joinedChannels.get(channel) match {
      case Some(x) =>
        x forward m
      case None =>
        val newChannel = createChannel(channel)
        joinedChannels += channel -> newChannel
        newChannel forward m
    }
  }

  // @formatter:off
  /**
    * cmd-parsing and forwarding to ClientConnectionActor
    **/
  def cmdParser(channel: Channel, msg: String) = {
    (joinedChannels.get(channel), channel.toString == "system", msg.split(" ").toList) match {
      case (      _,  true, "/login" :: name :: Nil) =>
        if (owner.toString == "") networkIO ! CLoginRequest(escape(name))
        else systemChannel ! SNotAuthorized("You can't choose your username again")

      case (Some(_), false, "/leave"         :: Nil) => networkIO ! CLeaveChannelRequest(channel)
      case (Some(_), false, "/quit"          :: Nil) => networkIO ! CLeaveChannelRequest(channel)
      case (Some(_), false, "/whoisbanned"   :: Nil) => networkIO ! CGetBannedPplRequest(channel)
      case (Some(_), false, "/unban" :: user :: Nil) => networkIO ! CUnbanUserRequest(channel, user)
      case (Some(_), false, "/ban"   :: user :: reason) if reason.nonEmpty => networkIO ! CBanUserRequest(channel, user, reason.mkString(" "))

      case (Some(_),     _, "/join" :: room  :: Nil) =>
        if (room != "system") networkIO     ! CJoinChannelRequest(room)
        else                  systemChannel ! SNotAuthorized("You are not allowed to create a channel named \"system\"")

      case (Some(_),  true, "/clear" :: Nil )=> systemChannel ! SClearChatArea("system")
      case (Some(_), false, "/clear" :: Nil )=> networkIO     ! CClearChatAreaRequest(channel)

      case (Some(_),  true, cmd :: _) if cmd.startsWith("/") => systemChannel ! SHelpCommand("", owner, help)
      case (Some(_), false, cmd :: _) if cmd.startsWith("/") => networkIO     ! CGetHelp(channel)

      case (_, true, _) => systemChannel ! SNotAuthorized("You are not allowed to do that in the system channel")
      case (_,    _, _) => networkIO     ! CSendMessage(channel, msg)
    }
  }
  // @formatter:on

  def receive = {
    //CUSTOM BEHAVIOUR
    case m@SLoginSuccessFul    (user)        => owner = user; systemChannel ! m
    case m@SInviteToRoom       (channelName) => networkIO     ! CJoinChannelRequest(channelName)

    //SYSTEM MESSAGES
    case m:SSystemMessage => systemChannel ! m

    //CHANNEL MESSAGES
    case m@SLastChannelMessages(channelName, lastMessages) =>
      //for each message the server would have send us before, we forward it to the channelActor
      for (msg <- lastMessages) {
          forwardOrCreateChannel(channelName, msg)
      }
    case m:SChannelMessage => forwardOrCreateChannel(m.channelName,m)

    //UI MESSAGES
    case ChatInput(channelName, msg)      => cmdParser(channelName, msg)
    case GiveModRights(channelName, user) => networkIO ! CRequestModRights(channelName, user)
    case CreateRoomWith(user)             =>
      if (user == owner) {
        systemChannel ! SNotAuthorized("You can't create a private room for yourself")
      } else {
        val channel = uniqueRoom(user, owner)
        networkIO ! CJoinChannelRequest(channel)
        networkIO ! CInviteUser(channel, user)
      }

    //OUTGOING MESSAGES
    case x: ClientToServer => networkIO ! x //ClientToServer are send over networkIO

    case _ => println("ClientActor got something undefined")
  }

  /** creates a channelName for two users that is (nearly) unique - ignoring the order */
  def uniqueRoom(user1: User, user2: User) : Channel = {
    (user1.toString :: user2.toString :: Nil).sorted.toString.hashCode.toString.slice(1,7)
  }
}