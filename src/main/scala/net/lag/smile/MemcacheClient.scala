/*
 * Copyright (c) 2008, Robey Pointer <robey@lag.net>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import net.lag.configgy.AttributeMap


/**
 * 
 */
class MemcacheClient(locator: NodeLocator) {
  private var pool: ServerPool = null

  def setPool(pool: ServerPool) = {
    this.pool = pool
    locator.setPool(pool)
  }

  def get(key: String): Option[Array[Byte]] = {
    locator.findNode(key.getBytes("utf-8")).get(key) match {
      case None => None
      case Some(v) => Some(v.data)
    }
  }

  def getString(key: String): Option[String] = {
    get(key) match {
      case None => None
      case Some(data) => Some(new String(data, "utf-8"))
    }
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
    val locator = attr.get("distribution", "ketama") match {
      case "default" =>
        new RoundRobinNodeLocator(KeyHasher.byName(attr.get("hash", "crc32-itu")))
      case "round-robin" =>
        new RoundRobinNodeLocator(KeyHasher.byName(attr.get("hash", "crc32-itu")))
      case "ketama" =>
        new KetamaNodeLocator(KeyHasher.byName(attr.get("hash", "ketama")))
      case x =>
        throw new IllegalArgumentException("unknown distribution: " + x)
    }
    val client = new MemcacheClient(locator)
    client.setPool(pool)
    client
  }
}
