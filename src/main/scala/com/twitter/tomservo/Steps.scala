package com.twitter.tomservo


object Steps {
    def readBytes(getCount: State => Int, process: State => Option[Step]) =
        new ReadBytesStep(getCount, process)
    def readBytes(count: Int, process: State => Option[Step]) =
        new ReadBytesStep((x: State) => count, process)

    // this isn't very efficient (lots of buffer copying):
    def readByteBuffer(getCount: State => Int, process: (State, Array[Byte]) => Option[Step]) =
        new ReadBytesStep(getCount, (state: State) => {
            val byteBuffer = new Array[Byte](getCount(state))
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })
    def readByteBuffer(count: Int, process: (State, Array[Byte]) => Option[Step]) =
        new ReadBytesStep((x: State) => count, (state: State) => {
            val byteBuffer = new Array[Byte](count)
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })
}
