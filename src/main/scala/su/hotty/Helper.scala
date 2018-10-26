package su.hotty

import java.io.{File, PrintWriter}

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import dispatch._
import org.joda.time.DateTime
import scala.concurrent._
import ExecutionContext.Implicits.global

import scala.collection.mutable.ArrayBuffer

object Helper {

  var mapper: ObjectMapper with ScalaObjectMapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)

  val sleepingQueues = new ArrayBuffer[String]
  val workingQueues = new ArrayBuffer[String]
  var host = ""
  var pathToFiles = "./"
  var queueNameContains = ""

  case class RMQueue(messagesTotal: Int, name: String, startRequestTime: Long)

  def prepareUrl = "https://"+host+":55672/api/queues"

  def getMapper(): ObjectMapper with ScalaObjectMapper = mapper

  def generateActors(n: Int): Array[ActorRef] ={
    var resultList = new Array[ActorRef](n)
    for (i <- 0 to (n-1)) resultList(i) = ActorSystem("RabbitMqScaner").actorOf(Props(new RMQAnalizeActor))
    resultList
  }

  def generateActor(): ActorRef ={
    ActorSystem("RabbitMqScaner").actorOf(Props(new RMQAnalizeActor))
  }

  def exequteRequest(url: String): String = {
    val svc = dispatch.url(url)
    val authHeader = Map("Authorization" -> "Basic Z3Vlc3Q6Z3Vlc3Q=")
    val requestWithAuthentication = svc <:< authHeader
    val responce = Http.default(requestWithAuthentication OK as.String)
    responce()
  }

  def getDateStr(date: DateTime): String = {
    import org.joda.time.format.DateTimeFormat
    val fmt = DateTimeFormat.forPattern("yyyy-MM-dd_HH:mm:ss")
    fmt.print(date)
  }

  def prepareFile(filename: String): PrintWriter = {
    val file = new File(pathToFiles+filename)
    file.getParentFile().mkdirs()
    new PrintWriter(file){
      override def println(x:String):Unit = {
        val finnalString = getDateStr(new DateTime()) + " : " + x
        super.println(finnalString)
      }
    }
  }

  def executeScan(mapper: ObjectMapper with ScalaObjectMapper): Unit ={
    val parsed = mapper.readValue[List[scala.collection.mutable.Map[String,Object]]](
      exequteRequest(prepareUrl)
    )
    var listRequestTime = System.currentTimeMillis()
    for (q <- parsed){
      val messagesTotal: Int = Int.unbox(q("messages"))
      val name = q("name").toString;
      if ((queueNameContains.isEmpty || name.contains(queueNameContains)) && messagesTotal>0 && !workingQueues.contains(name)) {
        workingQueues += name
        generateActor() ! new RMQueue(messagesTotal, name, listRequestTime)
      }
    }
  }

  def executeGlobalScan(): Unit ={
    var actor = ActorSystem("ReconManagerSystem").actorOf(Props(new Actor{
      def receive =
      {
        case "Start" => {
          var oldMessagesTotal: Int = 0
          var startRequestTime: Long = System.currentTimeMillis()
          var out: PrintWriter = null
          var emptyMesagesRequestCount = 0
          try {
            out = prepareFile("TOTAL_MONITORING_" + getDateStr(new DateTime())+".txt")
            out.println(s"Run total calculate queueNameContains=$queueNameContains")
            while (true) {
              val parsed = getMapper().readValue[List[scala.collection.mutable.Map[String, Object]]](
                exequteRequest(prepareUrl)
              )
              val requestTimeDifference = System.currentTimeMillis() - startRequestTime
              var listRequestTime = System.currentTimeMillis()
              var newMessagesTotal: Int = 0
              for (q <- parsed) {
                val messages: Int = Int.unbox(q("messages"))
                val name = q("name").toString;
                if ((queueNameContains.isEmpty || name.contains(queueNameContains))) {
                  newMessagesTotal += messages
                  //generateActor() ! new RMQueue(messagesTotal, name, listRequestTime)
                }
              }
              val messageCountDifference = oldMessagesTotal - newMessagesTotal

              if (newMessagesTotal <= oldMessagesTotal) {
                out.println(s"TOTAL MESSAGES for all scaning queues ---- $messageCountDifference messages by $requestTimeDifference miliseconds and $newMessagesTotal left")
              } else out.println(s"TOTAL MESSAGES count grows up to $newMessagesTotal messages")
              oldMessagesTotal = newMessagesTotal
              out.flush()
              if (oldMessagesTotal==0 && newMessagesTotal == 0 && messageCountDifference == 0){
                emptyMesagesRequestCount += 1
                if (emptyMesagesRequestCount>100){
                  Thread.sleep(600000)
                  emptyMesagesRequestCount = 0
                }
              }
              startRequestTime = System.currentTimeMillis()
              Thread.sleep(6000)
            }
          } finally {
            if(out != null) out.close()
            self ! "Start"
          }
        }
        case _ => println(self+" -- WRONG ERROR")
      }
    }))
    actor ! "Start"
  }

}
