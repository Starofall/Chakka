package chakka

import akka.actor.ActorRef
import org.scalajs
import org.scalajs.jquery._
import shared.ChatTypes.{Channel, User}

import scala.scalajs.js.Any
import scala.util.hashing.MurmurHash3

/** Creates user colors */
object UserColor {
  //color is auto created based on the hash of the userName
  //using MurmarHash3 to get well distributed hashes and then
  //use +100 to get no black colors and % 155 to limit it < 256
  def getUserColor(user: User): (Int, Int, Int) = {
    val r = Math.abs(MurmurHash3.stringHash(user + "1")) % 155 + 100
    val g = Math.abs(MurmurHash3.stringHash(user + "2")) % 155 + 100
    val b = Math.abs(MurmurHash3.stringHash(user + "3")) % 155 + 100
    (r, g, b)
  }
}

/** a boundElement is a wrapper for a jQuery binding that can be accessed with .binding */
abstract class BoundElement(html: String) {
  val binding = jQuery(html)
}

/** The UIApplication is the DOM correspondence to the whole application */
class UIApplication(val clientActor: ActorRef) extends BoundElement("<chat-application></chat-application>") {

  //a list of containerAreas for individual channels
  val channels = jQuery("<chat-channelcontainerlist></chat-channelcontainerlist>")

  //Creating UI Elements
  val channelList = new UIChannelList(this)
  val uiHeader = new UIHeader(this)

  binding
    .append(uiHeader.binding)
    .append(channelList.binding)
    .append(channels)

  def createUIChannelContainer(s: Channel): UIChannelContainer = {
    val channelContainer = new UIChannelContainer(this, s)
    channels.append(channelContainer.binding)
    channelContainer
  }

  def createUIChannelSelector(s: Channel): UIChannelSelector = {
    val uiChannelSelector = new UIChannelSelector(this, s)
    channelList.binding.append(uiChannelSelector.binding)
    uiChannelSelector
  }

  def build() = {
    //Clean all created before
    jQuery("body").children().remove()
    //Apply UI to DOM
    jQuery("body").append(binding)

    // Enable type-everywhere by focusing on the input area if the user types
    // TODO can't get this to work?...
    // keydown tested, working
    // code is working on mouseclicks, but not on keydowns
    binding.keydown { () => {
      val activeChan = jQuery(".active").text()
      jQuery(s"#input-$activeChan").focus()
    }}
  }
}

/** contains the message area the userArea and the inputArea */
class UIChannelContainer(app: UIApplication, channelName: Channel) extends BoundElement("<chat-channel></chat-channel>") {
  //we use html-id to make it easy to identify the container for tabbing
  binding.prop("id", s"channel-$channelName")

  val horizontal = jQuery("<chat-container style='display:flex'></chat-container")
  val userList   = new UIUserList(app, channelName)
  val chatArea   = new UIChatArea(app)
  val inputArea  = new UIInputArea(app, channelName: Channel)

  horizontal.append(chatArea.binding)

  //if it is not system we have a userList
  if (channelName.toString != "system") {
    binding.hide()
    horizontal.append(userList.binding)
  }

  binding
    .append(horizontal)
    .append(inputArea.binding)


  def removeChat(channel : Channel)= {
    binding.parent().children(s"#channel-$channel").hide()
  }
}

/** the header of the application */
class UIHeader(app: UIApplication) extends BoundElement("<chat-header>Chakka Chat</chat-header>") {
  def setStatus(status: String): Unit = {
    binding.text("Chakka Chat - Status:" + status)
  }
}

/** the list of users displayed in the channelList */
class UIUserList(app: UIApplication, channelName: Channel) extends BoundElement("<chat-userlist oncontextmenu=\"return false;\"></chat-userlist>") {

  /** uses a list of users to update the DOM to show the users in a channel */
  def setUserList(currentUsers: Set[User]): Unit = {
    //first clean the element
    binding.children().remove()
    //append userCounter
    binding.append(s"<p style='text-align: center;'> ${currentUsers.size} active user</p>")
    //append each user
    for (user <- currentUsers) {
      val (r, g, b) = UserColor.getUserColor(user)
      val userElement = jQuery(s"<chat-channeluser style='color:rgb($r,$g,$b)'>$user</chat-channeluser>")


      /** click() doesn't work, as well as bind("oncontextmenu") this was the only working solution (in FF)
        * removed contextmenu popup through tag 'oncontextmenu="return false;"'
        * */
      userElement.mouseup((e : JQueryEventObject) => {
        e.which match {
          case 1 => app.clientActor ! CreateRoomWith(user)                      // left   mousebutton
          case 2 => ()                                                          // middle mousebutton
          case 3 => app.clientActor ! GiveModRights(channelName, user.toString) // right  mousebutton
        }
        jQuery("chat-inputarea input").focus()
      })

      binding.append(userElement)
    }
  }
}

/** the list of channels under the header */
class UIChannelList(app: UIApplication) extends BoundElement("<chat-channellist></chat-channellist>")

