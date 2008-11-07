/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.naggati.Decoder
import net.lag.naggati.Steps._
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{AbstractIoSession, DummySession, IoSession}
import org.apache.mina.filter.codec._
import org.specs._
import scala.collection.mutable
import java.nio.ByteOrder


object MemcacheClientDecoderSpec extends Specification {

  private var fakeSession: IoSession = null
  private var fakeDecoderOutput: ProtocolDecoderOutput = null
  private var written = new mutable.ListBuffer[MemcacheResponse]

  def quickDecode(decoder: Decoder, s: String): Unit = quickDecode(decoder, s.getBytes)
  def quickDecode(decoder: Decoder, b: Array[Byte]): Unit = quickDecode(decoder, IoBuffer.wrap(b))
  def quickDecode(decoder: Decoder, buf: IoBuffer): Unit = {
    decoder.decode(fakeSession, buf, fakeDecoderOutput)
  }


  "MemcacheClientDecoder" should {
    doBefore {
      written.clear
      fakeSession = new DummySession
      fakeDecoderOutput = new ProtocolDecoderOutput {
        override def flush = {}
        override def write(obj: AnyRef) = {
          written += obj.asInstanceOf[MemcacheResponse]
        }
      }
    }


    "decode simple errors" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "CLIENT_ERROR you suck!\r\n")
      written.toList mustEqual List(MemcacheResponse.ClientError("you suck!"))

      written.clear
      quickDecode(decoder, "SERVER_ERROR i suck!\n")
      written.toList mustEqual List(MemcacheResponse.ServerError("i suck!"))

      written.clear
      quickDecode(decoder, "ERROR\r\n")
      written.toList mustEqual List(MemcacheResponse.Error)

      written.clear
      quickDecode(decoder, "NOT_FOUND\r\n")
      written.toList mustEqual List(MemcacheResponse.NotFound)
    }

    "decode values" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "VALUE hippos 124 5\r\nhello\r\n")
      written.toList mustEqual List(MemcacheResponse.Value("hippos", 124, "", "hello".getBytes))

      written.clear
      quickDecode(decoder, "END\r\n")
      written.toList mustEqual List(MemcacheResponse.EndOfResults)
    }

    "decode incr/decr result" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "413\r\n")
      written.toList mustEqual List(MemcacheResponse.NewValue(413))
    }

    "decode values in chunks" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "VALUE hippos 12")
      written.toList mustEqual Nil
      quickDecode(decoder, "4 5\r\nhello\r\nVALUE hat 3 6 105\r\ncommie\r\nEND\r\n")
      written.toList mustEqual List(MemcacheResponse.Value("hippos", 124, "", "hello".getBytes),
                             MemcacheResponse.Value("hat", 3, "105", "commie".getBytes),
                             MemcacheResponse.EndOfResults)
    }

    "decode various simple responses" in {
      val decoder = new Decoder(MemcacheClientDecoder.response)
      quickDecode(decoder, "STORED\r\n")
      written.toList mustEqual List(MemcacheResponse.Stored)

      written.clear
      quickDecode(decoder, "NOT_STORED\r\n")
      written.toList mustEqual List(MemcacheResponse.NotStored)

      written.clear
      quickDecode(decoder, "EXISTS\r\n")
      written.toList mustEqual List(MemcacheResponse.Exists)

      written.clear
      quickDecode(decoder, "DELETED\r\n")
      written.toList mustEqual List(MemcacheResponse.Deleted)
    }
  }
}
