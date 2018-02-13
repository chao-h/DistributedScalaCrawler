package DistributedCommon

import PriorityCalculator.{WordPreprocessor}

import scala.collection.mutable.ListBuffer
import scala.io.StdIn._

/**
  * Created by moo on 4/24/16.
  */
class PriorityCalculator extends Serializable{
  val wordpreprocessor = new WordPreprocessor()

  def Word2Vec_priority(str:String): Float ={
    return 0.0.asInstanceOf[Float]
  }
}
