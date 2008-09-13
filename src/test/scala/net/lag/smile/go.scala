package net.lag.smile

import net.lag.configgy.Configgy
import net.lag.logging.{Level, Logger}
import net.lag.configgy.Configgy
//import net.lag.smile.MemcacheClient


object go {
  def main(args: Array[String]): Unit = {
    Configgy.configure("/Users/robey/code/scala/smile/test.conf")
    Logger.get("").setLevel(Logger.TRACE)
    val cache = MemcacheClient.create(Configgy.config.getAttributes("memcache").get)
    println(cache)
    cache.set("robey", "calgon take me away!")
    cache.shutdown
  }
}
