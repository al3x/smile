/*
 * Copyright (c) 2008, Robey Pointer <robey@lag.net>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.configgy.AttributeMap
import net.lag.extensions._


/**
 *
 */
class MemcacheClient(locator: NodeLocator) {
  private var pool: ServerPool = null
  var namespace: Option[String] = None


  def setPool(pool: ServerPool) = {
    this.pool = pool
    locator.setPool(pool)
  }

  def shutdown() = {
    pool.shutdown
    pool = null
  }

  override def toString() = {
    "<MemcacheClient locator=%s servers=%s>".format(locator, pool)
  }



  @throws(classOf[MemcacheServerException])
  def get(key: String): Option[Array[Byte]] = {
    val (node, rkey) = nodeForKey(key)
    node.get(rkey) match {
      case None => None
      case Some(v) => Some(v.data)
    }
  }

  @throws(classOf[MemcacheServerException])
  def getString(key: String): Option[String] = {
    get(key) match {
      case None => None
      case Some(data) => Some(new String(data, "utf-8"))
    }
  }

  @throws(classOf[MemcacheServerException])
  def set(key: String, value: Array[Byte], flags: Int, expiry: Int): Unit = {
    val (node, rkey) = nodeForKey(key)
    node.set(rkey, value, flags, expiry)
  }

  @throws(classOf[MemcacheServerException])
  def set(key: String, value: Array[Byte]): Unit = set(key, value, 0, 0)

  @throws(classOf[MemcacheServerException])
  def setString(key: String, value: String, flags: Int, expiry: Int): Unit = {
    set(key, value.getBytes("utf-8"), flags, expiry)
  }

  @throws(classOf[MemcacheServerException])
  def setString(key: String, value: String): Unit = setString(key, value, 0, 0)


  private def nodeForKey(key: String): (MemcacheConnection, String) = {
    val realKey = namespace match {
      case None => key
      case Some(prefix) => prefix + ":" + key
    }
    (locator.findNode(realKey.getBytes("utf-8")), realKey)
  }
}


object MemcacheClient {
  def create(servers: Array[MemcacheConnection], locator: NodeLocator) = {
    val client = new MemcacheClient(locator)
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
    val client = new MemcacheClient(locator)
    client.setPool(pool)
    client.namespace = attr.get("namespace")

    client
  }
}
