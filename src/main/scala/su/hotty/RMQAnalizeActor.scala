package su.hotty

import java.io.PrintWriter

import akka.actor.{Actor, ActorContext, ActorRef}
import org.joda.time.DateTime
import su.hotty.Helper._

import scala.collection.mutable.ArrayBuffer

class RMQAnalizeActor extends Actor{
  override def receive = {
    case RMQueue(messagesTotal: Int, name: String, startRequestTime: Long) => {
      var lastProductiveRequestTime = startRequestTime
      val out: PrintWriter = null
      try {
        println(getDateStr(new DateTime()) + "------------------------------NEW_ACTOR-------------------" + self)
        if(!sleepingQueues.contains(name)) {
          val out = prepareFile(name + "_" + getDateStr(new DateTime()) + ".txt")
          out.println(s"Run calculate $name - $messagesTotal - $lastProductiveRequestTime")
          var total = messagesTotal
          val mpsBuffer = new ArrayBuffer[Double]
          var nullIterationsCount = 0
          while (total > 0 && !sleepingQueues.contains(name)) {
            Thread.sleep(1000) //TODO: sleep 1000
            val parsedQueue = getMapper().readValue[Map[String, Object]](
              exequteRequest(prepareUrl + "/%2f/" + name)
            )
            val requestTimeDifference = System.currentTimeMillis() - lastProductiveRequestTime
            val newMessagesTotal = Int.unbox(parsedQueue("messages"))
            if (newMessagesTotal<=total) {
              val messageCountDifference = total - newMessagesTotal
              if (messageCountDifference == 0) {
                nullIterationsCount += 1
                if (nullIterationsCount >= 1000) sleepingQueues += name
              } else {
                lastProductiveRequestTime += requestTimeDifference
                out.println(s"$name ---- $messageCountDifference messages by $requestTimeDifference miliseconds only $newMessagesTotal left")
                mpsBuffer += (messageCountDifference / (requestTimeDifference / 1000d))
              }
            } else {
              out.println(s"Queue $name grows up to $newMessagesTotal messages")
              if (mpsBuffer.nonEmpty) {
                printAverageToFile(out, mpsBuffer)
                mpsBuffer.clear()
              }
            }
            total = newMessagesTotal
            out.flush()
          }
          printAverageToFile(out,mpsBuffer)
          finishJob(out,context,name,self)
        }
      } finally {
        finishJob(out,context,name,self)
      }

    }
    case _ => println("Wrong type")
  }


  def printAverageToFile(out: PrintWriter, mpsBuffer: ArrayBuffer[Double]): Unit ={
    out.println(s"--- Average messages per second (m/s): ${mpsBuffer.sum / mpsBuffer.length} m/s")
  }

  def finishJob(out: PrintWriter, context: ActorContext, name: String, curActor: ActorRef): Unit ={
    if(out != null) out.close()
    workingQueues -= name
    context.stop(curActor)
  }
}