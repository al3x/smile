package net.lag.smile.stress

import net.lag.configgy.Configgy
import net.lag.extensions._
import net.lag.logging.{Level, Logger}
import net.lag.smile.MemcacheClient
import java.util.Random
import java.util.concurrent.CountDownLatch


object ManyGets {
  def report(name: String, count: Int)(f: => Unit): Unit = {
    val start = System.currentTimeMillis
    f
    val duration = System.currentTimeMillis - start
    val average = duration * 1.0 / count
    println("%s: %d in %d msec (%.2f msec each)".format(name, count, duration, average))
  }

  // get the same value N times in a row.
  def serialGets(count: Int) = {
    println("serial gets: " + count)
    val cache = MemcacheClient.create(Array("localhost"), "default", "crc32-itu")

    val key = "toasters"
    val r = new Random
    val value = "x" + r.nextInt(1000000)
    cache.set(key, value)

    report("toasters", count) {
      for (i <- 1 to count) {
        if (cache.get(key) != Some(value)) {
          throw new Exception("aiieeee!")
        }
      }
    }
    cache.shutdown
  }

  // get the same value N times in a row from each of M threads.
  def parallelGets(count: Int, threads: Int) = {
    println("parallel gets: " + count + " on " + threads + " threads")
    val cache = MemcacheClient.create(Array("localhost"), "default", "crc32-itu")

    val key = "toasters"
    val r = new Random
    val value = "x" + r.nextInt(1000000)
    cache.set(key, value)

    val latch = new CountDownLatch(1)
    var threadList: List[Thread] = Nil

    for (i <- 1 to threads) {
      val t = new Thread {
        override def run() = {
          latch.await
          for (i <- 1 to count) {
            if (cache.get(key) != Some(value)) {
              throw new Exception("aiieeee!")
            }
          }
        }
      }
      t.start
      threadList = t :: threadList
    }

    report("toasters", count * threads) {
      latch.countDown
      for (t <- threadList) t.join
    }
    cache.shutdown
  }

  def main(args: Array[String]): Unit = {
    Logger.get("").setLevel(Logger.TRACE)
    serialGets(1000)
    parallelGets(1000, 10)
  }
}
