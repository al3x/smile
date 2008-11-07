/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.configgy.ConfigMap
import net.lag.extensions._
import scala.actors.Futures
import scala.collection.mutable


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


  /**
   * Get an item from the memcache cluster as an array of bytes.
   */
  @throws(classOf[MemcacheServerException])
  def getData(key: String): Option[Array[Byte]] = {
    val (node, rkey) = nodeForKey(key)
    node.get(rkey) match {
      case None => None
      case Some(v) => Some(v.data)
    }
  }

  /**
   * Get an item from the memcache cluster and decode it using the default
   * codec.
   */
  @throws(classOf[MemcacheServerException])
  def get(key: String): Option[T] = {
    getData(key) match {
      case None => None
      case Some(data) => Some(codec.decode(data))
    }
  }

  /**
   * Get an item from the memcache cluster and decode it using a specific
   * codec.
   */
  @throws(classOf[MemcacheServerException])
  def get[A](key: String, codec: MemcacheCodec[A]): Option[A] = {
    getData(key) match {
      case None => None
      case Some(data) => Some(codec.decode(data))
    }
  }

  /**
   * Get a list of items from the memcache cluster and return the ones that
   * exist in a map associating keys to arrays of bytes. If some of the keys
   * map to different memcache servers, the servers will be contacted
   * concurrently. A multi-get will be used on each server to get every item
   * from that server at once.
   */
  @throws(classOf[MemcacheServerException])
  def getData(keys: Array[String]): Map[String, Array[Byte]] = {
    val keyMap = new mutable.HashMap[String, String]
    val nodeKeys = new mutable.HashMap[MemcacheConnection, mutable.ListBuffer[String]]
    for (key <- keys) {
      val (node, rkey) = nodeForKey(key)
      keyMap(rkey) = key
      nodeKeys.getOrElseUpdate(node, new mutable.ListBuffer[String]) += rkey
    }
    val futures = for ((node, keyList) <- nodeKeys.elements) yield
      Futures.future { node.get(keyList.toArray) }
    Map.empty ++ (for (future <- futures; (key, value) <- future().elements) yield (keyMap(key), value.data))
  }

  /**
   * Get a list of items from the memcache cluster and return the ones that
   * exist in a map associating keys to items decoded using the default
   * codec. Parallel fetching is used, just as in <code>getData</code>.
   */
  @throws(classOf[MemcacheServerException])
  def get(keys: Array[String]): Map[String, T] = {
    Map.empty ++ (for ((key, data) <- getData(keys).elements) yield (key, codec.decode(data)))
  }

  /**
   * Get a list of items from the memcache cluster and return the ones that
   * exist in a map associating keys to items decoded using the codec given.
   * Parallel fetching is used, just as in <code>getData</code>.
   */
  @throws(classOf[MemcacheServerException])
  def get[A](keys: Array[String], codec: MemcacheCodec[A]): Map[String, A] = {
    Map.empty ++ (for ((key, data) <- getData(keys).elements) yield (key, codec.decode(data)))
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
  /**
   * Create a new MemcacheClient from a server list and node locator, using
   * the UTF-8 codec.
   */
  def create(servers: Array[MemcacheConnection], locator: NodeLocator): MemcacheClient[String] = {
    create(servers, locator, MemcacheCodec.UTF8)
  }

  /**
   * Create a new MemcacheClient from a server list, node locator, and codec.
   * The codec will be the default mechanism for translating memcache items
   * to/from scala objects.
   */
  def create[T](servers: Array[MemcacheConnection], locator: NodeLocator,
                codec: MemcacheCodec[T]): MemcacheClient[T] = {
    val client = new MemcacheClient(locator, codec)
    val pool = new ServerPool
    pool.servers = servers
    client.setPool(pool)
    client
  }

  /**
   * Create a new MemcacheClient from a configgy block. The memcache cluster
   * distribution and hash function are specified by strings
   * "<code>distribution</code>" and "<code>hash</code>". An optional namespace
   * may be specified with "<code>namespace</code>". The server list must be
   * in a string list called "<code>servers</code>".
   */
  def create(attr: ConfigMap) = {
    val pool = ServerPool.fromConfig(attr)
    val locator = NodeLocator.byName(attr("distribution", "default")) match {
      case (hashName, factory) =>
        factory(KeyHasher.byName(attr("hash", hashName)))
    }
    val client = new MemcacheClient(locator, MemcacheCodec.UTF8)
    client.setPool(pool)
    client.namespace = attr.getString("namespace")
    client
  }

  /**
   * Create a new MemcacheClient from a server list, specifying the cluster's
   * distribution and hash function by name.
   */
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
