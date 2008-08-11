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
  private var written: List[ServerResponse] = Nil

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
          written = written + obj.asInstanceOf[ServerResponse]
        }
      }
    }


    "decode simple errors" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "CLIENT_ERROR you suck!\r\n")
      written mustEqual List(ClientError("you suck!"))

      quickDecode(decoder, "SERVER_ERROR i suck!\n")
      written mustEqual List(ClientError("you suck!"), ServerError("i suck!"))

      quickDecode(decoder, "ERROR\r\n")
      written mustEqual List(ClientError("you suck!"), ServerError("i suck!"), Error)

      quickDecode(decoder, "NOT_FOUND\r\n")
      written mustEqual List(ClientError("you suck!"), ServerError("i suck!"), Error, NotFound)
    }

    "decode values" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "VALUE hippos 124 5\r\nhello\r\n")
      written mustEqual List(Value("hippos", 124, "", "hello".getBytes))

      quickDecode(decoder, "END\r\n")
      written mustEqual List(Value("hippos", 124, "", "hello".getBytes), EndOfResults)
    }

    "decode incr/decr result" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "413\r\n")
      written mustEqual List(NewValue(413))
    }

    "decode values in chunks" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "VALUE hippos 12")
      written mustEqual Nil
      quickDecode(decoder, "4 5\r\nhello\r\nVALUE hat 3 6 105\r\ncommie\r\nEND\r\n")
      written mustEqual List(Value("hippos", 124, "", "hello".getBytes),
                             Value("hat", 3, "105", "commie".getBytes),
                             EndOfResults)
    }

    "decode various simple responses" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "STORED\r\n")
      written mustEqual List(Stored)

      quickDecode(decoder, "NOT_STORED\r\n")
      written mustEqual List(Stored, NotStored)

      quickDecode(decoder, "EXISTS\r\n")
      written mustEqual List(Stored, NotStored, Exists)

      quickDecode(decoder, "DELETED\r\n")
      written mustEqual List(Stored, NotStored, Exists, Deleted)
    }
  }
}
