package com.twitter.smile

import scala.actors.Actor
import org.apache.mina.core.service.IoHandler
import org.apache.mina.core.session.{IdleStatus, IoSession}
import net.lag.logging.Logger


// Actor messages for the Mina "events"
abstract sealed class MinaMessage
object MinaMessage {
  case object SessionOpened extends MinaMessage
  case class MessageReceived(message: AnyRef) extends MinaMessage
  case class MessageSent(message: AnyRef) extends MinaMessage
  case class ExceptionCaught(cause: Throwable) extends MinaMessage
  case class SessionIdle(status: IdleStatus) extends MinaMessage
  case object SessionClosed extends MinaMessage
}


class IoHandlerActorAdapter(val actorFactory: (IoSession) => Actor) extends IoHandler {

  private val log = Logger.get

  def send(session: IoSession, message: MinaMessage) = {
    for (actor <- IoHandlerActorAdapter.actorFor(session)) { actor ! message }
  }

  def sendOr(session: IoSession, message: => MinaMessage)(f: => Unit) = {
    IoHandlerActorAdapter.actorFor(session) match {
      case None => f
      case Some(actor) => actor ! message
    }
  }

  def sessionCreated(session: IoSession) = {
    // don't overwrite an existing actor
    IoHandlerActorAdapter.actorFor(session) match {
      case None => session.setAttribute(IoHandlerActorAdapter.ACTOR_KEY, actorFactory(session))
      case Some(_) =>
    }
  }

  def sessionOpened(session: IoSession) = send(session, MinaMessage.SessionOpened)
  def messageReceived(session: IoSession, message: AnyRef) = send(session, MinaMessage.MessageReceived(message))
  def messageSent(session: IoSession, message: AnyRef) = send(session, MinaMessage.MessageSent(message))

  def exceptionCaught(session: IoSession, cause: Throwable) = {
    sendOr(session, MinaMessage.ExceptionCaught(cause)) {
      // weird bad: an exception happened but i guess it wasn't associated with any existing session.
      log.error(cause, "Exception inside mina!")
    }
  }

  def sessionIdle(session: IoSession, status: IdleStatus) = send(session, MinaMessage.SessionIdle(status))

  def sessionClosed(session: IoSession) = {
    send(session, MinaMessage.SessionClosed)
    session.removeAttribute(IoHandlerActorAdapter.ACTOR_KEY)
  }
}


object IoHandlerActorAdapter {
  private val ACTOR_KEY = "scala.mina.actor".intern

  def actorFor(session: IoSession) = {
    val actor = session.getAttribute(ACTOR_KEY).asInstanceOf[Actor]
    if (actor == null) None else Some(actor)
  }

  def setActorFor(session: IoSession, actor: Actor) = session.setAttribute(ACTOR_KEY, actor)
}