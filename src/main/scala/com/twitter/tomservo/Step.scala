package com.twitter.tomservo


// has to have a parameter so they don't become the same object:
sealed abstract case class StepResult(val name: String)
case object NEED_DATA extends StepResult("need-data")
case object COMPLETE extends StepResult("complete")


abstract class Step {
    // an implicit (default) next-step can be set via the :: operator
    private[tomservo] var next: Step = End

    def apply(state: State): StepResult

    // s1 :: s2  -->  s1 then s2
    def ::(s: Step) = { s.next = this; this }
}

/**
 * Special Step which means "end of decoding; start over".
 */
final object End extends Step {
    override def apply(state: State): StepResult = COMPLETE
}


// FIXME: move these

class ReadBytesStep(getCount: State => Int, process: State => Step) extends Step {
    def apply(state: State): StepResult = {
        val count = getCount(state)
        if (state.buffer.limit - state.buffer.position < count) {
            NEED_DATA
        } else {
            state.nextStep = process(state)
            COMPLETE
        }
    }
}

// when you know the byte count ahead of time, this is probably faster.
class ReadNBytesStep(count: Int, process: State => Step) extends Step {
    def apply(state: State): StepResult = {
        if (state.buffer.limit - state.buffer.position < count) {
            NEED_DATA
        } else {
            state.nextStep = process(state)
            COMPLETE
        }
    }
}

class ReadDelimiterStep(getDelimiter: State => Byte, process: (State, Int) => Step) extends Step {
    def apply(state: State): StepResult = {
        val delimiter = getDelimiter(state)
        state.buffer.indexOf(delimiter) match {
            case -1 =>
                NEED_DATA
            case n =>
                state.nextStep = process(state, n - state.buffer.position + 1)
                COMPLETE
        }
    }
}

// when you know the delimiter ahead of time, this is probably faster.
class ReadNDelimiterStep(delimiter: Byte, process: (State, Int) => Step) extends Step {
    def apply(state: State): StepResult = {
        state.buffer.indexOf(delimiter) match {
            case -1 =>
                NEED_DATA
            case n =>
                state.nextStep = process(state, n - state.buffer.position + 1)
                COMPLETE
        }
    }
}
