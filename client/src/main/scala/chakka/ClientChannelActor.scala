package chakka

import akka.actor.Actor
import shared.ChatTypes.Channel
import shared._

/**
  *  A ClientChannelActor handles an joined channel on the client side.
  *  It knows its channelName and has two bindings to UI elements
  *  one for the button to notify on change (UIChannelSelector)
  *  and one to the the channel pane (UIChannelContainer)
  *  It will apply incoming messages to the DOM
  */
class ClientChannelActor(channel:   Channel,
                         container: UIChannelContainer,
                         selector:  UIChannelSelector) extends Actor {


  def receive = {

    // SYSTEM Messages
    case m@SLoginSuccessFul(user)   =>
      container.chatArea.appendMessage("System", s"# Login successful as ${user.toString}!")
      selector.setActive()

    case m@SLoginFailed    (reason) =>
      container.chatArea.appendMessage("System", s"# Login unsuccessful because of the following reason: $reason")
      container.chatArea.appendMessage("System", s"# Please choose a username via /login <username>")
      selector.setActive()

    case m@SNotAuthorized  (reason) =>
      container.chatArea.appendMessage("System", s"# $reason")
      selector.setChanged()

    case m@SBlockedUser    (channelName) =>
      container.chatArea.appendMessage("System", s"# You are banned in channel '$channelName'")
      selector.setChanged()

    case SYouAreBanned(fromChannel, user, reason) =>
      selector.hideTabs()
      container.removeChat(fromChannel)
      container.chatArea.appendMessage("System", s"# You were banned from channel $fromChannel for the following reason: $reason")
      selector.showSystemChannel()
      selector.setActive()

    case SYouLeft(fromChannel) =>
      selector.hideTabs()
      container.removeChat(fromChannel)
      container.chatArea.appendMessage("System", s"# You left channel $fromChannel")
      selector.showSystemChannel()
      selector.setActive()

    case SSystemNotification(msg) =>
      container.chatArea.appendMessage("System", s"# $msg")
      selector.setChanged()

    case SShowChannel(fromChannel) =>
      selector.showChannel(fromChannel)
      selector.setActive()

    // CHANNEL Messages
    case SChatMessage(_, user, msg) =>
      container.chatArea.appendMessage(user,msg)
      selector.setChanged()

    case SUserJoined(_, user, currentUsers) =>
      container.chatArea.appendMessage("System", s"# $user joined")
      container.userList.setUserList(currentUsers)
      selector.setChanged()

    case SUserLeft(_, user, currentUsers) =>
      container.chatArea.appendMessage("System", s"# $user left")
      container.userList.setUserList(currentUsers)
      selector.setChanged()

    case SUserBanned(_, user, reason, currentUsers) =>
      container.chatArea.appendMessage("System", s"# $user was banned for following reason: $reason")
      container.userList.setUserList(currentUsers)
      selector.setChanged()

    case SUserUnbanned(_, user) =>
      container.chatArea.appendMessage("System", s"# $user has been unbanned")
      selector.setChanged()

    case SModRights(hasMod, _, user) =>
      if (hasMod)
        container.chatArea.appendMessage("System", s"# User $user has been given moderator privileges!")
      else
        container.chatArea.appendMessage("System", s"# User $user has been stripped from his moderator privileges!")
      selector.setChanged()

    case SClearChatArea(_) =>
      container.chatArea.clear(channel)
      selector.setChanged()

    // WHISPER Messages (single user)
    case SHelpCommand(_, user, help) =>
      container.chatArea.appendMessage("System", s"# $help")

    case SShowBanned(_, bannedUsers) =>
      if (bannedUsers.isEmpty)
        container.chatArea.appendMessage("System", "# Nobody is banned...yet")
      else
        container.chatArea.appendMessage("System", s"# Banned users: ${bannedUsers.mkString(";")}")

    case m@_ => println("unknown in ClientChannelActor -"+m)
  }
}
