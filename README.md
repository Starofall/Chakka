# Chakka - the Akka Chat
Chat with Scala+Akka and Scala.JS+Akka.JS

This prototype is devided into three parts:

* Server - running Play with Scala and using Akka instances for user and channel handling
* Client - using Scala.JS and Akka.JS the client is compiled from Scala to JavaScript
* Shared - defines message objects that can be send typed over websocket and get handles by the akka instance on the other side

## Running it

Clone the repository and start it with 
>sbt run

and connect to localhost:9000
