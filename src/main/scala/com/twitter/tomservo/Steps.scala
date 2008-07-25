package com.twitter.tomservo

import java.nio.ByteOrder


object Steps {
    def readBytes(getCount: State => Int)(process: State => Step) =
        new ReadBytesStep(getCount, process)
    def readBytes(count: Int)(process: State => Step) =
        new ReadNBytesStep(count, process)

    // this isn't very efficient (lots of buffer copying):
    def readByteBuffer(getCount: State => Int)(process: (State, Array[Byte]) => Step) =
        new ReadBytesStep(getCount, (state: State) => {
            val byteBuffer = new Array[Byte](getCount(state))
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })
    def readByteBuffer(count: Int)(process: (State, Array[Byte]) => Step) =
        new ReadNBytesStep(count, (state: State) => {
            val byteBuffer = new Array[Byte](count)
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })

    def readDelimiter(getDelimiter: State => Byte)(process: (State, Int) => Step) =
        new ReadDelimiterStep(getDelimiter, process)
    def readDelimiter(delimiter: Byte)(process: (State, Int) => Step) =
        new ReadNDelimiterStep(delimiter, process)

    // this isn't very efficient (lots of buffer copying):
    def readDelimiterBuffer(getDelimiter: State => Byte)(process: (State, Array[Byte]) => Step) =
        new ReadDelimiterStep(getDelimiter, (state: State, n: Int) => {
            val byteBuffer = new Array[Byte](n)
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })
    def readDelimiterBuffer(delimiter: Byte)(process: (State, Array[Byte]) => Step) =
        new ReadNDelimiterStep(delimiter, (state: State, n: Int) => {
            val byteBuffer = new Array[Byte](n)
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })

    // specialized for line buffering:
    def readLine(removeLF: Boolean)(process: (State, String) => Step) =
        new ReadNDelimiterStep('\n'.toByte, (state, n) => {
            val end = if ((n > 1) && (state.buffer.get(state.buffer.position + n - 2) == '\r'.toByte)) {
                n - 2
            } else {
                n - 1
            }
            val byteBuffer = new Array[Byte](n)
            state.buffer.get(byteBuffer)
            process(state, new String(byteBuffer, 0, (if (removeLF) end else n), "UTF-8"))
        })
    def readLine(process: (State, String) => Step): Step = readLine(true)(process)

    // read 1-byte ints:
    def readInt8(process: (State, Byte) => Step) =
        new ReadNBytesStep(1, (state: State) => {
            process(state, state.buffer.get)
        })

    // read 4-byte ints:
    def readInt32(process: (State, Int) => Step) = readInt32BE(process)
    def readInt32BE(process: (State, Int) => Step) =
        new ReadNBytesStep(4, (state: State) => {
            state.buffer.order(ByteOrder.BIG_ENDIAN)
            process(state, state.buffer.getInt)
        })
    def readInt32LE(process: (State, Int) => Step) =
        new ReadNBytesStep(4, (state: State) => {
            state.buffer.order(ByteOrder.LITTLE_ENDIAN)
            process(state, state.buffer.getInt)
        })
}
