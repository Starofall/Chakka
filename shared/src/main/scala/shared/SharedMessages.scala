package shared

import prickle._
import shared.ChatTypes._
import shared.Utils._

import scala.annotation.tailrec


object ChatPickler {

  implicit val messagePickler: PicklerPair[NetworkMessage] =
    //Abstract NetworkMessage
    CompositePickler[NetworkMessage]
      //ServerMessages
      .concreteType[SChatMessage]
      .concreteType[SUserJoined]
      .concreteType[SLoginFailed]
      .concreteType[SLoginSuccessFul]
      .concreteType[SBlockedUser]
      .concreteType[SLastChannelMessages]
      .concreteType[SNotAuthorized]
      .concreteType[SUserLeft]
      .concreteType[SYouLeft]
      .concreteType[SYouAreBanned]
      .concreteType[SUserBanned]
      .concreteType[SUserUnbanned]
      .concreteType[SHelpCommand]
      .concreteType[SShowChannel]
      .concreteType[SShowBanned]
      .concreteType[SKeepAlive]
      .concreteType[SInviteToRoom]
      .concreteType[SModRights]
      .concreteType[SSystemNotification]
      .concreteType[SClearChatArea]
      //ClientMessages
      .concreteType[CLeaveChannelRequest]
      .concreteType[CSendMessage]
      .concreteType[CLoginRequest]
      .concreteType[CJoinChannelRequest]
      .concreteType[CBanUserRequest]
      .concreteType[CUnbanUserRequest]
      .concreteType[CGetHelp]
      .concreteType[CGetBannedPplRequest]
      .concreteType[CKeepAlive]
      .concreteType[CUserDisconnected]
      .concreteType[CRequestModRights]
      .concreteType[CInviteUser]
      .concreteType[CClearChatAreaRequest]
}

// @formatter:off

object ChatTypes{

  //Enable the implicit conversion of MessageContents from and to strings
  import scala.language.implicitConversions

  /**
    * a strong typed element that is send inside a [[NetworkMessage]]
    * these elements can be auto-created from strings e.g. setChannel("Test") works
    * but when handling an incoming message CSendMessage(channel,msg) it is not possible to create a CSendMessage(msg,channel)
    * they can also be used as if they were strings to due there implicit 2string conversion
    */
  abstract class MessageContents

  /** the name of a channel */
  case class Channel(name:String) extends MessageContents {
    override def toString: String = name
  }
  object Channel{
    implicit def string2Channel(s:String):Channel = Channel(s)
    implicit def channel2string(m:Channel):String = m.name
  }

  /** the name of a user */
  case class User(user:String)  extends MessageContents {
    override def toString: String = user
  }
  object User{
    implicit def string2User(s:String):User = User(s)
    implicit def user2string(m:User):String = m.user
  }

  /** the content of a message send by the user, so it might contain html or illegal elements */
  case class DirtyMessage(msg:String)  extends MessageContents {
    override def toString: String = msg
  }
  object DirtyMessage{
    implicit def string2dirtyMessage(s:String):DirtyMessage = DirtyMessage(s)
    implicit def dirtyMessage2string(m:DirtyMessage):String = m.msg
    implicit def dirtyMessage2cleanMessage(m:DirtyMessage):CleanMessage = escape(m.msg)
  }

  /** the content of a message cleaned by the server so that we can send it to any client */
  case class CleanMessage(msg:String)  extends MessageContents {
    override def toString: String = msg
  }
  object CleanMessage{
    implicit def string2cleanMessage(s:String):CleanMessage = CleanMessage(s)
    implicit def cleanMessage2string(m:CleanMessage):String = m.msg
  }
}

/** An NetworkMessage is something the server and the client exchange thought Akka/WebSocket */
sealed trait NetworkMessage

/** a message that only travels in the direction from the server to the client */
trait ServerToClient extends NetworkMessage
/** a message that only travels in the direction from the client to the server */
trait ClientToServer extends NetworkMessage

