/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.configgy.Configgy
import org.specs._
import scala.collection.mutable
import java.io.{BufferedReader, InputStreamReader}


object KetamaNodeLocatorSpec extends Specification {

  def newTestLocator = {
    val servers = List(
      "10.0.1.1:11211 600",
      "10.0.1.2:11211 300",
      "10.0.1.3:11211 200",
      "10.0.1.4:11211 350",
      "10.0.1.5:11211 1000",
      "10.0.1.6:11211 800",
      "10.0.1.7:11211 950",
      "10.0.1.8:11211 100"
    )
    val pool = new ServerPool
    val connections = for (s <- servers) yield ServerPool.makeConnection(s, pool)
    pool.servers = connections.toArray
    val ketama = new KetamaNodeLocator
    ketama.setPool(pool)
    ketama
  }


  "KetamaNodeLocator" should {
    "be compatible with a standard benchmark" in {
      val stream = getClass.getResourceAsStream("/resources/ketama_results")
      val reader = new BufferedReader(new InputStreamReader(stream))
      val expected = new mutable.ListBuffer[Array[String]]
      var line: String = null
      do {
        line = reader.readLine
        if (line != null) {
          val segments = line.split(" ")
          segments.length mustEqual 4
          expected += segments
        }
      } while (line != null)
      expected.size mustEqual 99

      val ketama = newTestLocator
      var count = 0
      for (testcase <- expected) {
        val connection = ketama.findNode(testcase(0).getBytes("utf-8"))
        if (connection.hostname != testcase(3)) {
          println("testcase line " + (count + 1))
        }
        connection.hostname mustEqual testcase(3)
        count += 1
      }
    }

    "be compatible with a test on a very large server list" in {
      ClassLoader.getSystemClassLoader.getResourceAsStream("resources/ketama_results").read()
      Configgy.configureFromResource("resources/test1.conf")
      val pool = ServerPool.fromConfig(Configgy.config.getConfigMap("memcache").get)
      val ketama = new KetamaNodeLocator
      ketama.setPool(pool)
      ketama

      val expected = List(
        List("apple", "daemon003"),
        List("beanie baby", "cluster049"),
        List("california", "cluster027"),
        List("dead dog", "cluster062"),
        List("entrenched", "cluster001"),
        List("FFS", "cluster044"),
        List("GIGO", "cluster065"),
        List("hello sailor", "cluster049"),
        List("inner universe", "cluster015"),
        List("jump!", "daemon009"),
        List("kangaroo meat", "cluster001")
        )
      var count = 0
      for (testcase <- expected) {
        val connection = ketama.findNode(testcase(0).getBytes("utf-8"))
        if (connection.hostname != testcase(1)) {
          println("testcase line " + (count + 1))
        }
        connection.hostname mustEqual testcase(1)
        count += 1
      }
    }
  }
}
