package com.twitter.smile

import java.io.IOException
import java.net.InetSocketAddress
import scala.actors.{Actor, OutputChannel}
import scala.actors.Actor._
import scala.collection.mutable
import com.twitter.tomservo.MemcachedResponse
import net.lag.extensions._
import net.lag.logging.Logger
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioSocketConnector


/**
 * All exceptions thrown from this library will be subclasses of this exception.
 */
class MemcacheServerException(reason: String) extends IOException(reason)

class MemcacheServerOffline extends MemcacheServerException("server is unreachable")


class MemcacheServer(hostname: String, port: Int, weight: Int) {
  var pool: ServerPool = null

  val log = Logger.get

  @volatile protected var session: Option[IoSession] = None

  // if the last connection attempt failed, this contains the time we should try next:
  @volatile protected var delaying: Option[Long] = None


//  private var

  override def toString() = {
    val status = session match {
      case None =>
        delaying match {
          case None => "not connected"
          case Some(d) =>
            if (d > System.currentTimeMillis) {
              "waiting %d sec to retry".format((System.currentTimeMillis - d + 999) / 1000)
            } else {
              "ready to retry"
            }
        }
      case Some(s) => "connected"
    }
    "<MemcacheServer %s:%d weight=%d (%s)>".format(hostname, port, weight, status)
  }

  def get(key: String): Option[Array[Byte]] = {
    serverActor !? Get("get", key) match {
      case ConnectionFailed => throw new MemcacheServerOffline
      case GetResponse(values) =>
        values match {
          case Nil => None
          case v :: Nil => Some(v.data)
          // sanity check:
          case _ => throw new MemcacheServerException("too many results for single get: " + values.length)
        }
    }
  }

  // for convenience
  def getString(key: String): String = {
    get(key) match {
      case None => ""
      case Some(data) => new String(data)
    }
  }


  private def connect(): Unit = {
    if (delaying.isDefined && (System.currentTimeMillis < delaying.get)) {
      // not yet.
      return
    }
    delaying = None

    val future = pool.connector.connect(new InetSocketAddress(hostname, port))
    future.await
    if (!future.isConnected) {
      val exception = future.getException
      if (exception != null) {
        log.warning("Failed to connect to memcache server %s:%d: %s", hostname, port, exception)
      } else {
        log.warning("Failed to connect to memcache server %s:%d: no exception", hostname, port)
      }
      delaying = Some(System.currentTimeMillis + pool.retryDelay)
      session = None
    } else {
      session = Some(future.getSession)
      IoHandlerActorAdapter.setActorFor(session.get, serverActor)
    }
  }

  private[smile] def ensureConnected: Boolean = {
    session match {
      case None =>
        connect
        session.isDefined
      case Some(s) => true
    }
  }

  private def disconnect(): Unit = {
    for (s <- session) {
      s.close
    }
    session = None
  }

//  private def set(command: String, )


  private case object Stop
  private case class Get(query: String, key: String)

  private case object ConnectionFailed
  private case class GetResponse(values: List[MemcachedResponse.Value])

  val serverActor = actor {
    loop {
      react {
        case Stop => disconnect; self.exit

        case Get(query, key) =>
          if (!ensureConnected) {
            reply(ConnectionFailed)
          } else {
            for (s <- session) {
              s.write(query + " " + key + "\r\n")
              // mina currently only supports *seconds* here :(
              s.getConfig.setReaderIdleTime(pool.readTimeout / 1000)
              waitForGetResponse(sender, new mutable.ListBuffer[MemcachedResponse.Value])
            }
          }

        // non-interesting (unsolicited) mina messages:
        case MinaMessage.SessionOpened =>
        case MinaMessage.MessageReceived(message) =>
          log.error("unsolicited response from server %s: %s", this, message)
        case MinaMessage.MessageSent(message) =>
        case MinaMessage.ExceptionCaught(cause) =>
          log.error(cause, "exception in actor for %s", this)
          disconnect
        case MinaMessage.SessionIdle(status) =>
          for (s <- session) {
            Console.println("got idle")
            s.getConfig.setReaderIdleTime(0)
          }
        case MinaMessage.SessionClosed =>
      }
    }
  }

  private def waitForGetResponse(sender: OutputChannel[Any], responses: mutable.ListBuffer[MemcachedResponse.Value]): Unit = {
    react {
      case MinaMessage.MessageReceived(message) =>
        message match {
          case v: MemcachedResponse.Value =>
            responses += v
            waitForGetResponse(sender, responses)
          case MemcachedResponse.EndOfResults =>
            sender ! GetResponse(responses.toList)
          case x =>
            Console.println("got: " + x)
        }
      case MinaMessage.ExceptionCaught(cause) =>
        log.error(cause, "exception in actor for %s", this)
        disconnect
      case MinaMessage.SessionIdle(status) =>
      case MinaMessage.SessionClosed =>
    }
  }
}