/** additional trait used to determined messages that are only processed by the server if the user is authed */
trait AuthedRequiredMessage extends ClientToServer {
 val channelName:Channel
}
/** additional trait used to determined messages are from the server but are passed to a channel actor */
trait SChannelMessage extends ServerToClient {
 val channelName:Channel
}
/** additional trait used to determined messages are from the server but are passed to a SYSTEM actor */
trait SSystemMessage extends ServerToClient

case class SKeepAlive()                                                                           extends ServerToClient
case class SInviteToRoom(channelName: Channel)                                                    extends ServerToClient
case class SLoginSuccessFul(user: User)                                                           extends ServerToClient
case class SLastChannelMessages(channelName: Channel, lastMessages: List[NetworkMessage])         extends ServerToClient
case class SLoginFailed(reason:String)                                                            extends ServerToClient with SSystemMessage
case class SNotAuthorized(reason:String)                                                          extends ServerToClient with SSystemMessage
case class SBlockedUser(channelName: Channel)                                                     extends ServerToClient with SSystemMessage
case class SYouLeft(channelName: Channel)                                                         extends ServerToClient with SSystemMessage
case class SYouAreBanned(channelName: Channel, user: User, reason: String)                        extends ServerToClient with SSystemMessage
case class SModRights(hasMod: Boolean, channel: Channel, user: User)                              extends ServerToClient with SSystemMessage
case class SSystemNotification(msg: String)                                                       extends ServerToClient with SSystemMessage
case class SChatMessage(channelName: Channel, user: User, msg: CleanMessage)                      extends ServerToClient with SChannelMessage
case class SUserJoined(channelName: Channel,  user: User, currentUsers:Set[User])                 extends ServerToClient with SChannelMessage
case class SUserLeft(channelName: Channel, user: User, currentUsers:Set[User])                    extends ServerToClient with SChannelMessage
case class SUserBanned(channelName: Channel, user: User, reason: String, currentUsers:Set[User])  extends ServerToClient with SChannelMessage
case class SUserUnbanned(channelName: Channel, user: User)                                        extends ServerToClient with SChannelMessage
case class SHelpCommand(channelName: Channel, user: User, help: String)                           extends ServerToClient with SChannelMessage
case class SShowChannel(channelName: Channel)                                                     extends ServerToClient with SChannelMessage
case class SShowBanned(channelName: Channel, bannedUsers:Set[User])                               extends ServerToClient with SChannelMessage
case class SClearChatArea(channelName: Channel)                                                   extends ServerToClient with SChannelMessage

case class CLoginRequest(requestedUsername: String)                                               extends ClientToServer
case class CKeepAlive()                                                                           extends ClientToServer
case class CUserDisconnected()                                                                    extends ClientToServer
case class CInviteUser(channelName: Channel, user: User)                                          extends ClientToServer
case class CRequestModRights(channelName: Channel, user: User)                                    extends ClientToServer
case class CSendMessage(channelName: Channel,msg: DirtyMessage)                                   extends ClientToServer with AuthedRequiredMessage
case class CJoinChannelRequest(channelName:Channel)                                               extends ClientToServer with AuthedRequiredMessage
case class CLeaveChannelRequest(channelName: Channel)                                             extends ClientToServer with AuthedRequiredMessage
case class CBanUserRequest(channelName: Channel, user: User, reason: String)                      extends ClientToServer with AuthedRequiredMessage
case class CUnbanUserRequest(channelName: Channel, user: User)                                    extends ClientToServer with AuthedRequiredMessage
case class CGetHelp(channelName: Channel)                                                         extends ClientToServer with AuthedRequiredMessage
case class CGetBannedPplRequest(channelName: Channel)                                             extends ClientToServer with AuthedRequiredMessage
case class CClearChatAreaRequest(channelName: Channel)                                            extends ClientToServer with AuthedRequiredMessage
// @formatter:on