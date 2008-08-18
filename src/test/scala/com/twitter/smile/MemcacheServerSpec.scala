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
    doAfter {
      server.stop
    }


    "connect to localhost" in {
      server = new FakeMemcacheServer(Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      m.ensureConnected mustBe true
      server.awaitConnection(500) mustBe true
    }

    "correctly indicate a failed connection" in {
      server = new FakeMemcacheServer(Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port + 1, 1)
      m.pool = pool
      m.ensureConnected mustBe false
      server.awaitConnection(500) mustBe false
    }

    "do a GET" in {
      server = new FakeMemcacheServer(Receive(9) ::
        Send("VALUE cat 0 5\r\nhello\r\nEND\r\n".getBytes) :: Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      m.getString("cat") mustEqual "hello"
    }
  }
}
