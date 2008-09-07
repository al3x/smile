/*
 * Copyright (c) 2008, Robey Pointer <robey@lag.net>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import scala.collection.mutable


/**
 * Locate a node by taking the mod of the key's hash against the number of servers. This is
 * the default memcache node locator.
 */
class RoundRobinNodeLocator(hasher: KeyHasher) extends NodeLocator {

  def this() = this(KeyHasher.CRC32_ITU)

  var pool: ServerPool = null
  var continuum: Array[MemcacheConnection] = null

  def setPool(pool: ServerPool) = {
    this.pool = pool
    val stack = new mutable.ListBuffer[MemcacheConnection]
    for (s <- pool.servers) {
      for (i <- 1 to s.weight) {
        stack += s
      }
    }
    continuum = stack.toArray
  }

  /**
   * Return the server node that should contain this key.
   */
  def findNode(key: Array[Byte]): MemcacheConnection = {
    val index = (hasher.hashKey(key) % continuum.size).toInt
    continuum(index)
  }
}
