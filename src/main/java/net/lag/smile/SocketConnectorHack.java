/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile;

import java.util.concurrent.*;
import org.apache.mina.transport.socket.*;
import org.apache.mina.transport.socket.nio.*;


/**
 * Hack to work around a scala 2.7.1 compiler bug where NioSocketConnector's type parameter
 * makes it explode. This is truly an awful hack, and I apologize profusely. :(
 */
class SocketConnectorHack {
  static SocketConnector get(Executor e) {
    return new NioSocketConnector(new NioProcessor(e));
  }
}
