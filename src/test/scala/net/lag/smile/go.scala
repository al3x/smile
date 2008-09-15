package net.lag.smile

import net.lag.configgy.Configgy
import net.lag.logging.{Level, Logger}
import net.lag.configgy.Configgy
//import net.lag.smile.MemcacheClient


object go {
  def main(args: Array[String]): Unit = {
    Logger.get("").setLevel(Logger.TRACE)
    val cache = MemcacheClient.create(Array("localhost"), "default", "crc32-itu")
    println(cache)
    cache.set("robey", "calgon take me away!")
    cache.shutdown
  }
}
