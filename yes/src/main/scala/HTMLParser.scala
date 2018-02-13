import java.net.{URI, URL}

import DistributedCommon.LinkInfo
import PriorityCalculator.WordPreprocessor
import akka.actor.{ActorRef, Actor, ActorSelection}
import com.gravity.goose.{Configuration, Goose}
import org.htmlcleaner.{TagNode, CleanerProperties, DomSerializer, HtmlCleaner}

import scala.collection.mutable.ListBuffer
import scala.util.Random
import scala.util.matching.Regex

class HTMLParser(val client:ActorRef, val domain:String, val token_score:Map[String, Float]) extends Actor{
  val goose = new Goose(new Configuration)
  val wordpreprocessor = new WordPreprocessor()

  override def preStart()={
    println("Parser start")
  }

  def receive={
    case "stop" => {println("Parser stop"); context.stop(self)}
    case li:LinkInfo => parseHTML(li)
    case _ => println(sender.toString+" sent unknown msg to "+self.toString)
  }

  def parseHTML(li:LinkInfo): Unit ={
    val (url, html) = (li.url, li.html)
    //extract article with goose, if ok then send to article writer
    val article = goose.extractContent(url, html)
    if (article.cleanedArticleText.length > 500 && article.title.length() > 0) {
      //println(f"Sending article: $url")
      li.title = article.title
      li.content = article.cleanedArticleText
      li.html = ""
      li.score = getPriority(li.content)
      //server ! new Article(url, article.title, article.cleanedArticleText)
      client ! ("write", li)
    }

    //find out links with xpath
    val tagNode = new HtmlCleaner().clean(html)
    val doc = new DomSerializer(new CleanerProperties()).createDOM(tagNode)
    val nodes = tagNode.evaluateXPath("//a")
    nodes.foreach(n => {
      try {
        val relativeURL = n.asInstanceOf[TagNode].getAttributeByName("href")
        val mergedURL = (new URL(new URL(url), relativeURL)).toString.split("#")(0)

        //if (!regex.findFirstIn(mergedURL).isEmpty && !mergedURL.cogentains("#")){
        if((new URI(mergedURL)).getHost == domain){
          val anchor_text = n.asInstanceOf[TagNode].getText.toString
          val new_li = new LinkInfo(mergedURL)
          new_li.mother(li)
          new_li.anchor_text = anchor_text
          new_li.priority = getPriority(anchor_text)

          new_li.html = ""
          client ! ("enqueue", new_li)
        }
      }catch {
        case e:Exception =>
      }
    })
  }



  def getPriority(str:String): Float ={
    var score = 0.0.asInstanceOf[Float]
    val used = ListBuffer[String]()
    val tokens = wordpreprocessor.tokenizeString(str)

    tokens.foreach(token => {
      if (token_score.contains(token)) {
        used += token
        score += token_score.get(token).get
      }
    })
    return score
  }
}
