package io.github.antonioimbesi.pulse.core.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Core state machine that coordinates the MVI (Model-View-Intent) cycle.
 *
 * The engine is responsible for:
 * - Maintaining the single source of truth for UI state
 * - Routing dispatched intents to appropriate processors
 * - Managing the side effect delivery channel
 * - Ensuring thread-safe state transitions
 *
 * Typically, you won't implement this interface directly. Use [DefaultMviEngine]
 * or create an engine via [MviEngineFactory][io.github.antonioimbesi.pulse.core.MviEngineFactory].
 *
 * @param State The immutable UI state type. Must support equality comparison for proper
 *              state diffing (typically a data class).
 * @param Intent The sealed type representing user actions. Must be non-null ([Any] bound).
 * @param SideEffect The type for one-time events that don't belong in persistent state.
 *
 * @see DefaultMviEngine The production implementation of this interface
 * @see io.github.antonioimbesi.pulse.core.MviHost The ViewModel-facing facade that wraps engines
 */
interface MviEngine<State, Intent : Any, SideEffect> {
    /**
     * Observable stream of the current UI state.
     */
    val state: StateFlow<State>

    /**
     * Stream of one-time side effects for the UI layer.
     */
    val sideEffect: Flow<SideEffect>

    /**
     * Dispatches a user intent for asynchronous processing.
     *
     * @param intent The user action to process.
     */
    infix fun dispatch(intent: Intent)
}