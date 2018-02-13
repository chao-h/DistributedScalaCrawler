name := "DistributedClient"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.4.3"

libraryDependencies += "com.typesafe.akka" % "akka-remote_2.11" % "2.4.3"

libraryDependencies += "com.syncthemall" % "goose" % "2.1.25"

libraryDependencies += "org.jsoup" % "jsoup" % "1.8.3"

libraryDependencies += "org.scala-lang" % "scala-xml" % "2.11.0-M4"

libraryDependencies += "org.apache.lucene" % "lucene-analyzers-common" % "5.5.0"

libraryDependencies += "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.16"

libraryDependencies += "org.scalanlp" % "breeze_2.10" % "0.12"

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.3",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.6.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.7.0"
)