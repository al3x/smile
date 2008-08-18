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

    "do a 'get'" in {
      server = new FakeMemcacheServer(Receive(9) ::
        Send("VALUE cat 0 5\r\nhello\r\nEND\r\n".getBytes) :: Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      m.getString("cat") mustEqual "hello"
    }

    "do an empty 'get'" in {
      server = new FakeMemcacheServer(Receive(9) :: Send("END\r\n".getBytes) :: Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      m.get("cat") mustEqual None
    }

    "do a multi-'get'" in {
      server = new FakeMemcacheServer(Receive(9) ::
        Send("VALUE cat 0 5\r\nhello\r\nVALUE dog 0 7\r\ngoodbye\r\nEND\r\n".getBytes) :: Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      m.getString(Array("cat", "dog")) mustEqual Map("cat" -> "hello", "dog" -> "goodbye")
    }

    "do two 'get's on the same connection" in {
      server = new FakeMemcacheServer(Receive(9) ::
        Send("VALUE cat 0 5\r\nhello\r\nEND\r\n".getBytes) ::
        Receive(9) ::
        Send("VALUE dog 0 7\r\ngoodbye\r\nEND\r\n".getBytes) ::
        Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      m.getString("cat") mustEqual "hello"
      m.getString("dog") mustEqual "goodbye"
    }

    "timeout on a slow 'get'" in {
      server = new FakeMemcacheServer(Receive(9) :: Sleep(1200) :: Send("END\r\n".getBytes) :: Nil)
      server.start

      val m = new MemcacheServer("localhost", server.port, 1)
      m.pool = pool
      // it bothers me that this has to be a whole second. but mina doesn't support msec yet.
      m.pool.readTimeout = 1000
      m.getString("cat") must throwA(new MemcacheServerTimeout)
    }
  }
}
