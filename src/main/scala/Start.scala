import PriorityCalculator.{WordPreprocessor, Word2Vec}
import akka.actor._
import akka.pattern.{Backoff, BackoffSupervisor}
import sun.misc.{SignalHandler, Signal}

import scala.concurrent.duration._
import scala.io.StdIn._

import java.io.File
import scala.collection.mutable
import scala.math.log

/**
  * Created by moo on 4/6/16.
  */
object Start {

  def main(args: Array[String]) {
    val method = readLine("1: Word2Vec\n2: DScore\n3: Word2Vec+Dscore\nPlease input method: ").toString
    val token_score: Map[String, Float] = set_token_score(method)

    val serverSystem = ActorSystem("ServerSystem")

    var recover = false
    if(args.length>=1 && args(0)=="recover") recover=true

    val max_len = 1000
    val redisClient = create_redisClient(serverSystem, max_len)
    val mongodbClient = create_mongodbClient(serverSystem)
    val server = create_server(serverSystem, recover, token_score)

    import serverSystem.dispatcher
    serverSystem.scheduler.schedule(
      0 milliseconds,
      5 minutes,
      redisClient,
      "save"
    )
    serverSystem.scheduler.schedule(
      0 milliseconds,
      5 minutes,
      server,
      "backup_bf"
    )

    signal_handle(serverSystem)
  }

  def create_redisClient(serverSystem:ActorSystem, max_len:Int): ActorRef ={
    val redisClientSupervisor = BackoffSupervisor.props(
      Backoff.onFailure(
        Props(new RedisClient(max_len)),
        childName = "redisClient",
        minBackoff = 3 seconds,
        maxBackoff = 10 seconds,
        randomFactor = 0.2
      ).withManualReset
        .withSupervisorStrategy(
          OneForOneStrategy(){
            case _ : ActorKilledException => {
              println("redisClient Killed, restarting")
              SupervisorStrategy.Restart
            }
            case _ => SupervisorStrategy.escalate
          }
        )
    )
    val redisClient = serverSystem.actorOf(redisClientSupervisor, "redisClientSupervisor")
    redisClient
  }

  def create_mongodbClient(serverSystem:ActorSystem): ActorRef ={
    val mongodbClientSupervisor = BackoffSupervisor.props(
      Backoff.onFailure(
        Props(new MongoDBClient),
        childName = "mongodbClient",
        minBackoff = 3 seconds,
        maxBackoff = 10 seconds,
        randomFactor = 0.2
      ).withManualReset
        .withSupervisorStrategy(
          OneForOneStrategy(){
            case _ : ActorKilledException => {
              println("mongodbClient Killed, restarting")
              SupervisorStrategy.Restart
            }
            case _ => {
              println("Unhandled exception from MongoClient, escalating")
              SupervisorStrategy.escalate
            }
          }
        )
    )
    val mongodbClient = serverSystem.actorOf(mongodbClientSupervisor, "mongodbClientSupervisor")
    mongodbClient
  }

  def create_server(serverSystem:ActorSystem, recover:Boolean, token_score: Map[String, Float]): ActorRef ={
    val serverSupervisor = BackoffSupervisor.props(
      Backoff.onFailure(
        Props(new Server(recover, token_score)),
        childName = "server",
        minBackoff = 3 seconds,
        maxBackoff = 10 seconds,
        randomFactor = 0.2
      ).withManualReset
        .withSupervisorStrategy(
          OneForOneStrategy(){
            case _ : ActorKilledException => {
              println("Server Killed, restarting")
              SupervisorStrategy.Restart
            }
            case _:ActorInitializationException => {
              println("Server Initialize Exception, restarting")
              SupervisorStrategy.Restart
            }
            case _ => SupervisorStrategy.escalate
          }
        )
    )
    val server = serverSystem.actorOf(serverSupervisor, "serverSupervisor")
    server
  }

  def set_token_score(calc_method: String): Map[String, Float] ={
    var map: Map[String, Float] = Map[String, Float]()
    calc_method match{
      case "1" => map=Word2Vec_method(List[String]())
      case "2" => map=DScore_method()
      case _ =>
        val DScoreList: List[String] = DScore_method().map{case (k,v)=>k}(collection.breakOut): List[String]
        map = Word2Vec_method(DScoreList.slice(0,10))
    }

    def Word2Vec_method(assist: List[String]): Map[String, Float] = {
      val model = new Word2Vec
      val src = "articles.bin"
      println(f"Loading Word2Vec model from $src")
      model.load(src)

      var themes: List[String] = List()
      val wordpreprocessor = new WordPreprocessor()
      var results: List[(String, Float)] = List()
      do {
        val theme_str = readLine("Please input themes: ").toString
        themes = wordpreprocessor.tokenizeString(theme_str) ++ assist
        println("Theme: " + themes.toString)
        results = model.distance(themes, N = 100)
      }while(results.length <= 0)

      results = themes.map(theme => (theme, 1.0.asInstanceOf[Float])) ++ results
      model.pprint(results)
      return results.toMap[String,Float]
    }

    def DScore_method(): Map[String, Float]={
      def getListOfFiles(dir: String):List[File] = {
        val d = new File(dir)
        if (d.exists && d.isDirectory) {
          d.listFiles.filter(_.isFile).toList
        } else {
          List[File]()
        }
      }

      val pp = new WordPreprocessor();

      val tfdict = mutable.HashMap[String, Int]()
      val rdict  = mutable.HashMap[String, Int]()
      val ndict  = mutable.HashMap[String, Int]()
      val yes_files = getListOfFiles("yes/");
      val R = yes_files.length

      for (file <- yes_files){
        val content = scala.io.Source.fromFile(file).mkString;
        val token_list = pp.tokenizeString(content);
        val token_set = token_list.toSet;
        for (token <- token_set){
          tfdict.put(token, token_list.count(_==token) + tfdict.get(token).getOrElse(0));
          rdict.put(token, 1 + rdict.get(token).getOrElse(0));
          ndict.put(token, 1 + ndict.get(token).getOrElse(0));
        }
      }

      val no_files = getListOfFiles("no/");
      for (file <- no_files){
        val content = scala.io.Source.fromFile(file).mkString;
        val token_list = pp.tokenizeString(content);
        val token_set = token_list.toSet;
        for (token <- token_set){
          ndict.put(token, 1 + ndict.get(token).getOrElse(1));
        }
      }

      val N = yes_files.length + no_files.length;
      val Q = mutable.HashMap[String, Float]();
      for ((token,_) <- tfdict){
        val r = rdict.getOrElse(token, 0);
        val tf = tfdict.getOrElse(token, 0);
        val n = ndict.getOrElse(token, 0);
        try{
          val w = log(((r+0.5)/(R-r+0.5))/((n-r+0.5)/(N-n-R+r+0.5)));
          val Qt = w*((r+tf).toDouble/R)/2;
          Q.put(token, Qt.asInstanceOf[Float]);
        }catch{
          case e:java.lang.ArithmeticException  => Q.put(token, 0.0.asInstanceOf[Float]);
        }
      }

      val Qtable = Q.toList.sortWith((a,b)=>(a._2>b._2));
      val model = new Word2Vec
      model.pprint(Qtable)
      return Qtable.toMap[String, Float]
    }

    return map
  }

  def signal_handle(actorSystem: ActorSystem): Unit ={
    Signal.handle(new Signal("INT"), new SignalHandler() {
      override def handle(signal: Signal): Unit = {
        println("To be shut down gracefully!")
        actorSystem.terminate()
        return
      }
    })
  }
}
