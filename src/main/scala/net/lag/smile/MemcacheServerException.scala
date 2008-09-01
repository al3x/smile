package net.lag.smile

import java.io.IOException


/**
 * All exceptions thrown from this library will be subclasses of this exception.
 */
class MemcacheServerException(reason: String) extends IOException(reason)
class MemcacheServerTimeout extends MemcacheServerException("timeout")
class MemcacheServerOffline extends MemcacheServerException("server is unreachable")

class NotStoredException extends MemcacheServerException("not stored")
