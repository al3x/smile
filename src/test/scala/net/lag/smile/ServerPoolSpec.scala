/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.configgy.Configgy
import org.specs._


object ServerPoolSpec extends Specification {
  "ServerPool" should {
    "parse a server description" in {
      val m1 = ServerPool.makeConnection("10.0.1.1:11211 600", null)
      m1.hostname mustEqual "10.0.1.1"
      m1.port mustEqual 11211
      m1.weight mustEqual 600
      val m2 = ServerPool.makeConnection("10.0.1.2:11511 300", null)
      m2.hostname mustEqual "10.0.1.2"
      m2.port mustEqual 11511
      m2.weight mustEqual 300
    }

    "read a config file" in {
      ClassLoader.getSystemClassLoader.getResourceAsStream("resources/ketama_results").read()
      Configgy.configureFromResource("resources/test1.conf")
      val pool = ServerPool.fromConfig(Configgy.config.getAttributes("memcache").get)
      pool.servers.size mustEqual 77
      pool.servers(0).toString must include("daemon001:11211 weight=1")
      pool.servers(1).toString must include("daemon002:11211 weight=1")
      pool.servers(23).toString must include("twitter-web007:11211 weight=1")
      pool.servers(76).toString must include("twitter-web068:11211 weight=2")
      pool.readTimeout mustEqual 3000
      pool.retryDelay mustEqual 42000
    }
  }
}
