# Actor system for Scala.js

This project aims at providing an actor system for Scala.js, featuring:

*   Supervision
*   (Almost) transparent collaboration with actors in an Akka-based backend
*   Transparent collaboration with actors in several Web Workers

It is currently a prototype, working well but still in a rough shape. Hence,
it is not published anywhere yet.

## Build it and try the examples

To build the libraries, use

    > package

in an sbt console.

Then download the examples and follow the README.md available [here](https://github.com/yawnt/scala-js-actors-examples)

## Design documentation

The best source of documentation for the design at large is
[this report](http://lampwww.epfl.ch/~doeraene/scalajs-actors-design.pdf).

## License

Scala.js actors is distributed under the
[Scala License](http://www.scala-lang.org/license.html).
