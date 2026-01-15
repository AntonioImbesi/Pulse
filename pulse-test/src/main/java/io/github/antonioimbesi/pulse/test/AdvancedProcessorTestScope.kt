package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import io.github.antonioimbesi.pulse.test.event.ProcessorEvent
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Advanced test scope with additional utilities for complex scenarios.
 */
class AdvancedProcessorTestScope<State, SideEffect>(
    initialState: State,
    private val testScope: TestScope
) {
    private val processorScope = ProcessorTestScope<State, SideEffect>(initialState, testScope)
    
    val currentState: State get() = processorScope.currentState
    val events: List<ProcessorEvent<State, SideEffect>> get() = processorScope.events()
    val states: List<State> get() = processorScope.states()
    val sideEffects: List<SideEffect> get() = processorScope.sideEffects()
    val finalState: State get() = processorScope.finalState()
    
    fun getScope(): ProcessorScope<State, SideEffect> = processorScope
    
    /**
     * Advance virtual time and return current state for chaining.
     */
    fun advanceTimeBy(delayTimeMillis: Long): AdvancedProcessorTestScope<State, SideEffect> {
        testScope.advanceTimeBy(delayTimeMillis)
        return this
    }
    
    /**
     * Advance until all pending tasks complete.
     */
    fun advanceUntilIdle(): AdvancedProcessorTestScope<State, SideEffect> {
        testScope.advanceUntilIdle()
        return this
    }
    
    /**
     * Take a snapshot of current events.
     */
    fun snapshotEvents(): List<ProcessorEvent<State, SideEffect>> = events.toList()
    
    /**
     * Get events since a specific position.
     */
    fun eventsSince(position: Int): List<ProcessorEvent<State, SideEffect>> = 
        events.drop(position)
    
    /**
     * Get the number of events.
     */
    fun eventCount(): Int = events.size
    
    fun cleanup() {
        processorScope.cancelAllJobs()
    }
}
