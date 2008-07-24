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


    test("readBytes") {
        var scored = false
        val step = readByteBuffer(3) { (state: State, buffer: Array[Byte]) =>
            scored = true
            End
        }

        val decoder = new Decoder(step)
        decoder.decode(fakeSession, IoBuffer.wrap("xx".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        expect(false) { scored }
        decoder.decode(fakeSession, IoBuffer.wrap("y".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        expect(true) { scored }
    }

    test("readBytes can chunk") {
        // chunk up every 4 bytes:
        val step = readByteBuffer(4) { (state: State, buffer: Array[Byte]) =>
            state.out.write(new String(buffer))
            End
        }
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

    test("readDelimiter") {
        var scored = false
        val step = readDelimiterBuffer('\n'.toByte) { (state: State, buffer: Array[Byte]) =>
            scored = true
            End
        }
        val decoder = new Decoder(step)
        decoder.decode(fakeSession, IoBuffer.wrap("hello".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        expect(false) { scored }
        decoder.decode(fakeSession, IoBuffer.wrap(" kitty\n".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        expect(true) { scored }
    }

    test("readDelimiter can chunk") {
        val step = readDelimiterBuffer('\n'.toByte) { (state: State, buffer: Array[Byte]) =>
            state.out.write(new String(buffer))
            End
        }
        val decoder = new Decoder(step)

        // partial write gives nothing:
        decoder.decode(fakeSession, IoBuffer.wrap("partia".getBytes), fakeDecoderOutput)
        expect(Nil) { written }
        // overlap write finishes one block and continues buffering:
        decoder.decode(fakeSession, IoBuffer.wrap("l\nand".getBytes), fakeDecoderOutput)
        expect(List("partial\n")) { written }
        // overlap write continues to block correctly:
        decoder.decode(fakeSession, IoBuffer.wrap(" another\nbut the".getBytes), fakeDecoderOutput)
        expect(List("partial\n", "and another\n")) { written }
        // many-block write gives all finished blocks:
        decoder.decode(fakeSession, IoBuffer.wrap("n\nmany\nnew ones\nbo".getBytes), fakeDecoderOutput)
        expect(List("partial\n", "and another\n", "but then\n", "many\n", "new ones\n")) { written }
        // partial write gives nothing, even with partial buffer:
        decoder.decode(fakeSession, IoBuffer.wrap("re".getBytes), fakeDecoderOutput)
        expect(List("partial\n", "and another\n", "but then\n", "many\n", "new ones\n")) { written }
        // exact block closing gives a block:
        decoder.decode(fakeSession, IoBuffer.wrap("d now\n".getBytes), fakeDecoderOutput)
        expect(List("partial\n", "and another\n", "but then\n", "many\n", "new ones\n", "bored now\n")) { written }
        // ditto for an exact whole block:
        decoder.decode(fakeSession, IoBuffer.wrap("bye\n".getBytes), fakeDecoderOutput)
        expect(List("partial\n", "and another\n", "but then\n", "many\n", "new ones\n", "bored now\n", "bye\n")) { written }
    }

    test("readLine") {
        val step = readLine { (state: State, line: String) =>
            state.out.write(line)
            End
        }
        val decoder = new Decoder(step)

        decoder.decode(fakeSession, IoBuffer.wrap("hello there\r\ncat".getBytes), fakeDecoderOutput)
        expect(List("hello there")) { written }
        decoder.decode(fakeSession, IoBuffer.wrap("s don't use CR\n".getBytes), fakeDecoderOutput)
        expect(List("hello there", "cats don't use CR")) { written }
        decoder.decode(fakeSession, IoBuffer.wrap("thing\r\n\nstop\r\n\r\nokay.\n".getBytes), fakeDecoderOutput)
        expect(List("hello there", "cats don't use CR", "thing", "", "stop", "", "okay.")) { written }
    }

    test("readLine preserving CRLF") {
        val step = readLine(false) { (state: State, line: String) =>
            state.out.write(line)
            End
        }
        val decoder = new Decoder(step)

        decoder.decode(fakeSession, IoBuffer.wrap("hello there\r\ncat".getBytes), fakeDecoderOutput)
        expect(List("hello there\r\n")) { written }
        decoder.decode(fakeSession, IoBuffer.wrap("s don't use CR\n".getBytes), fakeDecoderOutput)
        expect(List("hello there\r\n", "cats don't use CR\n")) { written }
        decoder.decode(fakeSession, IoBuffer.wrap("thing\r\n\nstop\r\n\r\nokay.\n".getBytes), fakeDecoderOutput)
        expect(List("hello there\r\n", "cats don't use CR\n", "thing\r\n", "\n", "stop\r\n", "\r\n", "okay.\n")) { written }
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
