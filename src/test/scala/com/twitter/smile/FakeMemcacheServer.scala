package com.twitter.smile

import java.io.IOException
import java.net.ServerSocket
import java.util.concurrent.{CountDownLatch, TimeUnit}
import scala.collection.mutable


abstract case class Task
case class Receive(count: Int) extends Task
case class Send(data: Array[Byte]) extends Task
case class Sleep(ms: Int) extends Task


class FakeMemcacheServer(tasks: List[Task]) extends Runnable {
  val socket = new ServerSocket(0, 100)
  val port = socket.getLocalPort

  val gotConnection = new CountDownLatch(1)
  val thread = new Thread(this)
  thread.setDaemon(true)

  val dataRead = new mutable.ListBuffer[Array[Byte]]


  override def run() = {
    while (true) {
      val client = socket.accept
      val inStream = client.getInputStream
      val outStream = client.getOutputStream
      gotConnection.countDown

      for (t <- tasks) {
        t match {
          case Receive(count) =>
            var sofar = 0
            val buffer = new Array[Byte](count)
            while (sofar < count) {
              val n = inStream.read(buffer, sofar, count - sofar)
              if (n < 0) {
                throw new IOException("eof")
              }
              sofar += n
            }
            dataRead += buffer
          case Send(data) =>
            outStream.write(data)
          case Sleep(n) =>
            try {
              Thread.sleep(n)
            } catch {
              case x: InterruptedException =>
            }
        }
      }
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
