import DistributedCommon._
import akka.actor.{ActorRef, Actor}
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import scala.collection.mutable
import collection.JavaConversions._
import java.util

class RedisClient(val max_len:Int) extends Actor {
  val jedis = new Jedis("localhost")

  override def preStart(): Unit ={
    println(self.toString+" created")
  }

  def receive ={
    case _:ping => println(jedis.ping())
    case "ready" => sender ! "ready"
    case "save" => jedis.save()
    case ("backup_bf", bf_ba:Array[Byte]) => backup_bf(bf_ba)
    case "recover_domains" => recover_domains()
    case "recover_bf" => recover_bf()
    case ("init_queue", domain:String) => init_queue(domain)
    case ("add_queue", domain:String, url:String, score:Float) => add_queue(domain, url, score)
    case ("available", domain:String) => available(domain)
    case _ => println("unknwon msg to RedisClient")
  }

  def recover_domains(): Unit ={
    val domains = jedis.keys("*:queue").map(dq => dq.dropRight(":queue".length)).toList
    sender ! domains
  }

  def recover_bf(): Unit ={
    val key = "backup_bf".getBytes
    val bf_ba:Array[Byte] = jedis.get(key)
    sender ! bf_ba
  }

  def backup_bf(bf_ba:Array[Byte]): Unit ={
    val key = "backup_bf".getBytes
    jedis.set(key, bf_ba)
  }

  def available(domain:String): Unit ={
    val key = domain+":queue"
    val list = jedis.zrangeWithScores(key, -1, -1)
    if(!list.isEmpty) {
      val url = list.head.getElement
      val score = list.head.getScore
      sender ! url
      jedis.zrem(key, url)
      println(f"[Jedis] Popped $url from $key score $score")
    }else{
      sender ! ""
      println(f"[Jedis] $key is empty")
    }
  }

  def init_queue(domain:String): Unit ={
    val key = domain+":queue"
    jedis.del(key)
  }

  def add_queue(domain:String, url:String, score:Float): Unit ={
    val key = domain+":queue"
    jedis.zadd(key, score, url)
    //println(f"[Jedis] $url added to $key with score $score")
    while(jedis.zcard(key)>max_len){
      jedis.zremrangeByRank(key, 0, 0)
    }
  }

  def init_client_num(domain:String): Unit ={
    val key = "client_num"
    jedis.zadd(key, 0, domain)
    println(f"[Jedis] $domain client num initialized")
  }

  def get_lowest_domain_client_num(): Unit ={
    val key = "client_num"
    val list = jedis.zrange(key, 0, 0)
    if(list.isEmpty) sender ! ""
    else {
      val domain = list.head
      sender ! domain
      println(f"[Jedis] get lowest domain client num $domain")
      val num = jedis.zscore(key, domain)
      num match{
        case null => jedis.zadd(key, 0, domain)
        case _    => jedis.zadd(key, num+1, domain)
      }
    }
  }
}
