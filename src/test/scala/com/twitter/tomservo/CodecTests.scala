package com.twitter.tomservo

import java.nio.ByteOrder
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

    def quickDecode(decoder: Decoder, s: String): Unit = quickDecode(decoder, s.getBytes)
    def quickDecode(decoder: Decoder, b: Array[Byte]): Unit = quickDecode(decoder, IoBuffer.wrap(b))
    def quickDecode(decoder: Decoder, buf: IoBuffer): Unit = {
        decoder.decode(fakeSession, buf, fakeDecoderOutput)
    }


    test("readBytes") {
        var scored = false
        val step = readByteBuffer(3) { buffer =>
            scored = true
            End
        }

        val decoder = new Decoder(step)
        quickDecode(decoder, "xx")
        expect(Nil) { written }
        expect(false) { scored }
        quickDecode(decoder, "y")
        expect(Nil) { written }
        expect(true) { scored }
    }

    test("readBytes can chunk") {
        // chunk up every 4 bytes:
        val step = readByteBuffer(4) { buffer =>
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
        val step = readDelimiterBuffer('\n'.toByte) { buffer =>
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
        val step = readDelimiterBuffer('\n'.toByte) { buffer =>
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
        val step = readLine { line => state.out.write(line); End }
        val decoder = new Decoder(step)

        quickDecode(decoder, "hello there\r\ncat")
        expect(List("hello there")) { written }
        quickDecode(decoder,"s don't use CR\n")
        expect(List("hello there", "cats don't use CR")) { written }
        quickDecode(decoder, "thing\r\n\nstop\r\n\r\nokay.\n")
        expect(List("hello there", "cats don't use CR", "thing", "", "stop", "", "okay.")) { written }
    }

    test("readLine preserving CRLF") {
        val step = readLine(false) { line => state.out.write(line); End }
        val decoder = new Decoder(step)

        quickDecode(decoder, "hello there\r\ncat")
        expect(List("hello there\r\n")) { written }
        quickDecode(decoder, "s don't use CR\n")
        expect(List("hello there\r\n", "cats don't use CR\n")) { written }
        quickDecode(decoder, "thing\r\n\nstop\r\n\r\nokay.\n")
        expect(List("hello there\r\n", "cats don't use CR\n", "thing\r\n", "\n", "stop\r\n", "\r\n", "okay.\n")) { written }
    }

    test("combine") {
        val step = readInt32 { len =>
            readByteBuffer(len) { bytes =>
                state.out.write(new String(bytes, "UTF-8"))
                End
            }
        }
        val decoder = new Decoder(step)

        val buffer = IoBuffer.allocate(9)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(5)
        buffer.put("hello".getBytes)
        buffer.flip
        quickDecode(decoder, buffer)
        expect(List("hello")) { written }
    }

    test("combine with branching") {
        // 1-byte "type" field indicates if a string or int follows
        val step = readInt8 { datatype =>
            if ((datatype & 0x80) == 0) {
                readByteBuffer(datatype & 0x7f) { bytes =>
                    state.out.write(new String(bytes, "UTF-8"))
                    End
                }
            } else {
                readInt32 { n =>
                    state.out.write(n)
                    End
                }
            }
        }
        val decoder = new Decoder(step)

        val buffer = IoBuffer.allocate(14)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.put(3.toByte)
        buffer.put("cat".getBytes)
        buffer.put(0xff.toByte)
        buffer.putInt(23)
        buffer.put(4.toByte)
        buffer.put("yay!".getBytes)
        buffer.flip
        quickDecode(decoder, buffer)
        expect(List("cat", 23, "yay!")) { written }
    }

}
