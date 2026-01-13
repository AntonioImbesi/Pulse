package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.test.event.EventSequenceBuilder
import io.github.antonioimbesi.pulse.test.event.ExpectedEvent
import io.github.antonioimbesi.pulse.test.event.ProcessorEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Assertions DSL for verifying processor behavior.
 */
class ProcessorAssertions<UiState, SideEffect>(
    private val scope: ProcessorTestScope<UiState, SideEffect>
) {

    /**
     * Assert the final state after all operations.
     */
    infix fun finalState(expected: UiState) {
        assertEquals(expected, scope.finalState())
    }

    /**
     * Assert exact event sequence (states and side effects in order).
     */
    fun expectEvents(builder: EventSequenceBuilder<UiState, SideEffect>.() -> Unit) {
        val expected = EventSequenceBuilder<UiState, SideEffect>().apply(builder).build()
        val actual = scope.events()

        assertEquals("Event count mismatch", expected.size, actual.size)

        expected.indices.forEach { i ->
            when (val exp = expected[i]) {
                is ExpectedEvent.State -> {
                    val act = actual[i]
                    assertTrue(
                        "Event $i: expected state, got $act",
                        act is ProcessorEvent.StateChange
                    )
                    exp.assertion((act as ProcessorEvent.StateChange).newState)
                }
                is ExpectedEvent.Effect -> {
                    val act = actual[i]
                    assertTrue(
                        "Event $i: expected effect, got $act",
                        act is ProcessorEvent.SideEffectEmitted
                    )
                    exp.assertion((act as ProcessorEvent.SideEffectEmitted).sideEffect)
                }
            }
        }
    }

    /**
     * Assert states in order (ignoring side effects).
     */
    fun expectStates(vararg expected: UiState) {
        assertEquals(expected.toList(), scope.states())
    }

    /**
     * Assert side effects in order (ignoring states).
     */
    fun expectSideEffects(vararg expected: SideEffect) {
        assertEquals(expected.toList(), scope.sideEffects())
    }

    /**
     * Assert no state changes occurred.
     */
    fun noStateChanges() {
        assertTrue("Expected no state changes", scope.states().isEmpty())
    }

    /**
     * Assert no side effects were emitted.
     */
    fun noSideEffects() {
        assertTrue("Expected no side effects", scope.sideEffects().isEmpty())
    }

    /**
     * Assert total event count.
     */
    infix fun eventCount(expected: Int) {
        assertEquals(expected, scope.events().size)
    }

    /**
     * Custom assertion on all events.
     */
    fun assertEvents(assertion: (List<ProcessorEvent<UiState, SideEffect>>) -> Unit) {
        assertion(scope.events())
    }
}
