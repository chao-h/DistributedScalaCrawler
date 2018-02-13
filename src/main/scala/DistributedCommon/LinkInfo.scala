package DistributedCommon

import java.net.URI

case class LinkInfo(val url:String) {
  val domain = new URI(url).getHost
  var priority:Double = _
  var score:Double = _
  var html:String = _
  var title:String = _
  var content:String = _
  var anchor_text:String = _

  var mother_priority:Double = _
  var mother_score: Double = _

  def mother(mother:LinkInfo): Unit ={
    this.mother_priority = mother.priority
    this.mother_score = mother.score
  }

  def setPriority(anchor_priority:Double): Unit ={
    this.priority = anchor_priority
    //this.priority = 0.7*anchor_priority + 0.3*mother_score
  }
}
