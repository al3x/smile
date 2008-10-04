/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.configgy.AttributeMap
import net.lag.extensions._


/**
 *
 */
class MemcacheClient[T](locator: NodeLocator, codec: MemcacheCodec[T]) {
  private var pool: ServerPool = null
  var namespace: Option[String] = None


  def setPool(pool: ServerPool) = {
    this.pool = pool
    locator.setPool(pool)
  }

  /**
   * Shutdown this memcache client instance. This is necessary in order to stop the connection
   * actors and terminate mina threads.
   */
  def shutdown() = {
    pool.shutdown
    pool = null
  }

  override def toString() = {
    "<MemcacheClient locator=%s servers=%s>".format(locator, pool)
  }

  /**
   * Return the list of memcache server connections.
   */
  def servers = pool.servers


  @throws(classOf[MemcacheServerException])
  def getData(key: String): Option[Array[Byte]] = {
    val (node, rkey) = nodeForKey(key)
    node.get(rkey) match {
      case None => None
      case Some(v) => Some(v.data)
    }
  }

  @throws(classOf[MemcacheServerException])
  def get(key: String): Option[T] = {
    getData(key) match {
      case None => None
      case Some(data) => Some(codec.decode(data))
    }
  }

  @throws(classOf[MemcacheServerException])
  def get[A](key: String, codec: MemcacheCodec[A]): Option[A] = {
    getData(key) match {
      case None => None
      case Some(data) => Some(codec.decode(data))
    }
  }

  @throws(classOf[MemcacheServerException])
  def setData(key: String, value: Array[Byte], flags: Int, expiry: Int): Unit = {
    val (node, rkey) = nodeForKey(key)
    node.set(rkey, value, flags, expiry)
  }

  @throws(classOf[MemcacheServerException])
  def setData(key: String, value: Array[Byte]): Unit = setData(key, value, 0, 0)

  @throws(classOf[MemcacheServerException])
  def set(key: String, value: T, flags: Int, expiry: Int): Unit = {
    setData(key, codec.encode(value), flags, expiry)
  }

  @throws(classOf[MemcacheServerException])
  def set(key: String, value: T): Unit = set(key, value, 0, 0)

  @throws(classOf[MemcacheServerException])
  def set[A](key: String, value: A, flags: Int, expiry: Int, codec: MemcacheCodec[A]): Unit = {
    setData(key, codec.encode(value), flags, expiry)
  }

  @throws(classOf[MemcacheServerException])
  def set[A](key: String, value: A, codec: MemcacheCodec[A]): Unit = set(key, value, 0, 0, codec)


  private def nodeForKey(key: String): (MemcacheConnection, String) = {
    val realKey = namespace match {
      case None => key
      case Some(prefix) => prefix + ":" + key
    }
    (locator.findNode(realKey.getBytes("utf-8")), realKey)
  }
}


object MemcacheClient {
  def create(servers: Array[MemcacheConnection], locator: NodeLocator): MemcacheClient[String] = {
    create(servers, locator, MemcacheCodec.UTF8)
  }

  def create[T](servers: Array[MemcacheConnection], locator: NodeLocator,
                codec: MemcacheCodec[T]): MemcacheClient[T] = {
    val client = new MemcacheClient(locator, codec)
    val pool = new ServerPool
    pool.servers = servers
    client.setPool(pool)
    client
  }

  def create(attr: AttributeMap) = {
    val pool = ServerPool.fromConfig(attr)
    val locator = NodeLocator.byName(attr.get("distribution", "default")) match {
      case (hashName, factory) =>
        factory(KeyHasher.byName(attr.get("hash", hashName)))
    }
    val client = new MemcacheClient(locator, MemcacheCodec.UTF8)
    client.setPool(pool)
    client.namespace = attr.get("namespace")
    client
  }

  def create(servers: Array[String], distribution: String, hash: String) = {
    val pool = new ServerPool
    val connections = for (s <- servers) yield ServerPool.makeConnection(s, pool)
    pool.servers = connections

    val locator = NodeLocator.byName(distribution) match {
      case (hashName, factory) => factory(KeyHasher.byName(hash))
    }
    val client = new MemcacheClient(locator, MemcacheCodec.UTF8)
    client.setPool(pool)
    client
  }
}
