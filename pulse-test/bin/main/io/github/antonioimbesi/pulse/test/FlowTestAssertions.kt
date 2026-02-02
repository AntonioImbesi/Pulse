package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.test.event.ProcessorEvent
import org.junit.Assert.assertEquals

/**
 * Assertions for testing with flows.
 */
class FlowTestAssertions<State, SideEffect, T>(
    private val scope: AdvancedProcessorTestScope<State, SideEffect>,
    private val flowValues: List<T>
) {
    fun assertFlowEmissions(vararg expected: T) {
        assertEquals("Flow emissions don't match", expected.toList(), flowValues)
    }
    
    fun assertFlowCount(count: Int) {
        assertEquals("Flow emission count mismatch", count, flowValues.size)
    }
    
    fun assertEvents(assertion: (List<ProcessorEvent<State, SideEffect>>) -> Unit) {
        assertion(scope.events)
    }
    
    fun assertFinalState(expected: State) {
        assertEquals(expected, scope.finalState)
    }
}
