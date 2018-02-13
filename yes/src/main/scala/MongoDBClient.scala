import DistributedCommon.LinkInfo
import akka.actor.{Kill, Actor}
import breeze.linalg.eig.Eig_DM_Impl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mongodb.MongoClientOptions
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject

//import org.mongodb.scala.connection.ConnectionPoolSettings
//import org.mongodb.scala.{MongoClientSettings, Completed, Observer, MongoClient}

/**
  * Created by moo on 4/12/16.
  */
class MongoDBClient extends Actor{
  var mongoClient:MongoClient = _
  initMongoClient()
  val database = mongoClient("Crawler")
  val collection = database("LinkInfo")
  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

  override def preStart(): Unit ={
    println(self.toString+" created")
  }

  def receive ={
    case "ready" => sender ! "ready"
    case ("write", li:LinkInfo) => write(li)
    case unknown => println("unknown message to mongodbClient "+unknown.getClass)
  }

  def write(li:LinkInfo): Unit ={
    val output = mapper.writeValueAsString(li)
    val dbObject = MongoDBObject(output)
    try {
      val result = collection.insert(dbObject)
      if(result.wasAcknowledged()){
        println("[Mongodb] write acknowledged")
      }
    }catch{
      case e: Throwable =>
        println("Mongodb Write Error "+e.getClass+" killing itself")
        self ! Kill
    }
    /*
    val output = mapper.writeValueAsString(li)
    val doc = org.mongodb.scala.bson.collection.immutable.Document(output)
    collection.insertOne(doc).subscribe(new Observer[Completed] {
      override def onError(e: Throwable): Unit = println("Mongodb Write Failed")

      override def onComplete(): Unit = {}//println("Mongodb Write Complete")

      override def onNext(result: Completed): Unit = println("Mongodb Write Inserted "+li.url)
    })*/
  }

  def initMongoClient(): Unit ={
    val options = MongoClientOptions.builder().
      connectTimeout(3000).
      socketTimeout(3000).
      serverSelectionTimeout(3000).
      build()
    mongoClient = MongoClient("localhost", options)
    try {
      mongoClient.getAddress
    }catch{
      case e: Throwable =>
        println("Error init mongoclient, killing itself")
        self ! Kill
    }
  }
}
