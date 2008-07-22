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

    def readDelimiter(getDelimiter: State => Byte, process: (State, Int) => Option[Step]) =
        new ReadDelimiterStep(getDelimiter, process)
    def readDelimiter(delimiter: Byte, process: (State, Int) => Option[Step]) =
        new ReadDelimiterStep((x: State) => delimiter, process)

    // this isn't very efficient (lots of buffer copying):
    def readDelimiterBuffer(getDelimiter: State => Byte, process: (State, Array[Byte]) => Option[Step]) =
        new ReadDelimiterStep(getDelimiter, (state: State, n: Int) => {
            val byteBuffer = new Array[Byte](n)
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })
    def readDelimiterBuffer(delimiter: Byte, process: (State, Array[Byte]) => Option[Step]) =
        new ReadDelimiterStep((x: State) => delimiter, (state: State, n: Int) => {
            val byteBuffer = new Array[Byte](n)
            state.buffer.get(byteBuffer)
            process(state, byteBuffer)
        })
}
