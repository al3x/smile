package net.lag.smile.stress

import net.lag.configgy.Configgy
import net.lag.logging.{Level, Logger}
import net.lag.configgy.Configgy
import net.lag.smile.MemcacheClient
import java.util.Random


object ManyGets {
  def main(args: Array[String]): Unit = {
    Configgy.configure("/Users/robey/code/scala/smile/test.conf")
    Logger.get("").setLevel(Logger.TRACE)
    val cache = MemcacheClient.create(Configgy.config.getAttributes("memcache").get)
    println(cache)

    val key = "toasters"
    val r = new Random
    val value = "x" + r.nextInt(1000000)
    cache.set(key, value)

    val start = System.currentTimeMillis
    for (i <- 1 to 1000) {
      if (cache.get(key) != Some(value)) {
        throw new Exception("aiieeee!")
      }
    }
    val duration = System.currentTimeMillis - start
    println("1000 toasters in " + duration + " msec")

    cache.shutdown
  }
}
