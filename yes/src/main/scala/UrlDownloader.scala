import java.io.{InputStreamReader, BufferedReader}
import java.net.{HttpURLConnection, URL}

import DistributedCommon.LinkInfo
import akka.actor.Status.Failure
import akka.actor.{Props, Actor}
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

import scala.util.matching.Regex

class UrlDownloader(val domain:String, val token_score:Map[String, Float]) extends Actor{
  val parent = context.parent
  var hp = context.actorOf(Props(new HTMLParser(parent, domain, token_score)))
  import context.dispatcher

  override def preStart(): Unit ={
    parent ! Available
  }

  def receive = {
    case LinkInfo("") =>
      val delayed_time = (10 seconds)
      println(f"Request new link fail for $domain, resend request after "+delayed_time)
      context.system.scheduler.scheduleOnce(delayed_time, parent, Available)
    case li:LinkInfo =>
      getHttp(li)
      parent ! Available
    case unknown => println(sender.path+" sended an unknown msg to "+self.toString); println(unknown.getClass)
  }

  def getHttp(li:LinkInfo): Unit = {
    val url = li.url
    println(f"crawling: $url")
    try {
      val html = getHTML(url)
      if (html != "") {
        li.html = html
        hp ! li
        //println("Crawled html for "+url)
      }
    }
    catch{
      case e: Exception => e.printStackTrace()
    }
  }

  def getHTML(url:String): String ={
    try{
      val urlObject = new URL(url)
      val urlConn = urlObject.openConnection().asInstanceOf[HttpURLConnection]


      urlConn.setRequestMethod("GET")
      urlConn.setConnectTimeout(10000)
      urlConn.setReadTimeout(10000)
      urlConn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
      urlConn.connect()

      val reader = new BufferedReader(new InputStreamReader(urlConn.getInputStream, "UTF-8"))
      val stringBuilder = new StringBuilder()

      var line:String = null
      line = reader.readLine()
      while (line!=null){
        stringBuilder.append(line+"\n")
        line = reader.readLine()
      }
      val r = stringBuilder.toString()
      stringBuilder.clear()
      reader.close()
      return r
    }catch{
      case e: Exception => {
        e.printStackTrace()
        return ""
      }
    }
  }
}
