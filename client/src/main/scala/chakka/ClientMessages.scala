package chakka

import shared.ChatTypes.{Channel, User}

/** client only messages that are send from the UI */
sealed trait UIMessage
/** the ui required to create a privat chat room with an other user */
case class CreateRoomWith(user: User)                  extends UIMessage
/** the user typed a text into the chat input */
case class ChatInput(channel: Channel, msg: String)    extends UIMessage
/** the user gave rights in the UI to another user */
case class GiveModRights(channel: Channel, user: User) extends UIMessage