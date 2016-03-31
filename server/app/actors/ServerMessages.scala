package actors

import akka.actor.ActorRef
import shared.ChatTypes.{Channel, User}
import shared.NetworkMessage

/** internal messages for the server*/
sealed abstract class ServerInternalMessage

/** used to get the user who created moderator privileges and start the keepAlive actor with a ServerActor reference */
case class ChannelCreationModRights(user: User, self: ActorRef) extends ServerInternalMessage

case class ShuttingDown(channel: Channel)                       extends ServerInternalMessage

/** To make it easier to add additional user information like username or isAdmin etc. we wrap the massage */
case class AuthedMessage(user: User, message: NetworkMessage)
