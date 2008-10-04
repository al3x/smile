/*
 * Copyright (c) 2008, Robey Pointer <robeypointer@gmail.com>
 * ISC licensed. Please see the included LICENSE file for more information.
 */

package net.lag.smile

import scala.collection.mutable


/**
 * Codec for converting objects of type T to/from binary data for storage in a memcache
 * cluster.
 */
trait MemcacheCodec[T] {
  def encode(value: T): Array[Byte]
  def decode(data: Array[Byte]): T
}


/**
 * Some standard codecs.
 */
object MemcacheCodec {
  /**
   * The standard memcache codec, which stores strings in UTF-8.
   */
  val UTF8 = new MemcacheCodec[String] {
    def encode(value: String) = value.getBytes("utf-8")
    def decode(data: Array[Byte]) = new String(data, "utf-8")
  }
}
