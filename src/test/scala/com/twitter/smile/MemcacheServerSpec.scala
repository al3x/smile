package com.twitter.smile

import com.twitter.tomservo.Steps._
import java.nio.ByteOrder
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{AbstractIoSession, DummySession, IoSession}
import org.apache.mina.filter.codec._
import org.specs._


object MemcacheServerSpec extends Specification {

  val pool = new ServerPool
  var server: FakeMemcacheServer = null


  "MemcacheServer" should {
    doBefore {
      server = new FakeMemcacheServer
      server.start
    }

    doAfter {
      server.stop
    }

    "connect to localhost" in {
      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      m.ensureConnected mustBe true
      server.awaitConnection(500) mustBe true
    }
  }
}
