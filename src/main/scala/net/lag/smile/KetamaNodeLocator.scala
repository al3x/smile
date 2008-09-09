package net.lag.smile

import net.lag.extensions._
import scala.collection.jcl
import java.nio.{ByteBuffer, ByteOrder}
import java.security.MessageDigest


class KetamaNodeLocator(hasher: KeyHasher) extends NodeLocator {
  private val NUM_REPS = 160

  private var pool: ServerPool = null
  private val continuum = new jcl.TreeMap[Long, MemcacheConnection]


  def this() = this(KeyHasher.KETAMA)

  def setPool(pool: ServerPool) = {
    this.pool = pool
    createContinuum
  }

  def findNode(key: Array[Byte]): MemcacheConnection = {
    val hash = hasher.hashKey(key)
    val tail = continuum.underlying.tailMap(hash)
    continuum(if (tail.isEmpty) continuum.firstKey else tail.firstKey)
  }

  private def computeHash(key: String, alignment: Int) = {
    val hasher = MessageDigest.getInstance("MD5")
    hasher.update(key.getBytes("utf-8"))
    val buffer = ByteBuffer.wrap(hasher.digest)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(alignment << 2)
    buffer.getInt.toLong & 0xffffffffL
  }

  private def createContinuum() = {
    // we use (NUM_REPS * #servers) total points, but allocate them based on server weights.
    val totalWeight = pool.servers.foldLeft(0.0) { _ + _.weight }
    continuum.clear

    for (node <- pool.servers) {
      val percent = node.weight.toDouble / totalWeight
      // the tiny fudge fraction is added to counteract float errors.
      val itemWeight = (percent * pool.servers.size * (NUM_REPS / 4) + 0.0000000001).toInt
      for (k <- 0 until itemWeight) {
        val key = if (node.port == 11211) {
          node.hostname + "-" + k
        } else {
          node.hostname + ":" + node.port + "-" + k
        }
        for (i <- 0 until 4) {
          continuum += (computeHash(key, i) -> node)
        }
      }
    }

    assert(continuum.size <= NUM_REPS * pool.servers.size)
    assert(continuum.size >= NUM_REPS * (pool.servers.size - 1))
  }

  override def toString() = {
    "<KetamaNodeLocator hash=%s nodes=%d servers=%d>".format(hasher, continuum.size,
      pool.servers.size)
  }
}
