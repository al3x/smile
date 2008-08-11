package com.twitter.smile

import java.net.ServerSocket
import java.util.concurrent.{CountDownLatch, TimeUnit}


class FakeMemcacheServer extends Runnable {
  val socket = new ServerSocket(0, 100)
  val port = socket.getLocalPort

  val gotConnection = new CountDownLatch(1)
  val thread = new Thread(this)
  thread.setDaemon(true)


  override def run() = {
    while (true) {
      val client = socket.accept
      gotConnection.countDown
    }
  }
  
  def start = {
    thread.start
  }
  
  def stop = {
    thread.interrupt
  }
  
  def awaitConnection(msec: Int) = {
    gotConnection.await(msec, TimeUnit.MILLISECONDS)
  }
}
