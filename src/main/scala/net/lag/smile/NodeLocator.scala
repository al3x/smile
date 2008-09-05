package net.lag.smile


/**
 * Used by MemcacheClient to map a memcache key to the server node that should contain it.
 */
trait NodeLocator {
  /**
   * Return a reference to the server pool. Included only for API completeness; not used
   * by this library.
   */
  def serverPool: ServerPool

  /**
   * Set the server pool. This will be called when MemcacheClient is initialized, and also
   * whenever the server list is changed.
   */
  def serverPool_=(pool: ServerPool): Unit

  /**
   * Return the server node that should contain this key.
   */
  def findNode(key: Array[Byte]): MemcacheConnection
}
