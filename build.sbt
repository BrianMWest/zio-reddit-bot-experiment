name := "switcharoo-tracker"

version := "0.1"

scalaVersion := "2.13.3"

libraryDependencies ++= Seq(
  "com.lihaoyi" %% "ujson" % "0.9.5",
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.2.0",
  "com.softwaremill.sttp.client" %% "core" % "2.2.1",
  "dev.zio" %% "zio" % "1.0.0-RC21-2",
  "org.scala-lang.modules" %% "scala-xml" % "1.2.0",
)
