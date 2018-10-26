name := "rabbitmqscaner"

version := "0.1"

scalaVersion := "2.12.7"

//addSbtPlugin("org.scala-sbt.plugins" % "sbt-onejar" % "0.8")
enablePlugins(JavaAppPackaging)

libraryDependencies ++= Seq(
  // For the console exercise, the logback dependency
  // is only important if you want to see all the
  // debugging output. If you don't want that, simply
  // omit it.
  "ch.qos.logback"          %  "logback-classic" % "1.2.3",
  "org.dispatchhttp"        %% "dispatch-core"   % "1.0.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.7",
  "joda-time" % "joda-time" % "2.9.9",
  "com.typesafe.akka" %% "akka-actor" % "2.5.17"
)

