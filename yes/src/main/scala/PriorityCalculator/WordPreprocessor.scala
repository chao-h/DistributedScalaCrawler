package PriorityCalculator
import java.io.{IOException, StringReader}

import org.apache.lucene.analysis.en.EnglishAnalyzer
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute

import scala.collection.mutable.ListBuffer

class WordPreprocessor() extends Serializable{
  val analyzer = new EnglishAnalyzer()

  def tokenizeString(str:String):List[String]={
    val result = ListBuffer[String]()
    val analyzer = new EnglishAnalyzer()
    try{
      val stream = analyzer.tokenStream(null, new StringReader(str))
      stream.reset()
      while(stream.incrementToken()){
        result.+=(stream.getAttribute(classOf[CharTermAttribute]).toString())
      }
    }catch{
      case e:IOException => e.printStackTrace()
    }
    return result.toList
  }
}
