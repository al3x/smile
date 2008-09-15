/*
 * Copyright (c) 2008, Robey Pointer <robey@lag.net>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import java.net.InetSocketAddress
import scala.actors.{Actor, OutputChannel}
import scala.actors.Actor._
import scala.collection.mutable
import net.lag.extensions._
import net.lag.logging.Logger
import net.lag.naggati.{IoHandlerActorAdapter, MinaMessage}
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioSocketConnector


/**
 * Connection to and configuration for a memcache server, and an actor for handling requests.
 */
class MemcacheConnection(val hostname: String, val port: Int, val weight: Int) {
  var pool: ServerPool = null

  private val log = Logger.get

  @volatile protected var session: Option[IoSession] = None

  // if the last connection attempt failed, this contains the time we should try next:
  @volatile protected var delaying: Option[Long] = None


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
    "<MemcacheConnection %s:%d weight=%d (%s)>".format(hostname, port, weight, status)
  }

  /**
   * Do we have an open TCP connection to this memcache server (or think we do)?
   */
  def connected = session != None

  @throws(classOf[MemcacheServerException])
  def get(keys: Array[String]): Map[String, MemcacheResponse.Value] = {
    serverActor ! Get("get", keys.mkString(" "))
    receive {
      case Timeout => throw new MemcacheServerTimeout
      case ConnectionFailed => throw new MemcacheServerOffline
      case Error(description) => throw new MemcacheServerException(description)
      case GetResponse(values) => Map.empty ++ (for (v <- values) yield (v.key, v))
    }
  }

  @throws(classOf[MemcacheServerException])
  def get(key: String): Option[MemcacheResponse.Value] = {
    serverActor ! Get("get", key)
    receive {
      case Timeout => throw new MemcacheServerTimeout
      case ConnectionFailed => throw new MemcacheServerOffline
      case Error(description) => throw new MemcacheServerException(description)
      case GetResponse(values) =>
        values match {
          case Nil => None
          case v :: Nil => Some(v)
          // sanity check:
          case _ => throw new MemcacheServerException("too many results for single get: " +
            values.length)
        }
      case x => throw new RuntimeException("ACCCCCK " + x)
    }
  }

  @throws(classOf[MemcacheServerException])
  def set(key: String, value: Array[Byte], flags: Int, expiry: Int): Unit = {
    serverActor ! Store("set", key, flags, expiry, value)
    receive {
      case Timeout => throw new MemcacheServerTimeout
      case ConnectionFailed => throw new MemcacheServerOffline
      case Error(description) => throw new MemcacheServerException(description)
      case MemcacheResponse.Stored =>
      case MemcacheResponse.NotStored => throw new NotStoredException
      case x => throw new MemcacheServerException("unexpected: " + x)
    }
  }

  /**
   * Stop the actor associated with this connection, and disconnect from the server if
   * connected. The MemcacheConnection object will be useless after this call.
   */
  def shutdown() = {
    serverActor ! Stop
  }


  //  ----------  implementation

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


  //  ----------  actor

  private case object Stop
  private case class Get(query: String, key: String)
  private case class Store(query: String, key: String, flags: Int, expiry: Int, data: Array[Byte])

  private case object ConnectionFailed
  private case class Error(description: String)
  private case object Timeout
  private case class GetResponse(values: List[MemcacheResponse.Value])

  // values collected from a get/gets
  private val values = new mutable.ListBuffer[MemcacheResponse.Value]


  val serverActor = actor {
    loop {
      values.clear

      react {
        case Stop =>
          disconnect
          self.exit

        case Get(query, key) =>
          if (!ensureConnected) {
            reply(ConnectionFailed)
          } else {
            for (s <- session) {
              s.write(query + " " + key + "\r\n")
              // mina currently only supports *seconds* here :(
              s.getConfig.setReaderIdleTime(pool.readTimeout / 1000)
              waitForGetResponse(sender)
            }
          }

        case Store(query, key, flags, expiry, data) =>
          if (!ensureConnected) {
            reply(ConnectionFailed)
          } else {
            for (s <- session) {
              s.write(query + " " + key + " " + flags + " " + expiry + " " + data.length + "\r\n")
              s.write(data)
              s.write("\r\n")
              // mina currently only supports *seconds* here :(
              s.getConfig.setReaderIdleTime(pool.readTimeout / 1000)
              waitForStoreResponse(sender)
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
          // probably leftover from a previous timeout.
          for (s <- session) {
            s.getConfig.setReaderIdleTime(0)
          }
        case MinaMessage.SessionClosed =>
      }
    }
  }

  /**
   * Handle mina messages on this connection while waiting for a response from the memcache
   * server. Timeouts and disconnects are handled too. Any response is passed into the
   * handler block.
   */
  private def waitForResponse(sender: OutputChannel[Any])(handler: AnyRef => Unit) = {
    react {
      case MinaMessage.MessageReceived(message) =>
        handler(message)
      case MinaMessage.ExceptionCaught(cause) =>
        log.error(cause, "exception in actor for %s", this)
        disconnect
      case MinaMessage.SessionIdle(status) =>
        log.error("timeout for %s", this)
        disconnect
        sender ! Timeout
      case MinaMessage.SessionClosed =>
        log.error("disconnected from server for %s", this)
        disconnect
        sender ! ConnectionFailed
    }
  }

  private def waitForGetResponse(sender: OutputChannel[Any]): Unit = {
    waitForResponse(sender) { message =>
      message match {
        case v: MemcacheResponse.Value =>
          values += v
          waitForGetResponse(sender)
        case MemcacheResponse.EndOfResults =>
          sender ! GetResponse(values.toList)
        case MemcacheResponse.Error => sender ! Error("error")
        case MemcacheResponse.ClientError(x) => sender ! Error("client error: " + x)
        case MemcacheResponse.ServerError(x) => sender ! Error("server error: " + x)
        case x => sender ! Error("unexpected: " + x)
      }
    }
  }

  private def waitForStoreResponse(sender: OutputChannel[Any]): Unit = {
    waitForResponse(sender) { message =>
      message match {
        case MemcacheResponse.Error => sender ! Error("error")
        case MemcacheResponse.ClientError(x) => sender ! Error("client error: " + x)
        case MemcacheResponse.ServerError(x) => sender ! Error("server error: " + x)
        case item => sender ! item
      }
    }
  }
}
