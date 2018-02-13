package DistributedCommon

import akka.actor.ActorRef

import scala.collection.mutable

case class RegisterSignal()

case class RequestSignal(val domain:String)

case class ping()

case class RegisterResult(val domain:String)