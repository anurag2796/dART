name := "dART"

version := "0.1.0"

scalaVersion := "2.13.12"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.8.5" % Test,
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "ch.qos.logback" % "logback-classic" % "1.4.11"
)

// Compiler options for better error messages
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint"
)
