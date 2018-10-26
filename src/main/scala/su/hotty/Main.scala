package su.hotty

import org.joda.time.DateTime
import su.hotty.Helper._

object Main extends App {
  println("Start "+getDateStr(new DateTime()));
  try{
    if (args.length > 0)
      for (arg <- args){
        if (arg.startsWith("-path=")) pathToFiles = arg.replaceAll("-path=","")
        if (arg.startsWith("-host=")) host = arg.replaceAll("-host=","")
        if (arg.startsWith("-queueNameContains=")) queueNameContains = arg.replaceAll("-queueNameContains=","")
      }

    println(s"Run params:\n java -jar rabbitmqscaner -path=$pathToFiles -host=$host -queueNameContains=$queueNameContains")
    if (host.isEmpty) {
      println("Parameter '-host' is required!")
      System.exit(0);
    }

    executeGlobalScan()
    executeScan(getMapper())

    while (true) {
      Thread.sleep(60000)
      executeScan(getMapper())
    }
  } catch {
    case e: Throwable => System.exit(0)
  }

}
