import sbt.Project.projectToRef

lazy val clients = Seq(client)

val commonSettings = Seq(
  scalaVersion := "2.11.7",
  organization := "chakka",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-encoding", "utf8"
  ),
  resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  resolvers += "sonatype-snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

lazy val server = (project in file("server")).settings(commonSettings: _*).settings(
  scalaJSProjects := clients,
  pipelineStages := Seq(scalaJSProd, gzip),
  libraryDependencies ++= Seq(
    "com.vmunier" %% "play-scalajs-scripts" % "0.3.0",
    "com.github.benhutchison" %% "prickle" % "1.1.10",
    specs2 % Test
  )
).enablePlugins(PlayScala).
  aggregate(clients.map(projectToRef): _*).
  dependsOn(sharedJvm)

lazy val client = (project in file("client")).settings(commonSettings: _*).settings(
  persistLauncher := true,
  persistLauncher in Test := false,
  //To enable loading of
  unmanagedBase := baseDirectory.value / "lib",
  unmanagedJars in Compile ++= (file(".") ** "*.jar").classpath,
  libraryDependencies ++= Seq(
    "org.scala-js" %%% "scalajs-dom" % "0.8.0",
    "be.doeraene" %%% "scalajs-jquery" % "0.8.1", //jQuery Bindings
    "com.github.benhutchison" %%% "prickle" % "1.1.10"
  )
)
  .enablePlugins(ScalaJSPlugin, ScalaJSPlay)
  .dependsOn(sharedJs)

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared")).settings(commonSettings: _*).settings(
    libraryDependencies ++= Seq(
    "com.github.benhutchison" %% "prickle" % "1.1.10",
    "com.github.benhutchison" %%% "prickle" % "1.1.10")
  )
  .jsConfigure(_ enablePlugins ScalaJSPlay)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

// loads the Play project at sbt startup
onLoad in Global := (Command.process("project server", _: State)) compose (onLoad in Global).value