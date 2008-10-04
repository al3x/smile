/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.configgy.AttributeMap
import net.lag.extensions._
import net.lag.naggati.IoHandlerActorAdapter
import org.apache.mina.core.session.{IdleStatus, IoSession}
import org.apache.mina.filter.codec.{ProtocolCodecFilter, ProtocolEncoder, ProtocolEncoderOutput}
import org.apache.mina.transport.socket.nio.{NioProcessor, NioSocketConnector}
import java.util.concurrent.Executors


/**
 * Pool of memcache server connections, and their shared config.
 *
 * @param trace if true, log all incoming/outgoing data as hexdumps at TRACE level
 */
class ServerPool(trace: Boolean) {

  def this() = this(false)

  var threadPool = Executors.newCachedThreadPool
  var servers: Array[MemcacheConnection] = Array()

  private var DEFAULT_CONNECT_TIMEOUT = 250
  var retryDelay = 30000
  var readTimeout = 2000

  // note: this will create one thread per ServerPool
  var connector = SocketConnectorHack.get(threadPool)
  connector.setConnectTimeoutMillis(DEFAULT_CONNECT_TIMEOUT)
  connector.getSessionConfig.setTcpNoDelay(true)
  connector.getSessionConfig.setUseReadOperation(true)

  // don't always install this.
  if (trace) {
    connector.getFilterChain.addLast("logger", new LoggingFilter)
  }

  connector.getFilterChain.addLast("codec", MemcacheClientDecoder.filter)
  connector.setHandler(new IoHandlerActorAdapter((session: IoSession) => null))

  def shutdown() = {
    for (conn <- servers) {
      conn.shutdown
    }
    servers = Array()
    connector.dispose
    threadPool.shutdown
  }

  override def toString() = servers.mkString(", ")
}


object ServerPool {

  val DEFAULT_PORT = 11211
  val DEFAULT_WEIGHT = 1

  /**
   * Make a new MemcacheConnection out of a description string. A description string is:
   * <hostname> [ ":" <port> [ " " <weight> ]]
   * The default port is 11211 and the default weight is 1.
   */
  def makeConnection(desc: String, pool: ServerPool) = {
    val connection = desc.split("[: ]").toList match {
      case hostname :: Nil =>
        new MemcacheConnection(hostname, DEFAULT_PORT, DEFAULT_WEIGHT)
      case hostname :: port :: Nil =>
        new MemcacheConnection(hostname, port.toInt, DEFAULT_WEIGHT)
      case hostname :: port :: weight :: Nil =>
        new MemcacheConnection(hostname, port.toInt, weight.toInt)
      case _ =>
        throw new IllegalArgumentException
    }
    connection.pool = pool
    connection
  }

  /**
   * Make a new ServerPool out of a config block.
   */
  def fromConfig(attr: AttributeMap) = {
    val pool = new ServerPool(attr.getBool("trace", false))
    for (serverList <- attr.getStringList("servers")) {
      pool.servers = for (desc <- serverList) yield makeConnection(desc, pool)
    }
    for (n <- attr.getInt("retry_delay")) {
      pool.retryDelay = n * 1000
    }
    for (n <- attr.getInt("read_timeout")) {
      pool.readTimeout = n * 1000
    }
    pool
  }
}
