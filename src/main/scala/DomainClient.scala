import DistributedCommon.RegisterResult
import akka.actor.Actor

class DomainClient(rr: RegisterResult, val token_score: Map[String, Float]) extends Actor{
  def receive = {
    case _ =>
  }
}

/*
import DistributedCommon._
import akka.actor.Status.{Failure, Success}
import akka.actor._
import akka.pattern.{ask, pipe, AskTimeoutException}
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Await, Future}
import scala.concurrent.duration._

class DomainClient(val domain:String) extends Actor{
  val server = context.actorSelection("akka.tcp://ServerSystem@127.0.0.1:5150/user/Server")
  var serverRef:ActorRef = _
  var domain: String = _
  var urlDownloaders: List[ActorRef] = _
  import context.dispatcher

  def receive = {
    case _:Terminated => {
      if(sender == serverRef) {
        println("Received TERMINATED from ServerRef, killing itself"+self.path)
        self ! Kill
      }
    }
    case Available =>
      val child = sender()
      implicit val timeout = Timeout(10 seconds)
      val future = ask(server, ("available", domain)) recover {
        case e: AskTimeoutException => new LinkInfo("")
      }
      future pipeTo child

    case ("enqueue", li:LinkInfo) => server ! ("enqueue", domain, li)
    case _ => println(sender.toString+" sended an unknown msg to "+self.toString)
  }

  override def preStart(): Unit ={
    println("Start prestart")
    implicit val timeout = Timeout(30 seconds)
    //result = Await.result(future, timeout.duration).asInstanceOf[RegisterResult]

    val serverFuture = server.resolveOne()
    serverFuture.onFailure{
      case e:AskTimeoutException => println("ServerFuture timeout, killing it self"); self ! Kill
      case _ => println("ServerFuture failure, killing it self"); self ! Kill
    }
    serverFuture.onSuccess{
      case ref:ActorRef => {
        serverRef = ref
        println("ServerFuture Success")
        context.watch(serverRef)

        val future = serverRef ? "register"
        future onFailure{
          case e:AskTimeoutException =>
            println("Register Request timeout")
            self ! Kill
          case _ =>
            println("Register future failure, killing itself")
            self ! Kill
        }
        future onSuccess{
          case rr : RegisterResult =>
            domain = rr.domain
            println(f"Initialization success with Domain: $domain")
            var child_cnt = -1
            urlDownloaders = List.fill(5){
              child_cnt += 1
              context.actorOf(Props(new UrlDownloader(domain)), name=f"downloader_$child_cnt")
            }
          case unknown =>
            println(" Received: "+unknown.getClass+"Initialization fail")
            self ! Kill
        }
      }
      case _ => println("Unknown result of ServerRef, killing itself"); self ! Kill
    }
  }

  def LinkInfoOrdering = new Ordering[LinkInfo]{
    def compare(a:LinkInfo,b:LinkInfo)=
      a.priority compare b.priority
  }
}

case class Available()
*/
