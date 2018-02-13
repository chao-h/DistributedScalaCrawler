import akka.actor.{Terminated, Kill, ActorRef, Actor}
import akka.util.Timeout
import akka.pattern.{AskTimeoutException, ask}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Created by moo on 4/12/16.
  */
class Client extends Actor{
  var server: ActorRef = _
  import context.dispatcher

  override def preStart(): Unit ={
    val serverSelection = context.actorSelection("akka.tcp://ServerSystem@127.0.0.1:5150/user/serverSupervisor/server")
    implicit val timeout = Timeout(30 seconds)
    val serverFuture = serverSelection.resolveOne()
    server = Await.result(serverFuture, timeout.duration)
    context.watch(server)
    register()
  }

  def receive ={
    case _ : Terminated =>
      if (sender == server){
        println("Received TERMINATED from server in DomainClient, killing it self"); self ! Kill
      }
      else{
        println("Received TERMINATED from Unknown in DomainClient")
      }
    case unknown => println("Received unknown msg in Client "+unknown.getClass+" from "+sender)
  }

  def register(): Unit ={
    println("Start Registration")
    implicit val timeout = Timeout(30 seconds)
    val domainClientFuture = server ? "register"
    domainClientFuture onFailure{
      case e:AskTimeoutException => println("Register failure, server ask time out, killing it self"); self ! Kill
      case _ => println("Register failure, unknown failure, killing it self"); self ! Kill
    }
    domainClientFuture onSuccess{
      case ref:ActorRef =>
        println("Register success, ref: "+ref)
      case unknown =>
        println("Register onSuccess unknown msg: "+unknown.getClass)
        println("Killing itself")
        self ! Kill
    }
  }
}
