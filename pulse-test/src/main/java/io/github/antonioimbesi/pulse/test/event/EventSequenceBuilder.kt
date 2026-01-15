package io.github.antonioimbesi.pulse.test.event

import org.junit.Assert.assertEquals

/**
 * Builder for expected event sequences.
 */
class EventSequenceBuilder<State, SideEffect> {
    private val events = mutableListOf<ExpectedEvent<State, SideEffect>>()

    fun state(assertion: (State) -> Unit) {
        events.add(ExpectedEvent.State(assertion))
    }

    fun state(expected: State) {
        events.add(ExpectedEvent.State { assertEquals(expected, it) })
    }

    fun sideEffect(assertion: (SideEffect) -> Unit) {
        events.add(ExpectedEvent.Effect(assertion))
    }

    fun sideEffect(expected: SideEffect) {
        events.add(ExpectedEvent.Effect { assertEquals(expected, it) })
    }

    internal fun build() = events.toList()
}
