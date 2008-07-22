package com.twitter.tomservo

import org.apache.mina.core.buffer.IoBuffer
import org.apache.mina.core.session.{AbstractIoSession, DummySession, IoSession}
import org.apache.mina.filter.codec._
import sorg.testing._
import com.twitter.tomservo.Steps._


object CodecTests extends Tests {

    override def testName = "CodecTests"

    private var fakeSession: IoSession = null
    private var fakeDecoderOutput: ProtocolDecoderOutput = null
    private var written: List[AnyRef] = Nil

    override def setUp = {
        written = Nil
        fakeSession = new DummySession
        fakeDecoderOutput = new ProtocolDecoderOutput {
            override def flush = {}
            override def write(obj: AnyRef) = {
                written = written + obj
            }
        }
    }


    test("ReadBytesStep") {
        var scored = false
        val step = readByteBuffer(3, (state: State, buffer: Array[Byte]) => {
            scored = true
            None
        })

        val decoder = new Decoder(step)
        decoder.decode(fakeSession, IoBuffer.wrap("xx".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        expect(false) { scored }
        decoder.decode(fakeSession, IoBuffer.wrap("y".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        expect(true) { scored }
    }

    test("ReadBytesStep can chunk") {
        // chunk up every 4 bytes:
        val step = readByteBuffer(4, (state: State, buffer: Array[Byte]) => {
            state.out.write(new String(buffer))
            None
        })
        val decoder = new Decoder(step)

        // partial write gives nothing:
        decoder.decode(fakeSession, IoBuffer.wrap("12".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        // overlap write finishes one block and continues buffering:
        decoder.decode(fakeSession, IoBuffer.wrap("345".getBytes), fakeDecoderOutput)
        expect(List("1234")) { written }
        // overlap write continues to block correctly:
        decoder.decode(fakeSession, IoBuffer.wrap("6789".getBytes), fakeDecoderOutput)
        expect(List("1234", "5678")) { written }
        // many-block write gives all finished blocks:
        decoder.decode(fakeSession, IoBuffer.wrap("ABCDEFGHIJKLM".getBytes), fakeDecoderOutput)
        expect(List("1234", "5678", "9ABC", "DEFG", "HIJK")) { written }
        // partial write gives nothing, even with partial buffer:
        decoder.decode(fakeSession, IoBuffer.wrap("N".getBytes), fakeDecoderOutput)
        expect(List("1234", "5678", "9ABC", "DEFG", "HIJK")) { written }
        // exact block closing gives a block:
        decoder.decode(fakeSession, IoBuffer.wrap("O".getBytes), fakeDecoderOutput)
        expect(List("1234", "5678", "9ABC", "DEFG", "HIJK", "LMNO")) { written }
        // ditto for an exact whole block:
        decoder.decode(fakeSession, IoBuffer.wrap("PQRS".getBytes), fakeDecoderOutput)
        expect(List("1234", "5678", "9ABC", "DEFG", "HIJK", "LMNO", "PQRS")) { written }
    }

/*    test("foo") {
        val decoder = new Decoder(new Step { x })
        def apply(state: State): StepResult = {

        class ReadBytesStep(getCount: State => Int, process: State => Option[Step]) extends Step {
            def decode(session: IoSession, in: IoBuffer, out: ProtocolDecoderOutput): Unit = {
        

        expect(3) { 3 }
    }
*/
}
