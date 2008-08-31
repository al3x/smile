package net.lag.smile

import net.lag.naggati.IoHandlerActorAdapter
import org.apache.mina.core.session.{IdleStatus, IoSession}
import org.apache.mina.filter.codec.{ProtocolCodecFilter, ProtocolEncoder, ProtocolEncoderOutput}
import org.apache.mina.transport.socket.nio.{NioProcessor, NioSocketConnector}
import java.util.concurrent.Executors


/**
 * Pool of memcache server connections, and their shared config.
 */
class ServerPool {
  var connections: Array[MemcacheConnection] = Array()

  private var DEFAULT_CONNECT_TIMEOUT = 250
  var retryDelay = 30000
  var readTimeout = 2000

  // note: this will create one thread per ServerPool
  var connector = SocketConnectorHack.get(ServerPool.threadPool)
  connector.setConnectTimeoutMillis(DEFAULT_CONNECT_TIMEOUT)
  connector.getSessionConfig.setTcpNoDelay(true)
  connector.getSessionConfig.setUseReadOperation(true)

  connector.getFilterChain.addLast("codec", MemcacheClientDecoder.filter)
  connector.setHandler(new IoHandlerActorAdapter((session: IoSession) => null))

//  acceptor.getFilterChain().addLast( "logger", new LoggingFilter() );

}

object ServerPool {
  var threadPool = Executors.newCachedThreadPool
}
