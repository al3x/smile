package net.lag.smile

import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.codec.{ProtocolCodecFilter, ProtocolEncoder, ProtocolEncoderOutput}
import net.lag.extensions._
import net.lag.naggati.{Decoder, End, ProtocolError}
import net.lag.naggati.Steps._


abstract sealed class MemcachedResponse

object MemcachedResponse {
  // 3 types of error that can be returned by the server
  case object Error extends MemcachedResponse
  case class ClientError(reason: String) extends MemcachedResponse
  case class ServerError(reason: String) extends MemcachedResponse

  // when fetching stats or values:
  case class Value(key: String, flags: Int, casKey: String, data: Array[Byte]) extends MemcachedResponse {
    // FIXME: why doesn't scala do array equals correctly?
    override def equals(obj: Any) = {
      obj match {
        case Value(okey, oflags, ocasKey, odata) =>
          (key == okey) && (flags == oflags) && (casKey == ocasKey) &&
            (java.util.Arrays.equals(data, odata))
        case _ => false
      }
    }
  }
  case class StatItem(key: String, value: String) extends MemcachedResponse
  case object EndOfResults extends MemcachedResponse

  // set/add/replace/append/prepend/cas/delete/incr/decr:
  case object NotFound extends MemcachedResponse

  // set/add/replace/append/prepend/cas:
  case object Stored extends MemcachedResponse
  case object NotStored extends MemcachedResponse
  case object Exists extends MemcachedResponse

  // delete:
  case object Deleted extends MemcachedResponse

  // incr/decr:
  case class NewValue(data: Long) extends MemcachedResponse
}


object MemcacheClientDecoder {
  val response = readLine { line =>
    val parts = line.split(" ", 2)
    parts(0) match {
      case "ERROR" =>
        state.out.write(MemcachedResponse.Error)
        End

      case "CLIENT_ERROR" =>
        state.out.write(MemcachedResponse.ClientError(if (parts.length == 2) parts(1) else "(unknown)"))
        End

      case "SERVER_ERROR" =>
        state.out.write(MemcachedResponse.ServerError(if (parts.length == 2) parts(1) else "(unknown)"))
        End

      case "NOT_FOUND" =>
        state.out.write(MemcachedResponse.NotFound)
        End

      case "STORED" =>
        state.out.write(MemcachedResponse.Stored)
        End

      case "NOT_STORED" =>
        state.out.write(MemcachedResponse.NotStored)
        End

      case "EXISTS" =>
        state.out.write(MemcachedResponse.Exists)
        End

      case "DELETED" =>
        state.out.write(MemcachedResponse.Deleted)
        End

      case "END" =>
        state.out.write(MemcachedResponse.EndOfResults)
        End

      case "VALUE" =>
        val subParts = parts(1).split(" ")
        if ((subParts.length < 3) || (subParts.length > 4)) {
          throw new ProtocolError("Corrupted VALUE line")
        }
        try {
          val key = subParts(0)
          val flags = subParts(1).toInt
          val bytes = subParts(2).toInt
          val casKey = if (subParts.length == 4) subParts(3) else ""
          readByteBuffer(bytes) { bytes =>
            state.out.write(MemcachedResponse.Value(key, flags, casKey, bytes))
            readByteBuffer(2) { lf =>
              if (new String(lf, "UTF-8") != "\r\n") {
                throw new ProtocolError("Corrupted VALUE terminator: " + lf.hexlify)
              }
              End
            }
          }
        } catch {
          case e: NumberFormatException =>
            throw new ProtocolError("Corrupted VALUE line")
        }

      // incr/decr response:
      case value =>
        try {
          state.out.write(MemcachedResponse.NewValue(value.toLong))
          End
        } catch {
          case e: NumberFormatException =>
            throw new ProtocolError("Unexpected response: " + value)
        }
    }
  }


  val decoder = new Decoder(response)
  val encoder = new ProtocolEncoder {
    def encode(session: IoSession, message: AnyRef, out: ProtocolEncoderOutput) = {
      val data = message match {
        case s: String => s.getBytes
        case b: Array[Byte] => b
      }
      out.write(IoBuffer.wrap(data))
    }

    def dispose(session: IoSession): Unit = {
      // nothing.
    }
  }
  val filter = new ProtocolCodecFilter(encoder, decoder)
}
