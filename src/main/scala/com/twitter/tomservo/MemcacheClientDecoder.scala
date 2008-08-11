package com.twitter.tomservo

import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.codec.{ProtocolCodecFilter, ProtocolEncoder, ProtocolEncoderOutput}
import com.twitter.tomservo.Steps._


abstract sealed class ServerResponse

// 3 types of error that can be returned by the server
case object Error extends ServerResponse
case class ClientError(reason: String) extends ServerResponse
case class ServerError(reason: String) extends ServerResponse

// when fetching stats or values:
case class Value(key: String, flags: Int, casKey: String, data: Array[Byte]) extends ServerResponse {
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
case class StatItem(key: String, value: String) extends ServerResponse
case object EndOfResults extends ServerResponse

// set/add/replace/append/prepend/cas/delete/incr/decr:
case object NotFound extends ServerResponse

// set/add/replace/append/prepend/cas:
case object Stored extends ServerResponse
case object NotStored extends ServerResponse
case object Exists extends ServerResponse

// delete:
case object Deleted extends ServerResponse

// incr/decr:
case class NewValue(data: Long) extends ServerResponse


object MemcacheClientDecoder {
  val response = readLine { line =>
    val parts = line.split(" ", 2)
    parts(0) match {
      case "ERROR" =>
        state.out.write(Error)
        End

      case "CLIENT_ERROR" =>
        state.out.write(ClientError(if (parts.length == 2) parts(1) else "(unknown)"))
        End

      case "SERVER_ERROR" =>
        state.out.write(ServerError(if (parts.length == 2) parts(1) else "(unknown)"))
        End

      case "NOT_FOUND" =>
        state.out.write(NotFound)
        End

      case "STORED" =>
        state.out.write(Stored)
        End

      case "NOT_STORED" =>
        state.out.write(NotStored)
        End

      case "EXISTS" =>
        state.out.write(Exists)
        End

      case "DELETED" =>
        state.out.write(Deleted)
        End

      case "END" =>
        state.out.write(EndOfResults)
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
            state.out.write(Value(key, flags, casKey, bytes))
            readByteBuffer(2) { lf =>
              if (new String(lf, "UTF-8") != "\r\n") {
                throw new ProtocolError("Corrupted VALUE terminator")
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
          state.out.write(NewValue(value.toLong))
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
        case b: Array[String] => b
      }
      out.write(data)
    }

    def dispose(session: IoSession): Unit = {
      // nothing.
    }
  }
  val filter = new ProtocolCodecFilter(encoder, decoder)
}
