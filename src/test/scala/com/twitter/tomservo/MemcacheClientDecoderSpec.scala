package com.twitter.tomservo

import com.twitter.tomservo.Steps._
import java.nio.ByteOrder
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{AbstractIoSession, DummySession, IoSession}
import org.apache.mina.filter.codec._
import org.specs._


object MemcacheClientDecoderSpec extends Specification {

  private var fakeSession: IoSession = null
  private var fakeDecoderOutput: ProtocolDecoderOutput = null
  private var written: List[MemcachedResponse] = Nil

  def quickDecode(decoder: Decoder, s: String): Unit = quickDecode(decoder, s.getBytes)
  def quickDecode(decoder: Decoder, b: Array[Byte]): Unit = quickDecode(decoder, IoBuffer.wrap(b))
  def quickDecode(decoder: Decoder, buf: IoBuffer): Unit = {
    decoder.decode(fakeSession, buf, fakeDecoderOutput)
  }


  "MemcacheClientDecoder" should {
    doBefore {
      written = Nil
      fakeSession = new DummySession
      fakeDecoderOutput = new ProtocolDecoderOutput {
        override def flush = {}
        override def write(obj: AnyRef) = {
          written = written + obj.asInstanceOf[MemcachedResponse]
        }
      }
    }


    "decode simple errors" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "CLIENT_ERROR you suck!\r\n")
      written mustEqual List(MemcachedResponse.ClientError("you suck!"))

      written = Nil
      quickDecode(decoder, "SERVER_ERROR i suck!\n")
      written mustEqual List(MemcachedResponse.ServerError("i suck!"))

      written = Nil
      quickDecode(decoder, "ERROR\r\n")
      written mustEqual List(MemcachedResponse.Error)

      written = Nil
      quickDecode(decoder, "NOT_FOUND\r\n")
      written mustEqual List(MemcachedResponse.NotFound)
    }

    "decode values" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "VALUE hippos 124 5\r\nhello\r\n")
      written mustEqual List(MemcachedResponse.Value("hippos", 124, "", "hello".getBytes))

      written = Nil
      quickDecode(decoder, "END\r\n")
      written mustEqual List(MemcachedResponse.EndOfResults)
    }

    "decode incr/decr result" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "413\r\n")
      written mustEqual List(MemcachedResponse.NewValue(413))
    }

    "decode values in chunks" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "VALUE hippos 12")
      written mustEqual Nil
      quickDecode(decoder, "4 5\r\nhello\r\nVALUE hat 3 6 105\r\ncommie\r\nEND\r\n")
      written mustEqual List(MemcachedResponse.Value("hippos", 124, "", "hello".getBytes),
                             MemcachedResponse.Value("hat", 3, "105", "commie".getBytes),
                             MemcachedResponse.EndOfResults)
    }

    "decode various simple responses" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "STORED\r\n")
      written mustEqual List(MemcachedResponse.Stored)

      written = Nil
      quickDecode(decoder, "NOT_STORED\r\n")
      written mustEqual List(MemcachedResponse.NotStored)

      written = Nil
      quickDecode(decoder, "EXISTS\r\n")
      written mustEqual List(MemcachedResponse.Exists)

      written = Nil
      quickDecode(decoder, "DELETED\r\n")
      written mustEqual List(MemcachedResponse.Deleted)
    }
  }
}
