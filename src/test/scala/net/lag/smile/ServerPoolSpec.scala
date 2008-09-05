package net.lag.smile

import org.specs._


object ServerPoolSpec extends Specification {
  "ServerPool" should {
    "parse a server description" in {
      val m1 = ServerPool.makeConnection("10.0.1.1:11211 600")
      m1.hostname mustEqual "10.0.1.1"
      m1.port mustEqual 11211
      m1.weight mustEqual 600
      val m2 = ServerPool.makeConnection("10.0.1.2:11511 300")
      m2.hostname mustEqual "10.0.1.2"
      m2.port mustEqual 11511
      m2.weight mustEqual 300
    }
  }
}
