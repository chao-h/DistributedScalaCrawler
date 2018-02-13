import java.io._
import java.net.URI

import DistributedCommon._
import PriorityCalculator.{WordPreprocessor, Word2Vec}

import akka.actor._
import akka.pattern.ask
import akka.remote.RemoteScope
import akka.util.Timeout
import breeze.util.BloomFilter

import scala.collection.mutable
import scala.concurrent.Await
import scala.io.Source
import scala.concurrent.duration._
import scala.io.StdIn._
import scala.util.matching.Regex

/**
  * Created by moo on 4/6/16.
  */
class Server(val recover:Boolean, val token_score: Map[String, Float]) extends Actor{
  var domains: List[String] = _
  var redisClient:ActorRef = _
  var mongodbClient: ActorRef = _
  import context.dispatcher
  val domain_client_map = mutable.Map[String, List[String]]()
  var bf = new BloomFilter[String](100000000, 5)

  override def preStart {
    println(self.toString+" created")
    val redisClientSelection = context.actorSelection("akka://ServerSystem/user/redisClientSupervisor/redisClient")
    val mongodbClientSelection=context.actorSelection("akka://ServerSystem/user/mongodbClientSupervisor/mongodbClient")
    implicit val timeout = Timeout(10 seconds)
    val redisClientFuture = redisClientSelection.resolveOne()
    val mongodbClientFuture=mongodbClientSelection.resolveOne()

    redisClient = Await.result(redisClientFuture, timeout.duration)
    mongodbClient=Await.result(mongodbClientFuture, timeout.duration)
    context.watch(redisClient)
    context.watch(mongodbClient)

    val redisReadyFuture = redisClient ? "ready"
    val mongoReadyFuture = mongodbClient ? "ready"

    Await.result(redisReadyFuture, timeout.duration)
    Await.result(mongoReadyFuture, timeout.duration)

    if(recover) recover_preStart()
    else normal_preStart()
  }


  def receive = {
    case "backup_bf" => backup_bf()
    case "register" => register()
    case ("available", domain:String) => available(domain)
    case ("enqueue", domain:String, li:LinkInfo) => enqueue(domain, li)
    case ("write", li:LinkInfo) => mongodbClient ! ("write", li)
    case _:Terminated => {
      if(sender.path == redisClient.path){
        println("RedisClient Terminated, server suiciding")
        self ! Kill
      }
      else if(sender.path == mongodbClient.path){
        println("MongodbClient Terminated, server suiciding")
        self ! Kill
      }
      else {
        println(f"Terminated Signal from $sender")
        for ((domain: String, list: List[String]) <- domain_client_map) {
          domain_client_map.put(domain, domain_client_map.get(domain).getOrElse(List()).filter(_ != sender.toString))
        }
      }
    }
    case _ => println("Received unkown msg from "+sender.toString)
  }

  def enqueue(domain:String, li:LinkInfo): Unit ={
    if(!bf.contains(li.url))
      redisClient ! ("add_queue", domain, li.url, li.priority.asInstanceOf[Float])
  }

  def available(domain:String): Unit ={
    println("Received Available Signal from "+sender)
    val client = sender()
    implicit val timeout = Timeout(30 seconds)
    val future = redisClient ? ("available", domain)
    future onFailure{
      case _ => println("available onFailure")
    }
    future.onSuccess{
      case "" => client ! new LinkInfo(""); println(f"Queue of $domain is empty, send fail signal back")
      case url:String => client ! new LinkInfo(url); println("Available success"); bf += url
      case unknown => println("Received unknown signal from "+sender.toString()+" " +unknown.getClass)
    }
  }

  def register(): Unit ={
    println("Received Register Signal from "+sender)
    val client = sender()
    val domain = less_domain_from_client_map()
    val rr = RegisterResult(domain)
    val domainClient = create_remote_actor(client.toString(), rr)

    domain_client_map.put(domain, domainClient.toString()::domain_client_map.get(domain).getOrElse(List()))
    context.watch(domainClient)
    client ! domainClient
    println(f"Registered $domain to $domainClient")
  }

  def less_domain_from_client_map(): String ={
    var less = ""
    var min = Int.MaxValue
    for((domain:String, list:List[String]) <- domain_client_map){
      if(list.length < min){
        min = list.length
        less = domain
      }
    }
    return less
  }

  def normal_preStart(): Unit ={
    println("Normal PreStart")
    val seeds:List[String] = Source.fromFile("seeds.txt").getLines().toList
    println("seeds: " + seeds)
    domains = seeds.map(s=>new URI(s).getHost)
    //Add seed
    seeds.foreach(s => {
      val domain = new URI(s).getHost
      redisClient ! ("init_queue", domain)
      redisClient ! ("add_queue", domain, s, Float.MaxValue)
    })
    //Initialize client num
    domains.foreach(d=>{
      //redisClient ! ("init_client_num", d)
      domain_client_map.put(d, List())
    })
  }

  def recover_preStart(): Unit ={
    println("Recover Prestart")
    implicit val timeout = Timeout(30 seconds)
    var domains_future = redisClient ? "recover_domains"
    domains_future onFailure {
      case _ => println("Error recovering domains from Redis, Killing itself"); self ! Kill
    }
    domains_future onSuccess {
      case ds:List[String] => {
        domains = ds
        //Initialize client num
        domains.foreach(d=>{
          //redisClient ! ("init_client_num", d)
          domain_client_map.put(d, List())
        })
        println("Successfully recover domains "+domains)
      }
      case _ => println("Unknown msg to recover_prestart, killing itself"); self ! Kill
    }
    //Recover BloomFilter
    val bf_future = redisClient ? "recover_bf"
    bf_future onFailure{
      case _ => println("Error recovering bf from Redis, Killing itself"); self ! Kill
    }
    bf_future onSuccess{
      case bf_ba :Array[Byte] =>{
        val bais = new ByteArrayInputStream(bf_ba)
        val ois  = new ObjectInputStream(bais)
        bf = ois.readObject().asInstanceOf[BloomFilter[String]]
        println("Successfully recover bf")
      }
      case _ => println("Unknown msg to recover_preStart, killing itself"); self ! Kill
    }
  }

  def backup_bf(): Unit ={
    val baos = new ByteArrayOutputStream()
    val oos  = new ObjectOutputStream(baos)
    oos.writeObject(bf)
    val bf_ba = baos.toByteArray
    redisClient ! ("backup_bf", bf_ba)
  }

  def create_remote_actor(path:String, rr:RegisterResult): ActorRef ={
    val pattern = new Regex("akka.tcp://.*@.*:[0-9]*")
    val result = pattern.findFirstIn(path)
    if(result.isEmpty){
      println("Cannot extract address from path")
      return null
    }

    val address_path = result.head.toString
    val address = AddressFromURIString(address_path)
    val ref = context.actorOf(Props.create(classOf[DomainClient], rr, token_score).withDeploy(Deploy(scope = RemoteScope(address))))
    context.watch(ref)
    println("Remote create actor: "+ref.toString)
    ref
  }
}