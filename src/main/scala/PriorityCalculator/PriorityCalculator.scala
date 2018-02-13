package PriorityCalculator

import DistributedCommon.LinkInfo
import akka.actor.{Actor, ActorRef}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.StdIn._

class PriorityCalculator() extends Actor{
  val wordprerocessor = new WordPreprocessor
  val src = "articles.bin"
  val model = new Word2Vec()
  println(f"Loading Word2Vec model from $src")
  model.load(src)
  val dict: Map[String,Float] = setThemes()
  val classifier = new Classifier
  var trained = false


  def receive = {
    case ("put", li:LinkInfo) => {
      li.setPriority(getScore(li.anchor_text))
      sender ! ("put_with_priority", li)
    }

    case ("getScore", str:String) => {
      sender ! getScore(str)
    }

    case ("update", best_article: mutable.PriorityQueue[LinkInfo]) => {
      println("update request received")
      update(best_article)
      sender ! "updated"
    }
    case _ => {
      println("Unknown request")
    }
  }

  def update(best_article: mutable.PriorityQueue[LinkInfo]) ={
    best_article.foreach(article => article.score=getScore(article.title+" "+article.content))
    def ordering = new Ordering[LinkInfo]{
      def compare(a:LinkInfo,b:LinkInfo)=
        a.score compare b.score
    }
    val baws = mutable.PriorityQueue[LinkInfo]()(ordering)
    best_article.foreach(article => baws+=article)
    //val articles = Random.shuffle(baws.take(50).toList).take(25)
    val articles = baws.take(20).toList
    for ( article <- articles){
      println("==============================")
      println("Please rate for this article: ")
      println("This article have score: "+article.score)
      println("Title: "+article.title)
      println("Content: \n"+article.content)
      val bool = readLine("Is this what you want? (y or n): ").toString
      bool match {
        case "y" => classifier.train("y", article.title+" "+article.content)
        case  _  => classifier.train("n", article.title+" "+article.content)
      }
    }
    println("End of rating")
    println("================================")
    trained = true
  }

  def getScore(str:String): Double ={
    if (!trained) {
      var score = 0.0
      val used = ListBuffer[String]()
      val tokens = wordprerocessor.tokenizeString(str)

      tokens.foreach(token => {
        if (dict.contains(token)) {
          used += token
          score += dict.get(token).get
        }
      })
      return score
    } else {
      return classifier.textProb(str, "y")
    }
  }

  def setThemes(): Map[String,Float] ={
    var themes: List[String] = List()
    var results: List[(String, Float)] = List()
    var dict: Map[String, Float] = Map()

    do {
      val theme_str = readLine("Please input themes: ").toString
      themes = wordprerocessor.tokenizeString(theme_str)
      println("Theme: " + themes.toString)
      results = model.distance(themes, N = 100)
    }while(results.length <= 0)
    results = themes.map(theme => (theme, 1.0.asInstanceOf[Float])) ++ results
    model.pprint(results)
    dict = results.toMap[String,Float]
    return dict
  }

}
