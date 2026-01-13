package io.github.antonioimbesi.pulse.test

/**
 * Advanced test scope with additional utilities for complex scenarios.
 */
class AdvancedProcessorTestScope<UiState, SideEffect>(
    initialState: UiState,
    private val testScope: TestScope
) {
    private val processorScope = ProcessorTestScope<UiState, SideEffect>(initialState, testScope)
    
    val currentUiState: UiState get() = processorScope.currentUiState
    val events: List<ProcessorEvent<UiState, SideEffect>> get() = processorScope.events()
    val states: List<UiState> get() = processorScope.states()
    val sideEffects: List<SideEffect> get() = processorScope.sideEffects()
    val finalState: UiState get() = processorScope.finalState()
    
    fun getScope(): ProcessorScope<UiState, SideEffect> = processorScope
    
    /**
     * Advance virtual time and return current state for chaining.
     */
    fun advanceTimeBy(delayTimeMillis: Long): AdvancedProcessorTestScope<UiState, SideEffect> {
        testScope.advanceTimeBy(delayTimeMillis)
        return this
    }
    
    /**
     * Advance until all pending tasks complete.
     */
    fun advanceUntilIdle(): AdvancedProcessorTestScope<UiState, SideEffect> {
        testScope.advanceUntilIdle()
        return this
    }
    
    /**
     * Take a snapshot of current events.
     */
    fun snapshotEvents(): List<ProcessorEvent<UiState, SideEffect>> = events.toList()
    
    /**
     * Get events since a specific position.
     */
    fun eventsSince(position: Int): List<ProcessorEvent<UiState, SideEffect>> = 
        events.drop(position)
    
    /**
     * Get the number of events.
     */
    fun eventCount(): Int = events.size
    
    fun cleanup() {
        processorScope.cancelAllJobs()
    }
}
