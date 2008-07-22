package com.twitter.tomservo

import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.codec._


class Decoder(private val firstStep: Step) extends ProtocolDecoder {

    private val STATE_KEY = "com.twitter.tomservo.state".intern

    def dispose(session: IoSession): Unit = {
        session.removeAttribute(STATE_KEY)
    }

    def finishDecode(session: IoSession, out: ProtocolDecoderOutput): Unit = {
        // um, no. :)
    }

    def decode(session: IoSession, in: IoBuffer, out: ProtocolDecoderOutput): Unit = {
        val state = session.getAttribute(STATE_KEY) match {
            case null =>
                val newState = new State(firstStep, session, out)
                session.setAttribute(STATE_KEY, newState)
                newState
            case x => x.asInstanceOf[State]
        }
        state.addBuffer(in)

        var done = false
        do {
            val step = state.currentStep
            step(state) match {
                case NEED_DATA =>
                    // stay in current state; collect more data; try again later.
                    done = true
                case COMPLETE =>
                    /* if there's a next step set in the state, use that.
                     * otherwise if there's an implicit next step after the
                     * current one, use that. if nothing else, repeat the
                     * current step.
                     */
                    state.currentStep = state.nextStep match {
                        case Some(s) => s
                        case None => step.next match {
                            case Some(s) => s
                            case None => step
                        }
                    }
                    state.nextStep = None
            }
        } while (! done)

        state.buffer.compact
    }
}
