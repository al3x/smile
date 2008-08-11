package com.twitter.smile

import java.net.InetSocketAddress
import scala.actors.Actor
import scala.actors.Actor._
import net.lag.extensions._
import net.lag.logging.Logger
import org.apache.mina.core.session.IoSession
import org.apache.mina.transport.socket.nio.NioSocketConnector


class MemcacheServerOffline extends Exception


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

  val serverActor = actor {
    loop {
      react {
        case Stop => disconnect; self.exit

        // non-interesting (unsolicited) mina messages:
        case MinaMessage.SessionOpened =>
        case MinaMessage.MessageReceived(message) =>
          log.error("unsolicited response from server %s: %s", this, message)
        case MinaMessage.MessageSent(message) =>
        case MinaMessage.ExceptionCaught(cause) =>
          log.error(cause, "exception in actor for %s", this)
          disconnect
        case MinaMessage.SessionIdle(status) =>
        case MinaMessage.SessionClosed =>
      }
    }
  }
}