/** a element of the UIChannelList used to select the channel that should be displayed */
class UIChannelSelector(app: UIApplication, channel: Channel) extends BoundElement("<chat-channelselector></chat-channelselector>") {
  //Name it correct
  if (channel.toString.toList.forall( c => c.isDigit ) && channel.length == 6)
    binding.text(s"private - #$channel")
  else
    binding.text(s"$channel")

  //onclick it selects itself and un-selects all others
  binding.click(() => {
    //disable active on all other channelselectors
    jQuery("chat-channelselector").each(
      (_: Any, elem: scalajs.dom.Element) => jQuery(elem).removeClass("active")
    )
    //set this one active and remove notify
    binding.removeClass("notify")
    binding.removeClass("blink_me")
    binding.addClass("active")

    //hide other channels
    jQuery("chat-channel").each(
      (_: Any, elem: scalajs.dom.Element) => jQuery(elem).hide()
    )
    //show requested channel
    jQuery(s"#channel-$channel").show()

    //make correct input highlighted
    jQuery(s"#input-$channel").focus()
  })

  def showSystemChannel() = {
    jQuery("chat-channel").each(
      (_: Any, elem: scalajs.dom.Element) => jQuery(elem).hide()
    )
    jQuery(s"#channel-system").css("display", "inline")
  }

  /** select a channel to be displayed */
  def showChannel(channel: Channel) = {
    jQuery("chat-channel").each(
      (_: Any, elem: scalajs.dom.Element) => jQuery(elem).hide()
    )
    jQuery(s"#channel-$channel").css("display", "inline")
    jQuery(s"#channel-$channel").show()
    jQuery(s"#input-$channel").focus()

    jQuery(s"chat-channelselector:contains('$channel')").show()
  }

  /** sets this selector to an changed state where it notifies the user */
  def setChanged() = {
    if (!binding.hasClass("active")) {
      binding.addClass("notify")
      binding.addClass("blink_me")
    }
  }

  /** sets every other tab as nonactive and this selector as active */
  def setActive() = {
    jQuery("chat-channellist").children().removeClass("active")
    binding.removeClass("notify")
    binding.removeClass("blink_me")
    binding.addClass("active")
  }

  def hideTabs() = {
    binding.parent().children(s".active").hide()
  }


}

/** the chatArea is a the area where [[UIChatMessage]]'s are shown */
class UIChatArea(app: UIApplication) extends BoundElement("<chat-area></chat-area>") {

  /** add a [[UIChatMessage]] to the list of messages */
  def appendMessage(user: String, msg: String): Unit = {

    //automatically add links to urls in a message
    val regexUrl = "(http://|https://|www.)[A-Za-z0-9-_]+.[A-Za-z0-9-_:%&?/.=]+".r
    val linkedMsg = regexUrl.replaceAllIn(msg, "<a href='//$0'>$0</a>")

    val date = new scala.scalajs.js.Date()
    val now = s"${date.getHours().formatted("%02d")}:${date.getMinutes().formatted("%02d")}:${date.getSeconds().formatted("%02d")}"
    //append message to the chatArea
    val message = new UIChatMessage(UserColor.getUserColor(user), user, s"$now " + linkedMsg)
    binding.append(message.binding)
  }

  binding.focus()

  /** removes all chat-messages in the chat-area */
  def clear(channel: Channel) : Unit = {
    jQuery(s"#channel-$channel chat-area chat-message").each(
      (_: Any, elem: scalajs.dom.Element) => jQuery(elem).remove()
    )
  }

  // doesn't work yet
  /*
  binding.mouseup((e : JQueryEventObject) => {
    jQuery("link[rel=\"stylesheet\"]").attr("href").toString match {
      case "/assets/stylesheets/main-2.css" => jQuery("link[rel=\"stylesheet\"]").attr("href", "/assets/stylesheets/main-2.css")
      case _                                => jQuery("link[rel=\"stylesheet\"]").attr("href", "/assets/stylesheets/main.css")
    }}) */
}

/** a single message in the [[UIChatArea]] */
class UIChatMessage(rgbTuple: (Int, Int, Int), user: String, msg: String) extends BoundElement("<chat-message></chat-message>") {
  var (r, g, b) = rgbTuple
  binding.append(s"<chat-user style='color:rgb($r,$g,$b)'>$user</chat-user>")
  binding.append(s"<chat-text>$msg</chat-text>")
}

/** the inputArea consists of a text input area and a send button */
class UIInputArea(app: UIApplication, channelName: Channel) extends BoundElement("<chat-inputarea></chat-inputarea>") {
  val textInput = jQuery("<input class='chatTextInput' autofocus>")
  textInput.prop("id", s"input-$channelName")

  val sendButton = jQuery("<button class='sendTextButton'>Send</button>")
  binding.append(textInput).append(sendButton)

  //Add send button click handler
  sendButton.click(sendMessage _)

  //Add enter click handler
  textInput.keypress((e: JQueryEventObject, x: Any) => {
    if (e.which == 13) {
      // 13 = Enter pressed
      sendMessage(e)
    }
  })

  /** the user requested to send a message to the server */
  def sendMessage(e: JQueryEventObject) = {
    //do not send nothing
    if (textInput.value().toString != "") {
      app.clientActor ! ChatInput(channelName, textInput.value().toString)
      //reset field
      textInput.value("")
    }
  }
}
