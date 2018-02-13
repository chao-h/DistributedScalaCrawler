name := "DIstributedServer"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies += "com.typesafe.akka" % "akka-actor_2.11" % "2.4.3"

libraryDependencies += "com.typesafe.akka" % "akka-remote_2.11" % "2.4.1"

libraryDependencies += "org.scalanlp" % "breeze_2.10" % "0.12"

libraryDependencies += "org.apache.lucene" % "lucene-analyzers-common" % "5.5.0"

libraryDependencies += "redis.clients" % "jedis" % "2.8.1"

libraryDependencies += "org.mongodb.scala" %% "mongo-scala-driver" % "1.1.0"

libraryDependencies += "org.mongodb" %% "casbah" % "3.1.1"

libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.6.3",
  "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % "2.6.3",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.7.0"
)