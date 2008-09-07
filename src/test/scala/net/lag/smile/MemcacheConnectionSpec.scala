package net.lag.smile

import net.lag.naggati.Steps._
import java.nio.ByteOrder
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{AbstractIoSession, DummySession, IoSession}
import org.apache.mina.filter.codec._
import org.specs._


object MemcacheConnectionSpec extends Specification {

  val pool = new ServerPool
  var server: FakeMemcacheConnection = null

  def data(v: Option[MemcacheResponse.Value]) = {
    v match {
      case None => ""
      case Some(value) => new String(value.data, "utf-8")
    }
  }

  def data(map: Map[String, MemcacheResponse.Value]) = {
    Map.empty[String, String] ++ (for ((k, v) <- map) yield (k, new String(v.data, "utf-8")))
  }


  "MemcacheConnection" should {
    doAfter {
      server.stop
    }


    "connect to localhost" in {
      server = new FakeMemcacheConnection(Nil)
      server.start

      val m = new MemcacheConnection("localhost", server.port, 1)
      m.pool = pool
      m.ensureConnected mustBe true
      server.awaitConnection(500) mustBe true
    }

    "correctly indicate a failed connection" in {
      server = new FakeMemcacheConnection(Nil)
      server.start

      val m = new MemcacheConnection("localhost", server.port + 1, 1)
      m.pool = pool
      m.ensureConnected mustBe false
      server.awaitConnection(500) mustBe false
    }

    "get" in {
      "a single value" in {
        server = new FakeMemcacheConnection(Receive(9) ::
          Send("VALUE cat 0 5\r\nhello\r\nEND\r\n".getBytes) :: Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        data(m.get("cat")) mustEqual "hello"
        server.fromClient mustEqual List("get cat\r\n")
      }

      "an empty value" in {
        server = new FakeMemcacheConnection(Receive(9) :: Send("END\r\n".getBytes) :: Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        m.get("cat") mustEqual None
        server.fromClient mustEqual List("get cat\r\n")
      }

      "multiple values" in {
        server = new FakeMemcacheConnection(Receive(13) ::
          Send("VALUE cat 0 5\r\nhello\r\nVALUE dog 0 7\r\ngoodbye\r\nEND\r\n".getBytes) :: Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        data(m.get(Array("cat", "dog"))) mustEqual Map("cat" -> "hello", "dog" -> "goodbye")
        server.fromClient mustEqual List("get cat dog\r\n")
      }

      "two on the same connection" in {
        server = new FakeMemcacheConnection(Receive(9) ::
          Send("VALUE cat 0 5\r\nhello\r\nEND\r\n".getBytes) ::
          Receive(9) ::
          Send("VALUE dog 0 7\r\ngoodbye\r\nEND\r\n".getBytes) ::
          Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        data(m.get("cat")) mustEqual "hello"
        data(m.get("dog")) mustEqual "goodbye"
        server.fromClient mustEqual List("get cat\r\n", "get dog\r\n")
      }

      "timeout" in {
        server = new FakeMemcacheConnection(Receive(9) :: Sleep(1200) :: Send("END\r\n".getBytes) :: Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        // it bothers me that this has to be a whole second. but mina doesn't support msec yet.
        m.pool.readTimeout = 1000
        data(m.get("cat")) must throwA(new MemcacheServerTimeout)
      }

      "throw an exception for a bad server" in {
        server = new FakeMemcacheConnection(Receive(9) ::
          Send("CLIENT_ERROR i feel ill\r\n".getBytes) :: Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        data(m.get("cat")) must throwA(new MemcacheServerException(""))
      }
    }

    "set" in {
      "a single value" in {
        server = new FakeMemcacheConnection(Receive(24) :: Send("STORED\r\n".getBytes) :: Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        m.set("cat", "hello".getBytes, 0, 500)
        server.fromClient mustEqual List("set cat 0 500 5\r\nhello\r\n")
      }

      "with server error" in {
        server = new FakeMemcacheConnection(Receive(24) :: Send("NOT_STORED\r\n".getBytes) :: Nil)
        server.start

        val m = new MemcacheConnection("localhost", server.port, 1)
        m.pool = pool
        m.set("cat", "hello".getBytes, 0, 500) must throwA(new MemcacheServerException(""))
        server.fromClient mustEqual List("set cat 0 500 5\r\nhello\r\n")
      }
    }
  }
}
