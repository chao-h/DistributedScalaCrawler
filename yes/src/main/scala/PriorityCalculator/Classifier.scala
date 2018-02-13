package PriorityCalculator

import scala.collection.mutable

class Classifier {
  val wordpreprocessor = new WordPreprocessor
  val wcount = mutable.HashMap[(String, String), Int]()
  val ccount = mutable.HashMap[String, Int]()
  val probs = mutable.HashMap[String, Double]()

  def train(category: String, text: String): Unit ={
    wordpreprocessor.tokenizeString(text).foreach(word =>
      wcount.put((category, word), 1+wcount.get((category, word)).getOrElse(0)))
    ccount.put(category, 1+ccount.get(category).getOrElse(0))
  }

  def wordProb(word: String, cat: String): Double ={
    if(! wcount.contains((cat, word)))
      return 0.0
    wcount((cat, word)) / ccount(cat).floatValue
  }

  private def wordWeightedAverage(word: String, cat: String): Double = {
    val weight = 1
    val assumed_prob = 0.5
    // current probability
    val basic_prob = wordProb(word, cat)
    // count the number of times this word has appeared in all categories
    var totals = 0
    ccount foreach (c =>
      if(wcount.contains((c._1, word)))
        totals += wcount((c._1, word))
      )
    // final weighted average
    (weight * assumed_prob + totals * basic_prob) / (weight + totals)
  }

  private def totalCountCat(): Int = {
    ccount.foldLeft(0)(_+_._2)
  }

  private def docProb(text: String, cat: String): Double = {
    var prob = 1.0
    wordpreprocessor.tokenizeString(text).foreach(word =>
      prob *= wordWeightedAverage (word, cat)
    )
    prob
  }

  def textProb(text: String, cat: String): Double = {
    val catProb = ccount.get(cat).getOrElse(0) / totalCountCat().floatValue()
    val wProb = docProb(text, cat)
    catProb * wProb
  }

  private def catScores(text: String) {
    ccount foreach (c => probs.put(c._1, textProb(text, c._1)))
  }

  def classify(text: String): String = {
    catScores(text)
    probs.maxBy(_._2)._1
  }
}

