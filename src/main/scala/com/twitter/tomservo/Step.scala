package com.twitter.tomservo


// has to have a parameter so they don't become the same object:
sealed abstract case class StepResult(val name: String)
case object NEED_DATA extends StepResult("need-data")
case object COMPLETE extends StepResult("complete")


abstract class Step {
    // an implicit (default) next-step can be set via the :: operator
    private[tomservo] var next: Step = End

    def apply(): StepResult

    // s1 :: s2  -->  s1 then s2
    def ::(s: Step) = { s.next = this; this }

    def state = Decoder.localState.get()
}

/**
 * Special Step which means "end of decoding; start over".
 */
final object End extends Step {
    override def apply(): StepResult = COMPLETE
}


// FIXME: move these

class ReadBytesStep(getCount: () => Int, process: () => Step) extends Step {
    def apply(): StepResult = {
        val count = getCount()
        if (state.buffer.limit - state.buffer.position < count) {
            NEED_DATA
        } else {
            state.nextStep = process()
            COMPLETE
        }
    }
}

// when you know the byte count ahead of time, this is probably faster.
class ReadNBytesStep(count: Int, process: () => Step) extends Step {
    def apply(): StepResult = {
        if (state.buffer.limit - state.buffer.position < count) {
            NEED_DATA
        } else {
            state.nextStep = process()
            COMPLETE
        }
    }
}

class ReadDelimiterStep(getDelimiter: () => Byte, process: (Int) => Step) extends Step {
    def apply(): StepResult = {
        val delimiter = getDelimiter()
        state.buffer.indexOf(delimiter) match {
            case -1 =>
                NEED_DATA
            case n =>
                state.nextStep = process(n - state.buffer.position + 1)
                COMPLETE
        }
    }
}

// when you know the delimiter ahead of time, this is probably faster.
class ReadNDelimiterStep(delimiter: Byte, process: (Int) => Step) extends Step {
    def apply(): StepResult = {
        state.buffer.indexOf(delimiter) match {
            case -1 =>
                NEED_DATA
            case n =>
                state.nextStep = process(n - state.buffer.position + 1)
                COMPLETE
        }
    }
}
