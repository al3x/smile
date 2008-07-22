package com.twitter.tomservo

import scala.collection.mutable
import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.IoSession
import org.apache.mina.filter.codec.ProtocolDecoderOutput


class State(firstStep: Step, val session: IoSession, val out: ProtocolDecoderOutput) {
    var buffer: IoBuffer = IoBuffer.EMPTY_BUFFER

    // track whether we allocated this IoBuffer or it was passed in from mina
    private[tomservo] var dynamicBuffer = false

    private[tomservo] var currentStep = firstStep

    // next step to execute after the current step completes
    var nextStep: Option[Step] = None

    // arbitrary data can be stored here by Steps
    val data: mutable.Map[String, Object] = new mutable.HashMap[String, Object]


    /**
     * Add an IoBuffer from mina into our current state. If our current
     * buffer is empty, we optimistically just store the buffer from mina,
     * in the hopes that it can be completely processed inline. If we have
     * leftovers, though, we build our own cumulative buffer.
     *
     * After this method returns, 'buffer' is in flipped mode and contains
     * any previous data plus the new data.
     */
    private[tomservo] def addBuffer(in: IoBuffer) = {
        // buffers from mina always arrive in "flipped" mode.
        if (buffer.position > 0) {
            if (!dynamicBuffer) {
                // well then, make it dynamic!
                val oldBuffer = buffer
                buffer = IoBuffer.allocate(oldBuffer.position + in.limit, false)
                buffer.setAutoExpand(true)
                // mina 2.0.0-M2 has bug here!: FIXME:
//                buffer.setAutoShrink(true)
                dynamicBuffer = true

                oldBuffer.flip
                buffer.put(oldBuffer)
            }
            buffer.put(in)
            buffer.flip
        } else {
            buffer = in
            dynamicBuffer = false
        }
    }

    def apply(key: String) = data(key)

    def update(key: String, value: Object) = data(key) = value

    def reset = {
        data.clear
        currentStep = firstStep
        nextStep = None
        buffer = IoBuffer.EMPTY_BUFFER
        dynamicBuffer = false
    }
}