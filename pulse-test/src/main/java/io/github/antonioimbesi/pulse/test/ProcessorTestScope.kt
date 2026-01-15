package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import io.github.antonioimbesi.pulse.test.event.ProcessorEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Test scope that captures all processor events in execution order.
 * Works for both sync and async processors.
 */
class ProcessorTestScope<State, SideEffect>(
    initialState: State,
    private val coroutineScope: CoroutineScope? = null
) : ProcessorScope<State, SideEffect> {
    
    private var _currentState: State = initialState
    private val _events = mutableListOf<ProcessorEvent<State, SideEffect>>()
    private val _activeJobs = mutableListOf<Job>()
    
    override val currentState: State
        get() = _currentState
    
    override fun reduce(reducer: State.() -> State) {
        val oldState = _currentState
        _currentState = _currentState.reducer()
        _events.add(ProcessorEvent.StateChange(oldState, _currentState))
    }
    
    override fun send(sideEffect: SideEffect) {
        _events.add(ProcessorEvent.SideEffectEmitted(sideEffect))
    }
    
    // --- Flow and async support ---
    
    /**
     * Launch a flow collection in the test scope.
     * Use this inside your processors when testing flows.
     */
    fun <T> Flow<T>.collectInScope(action: suspend (T) -> Unit): Job {
        requireNotNull(coroutineScope) { "CoroutineScope required for flow collection" }
        val job = this.onEach { action(it) }.launchIn(coroutineScope)
        _activeJobs.add(job)
        return job
    }
    
    /**
     * Launch a coroutine in the test scope.
     */
    fun launch(block: suspend CoroutineScope.() -> Unit): Job {
        requireNotNull(coroutineScope) { "CoroutineScope required for launching coroutines" }
        val job = coroutineScope.launch(block = block)
        _activeJobs.add(job)
        return job
    }
    
    /**
     * Cancel all active jobs started in this scope.
     */
    fun cancelAllJobs() {
        _activeJobs.forEach { it.cancel() }
        _activeJobs.clear()
    }
    
    // --- Getters for assertions ---
    
    fun events(): List<ProcessorEvent<State, SideEffect>> = _events.toList()
    
    fun states(): List<State> = _events
        .filterIsInstance<ProcessorEvent.StateChange<State>>()
        .map { it.newState }
    
    fun sideEffects(): List<SideEffect> = _events
        .filterIsInstance<ProcessorEvent.SideEffectEmitted<SideEffect>>()
        .map { it.sideEffect }
    
    fun finalState(): State = _currentState
}