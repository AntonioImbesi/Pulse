package io.github.antonioimbesi.pulse.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Primary interface for hosts (typically ViewModels) that expose MVI state management capabilities.
 *
 * This interface defines the three fundamental operations of the MVI pattern:
 * - Observable state stream for UI rendering
 * - One-time side effect stream for navigation, toasts, etc.
 * - Intent dispatch mechanism for user actions
 *
 * Implementations should delegate to an [MviEngine][io.github.antonioimbesi.pulse.core.engine.MviEngine]
 * rather than implementing the MVI logic directly. This separation allows the host to focus on
 * lifecycle management while the engine handles state coordination.
 *
 * @param State The immutable state type that represents the current UI state.
 * @param Intent The sealed interface/class representing all possible user actions.
 * @param SideEffect The type representing one-time events that shouldn't survive configuration changes.
 *
 * @see io.github.antonioimbesi.pulse.core.engine.MviEngine
 */
interface MviHost<State, Intent : Any, SideEffect> {
    /**
     * Stream of the current UI state.
     *
     * Uses [StateFlow] to ensure:
     * - New collectors immediately receive the current state
     * - State is conflated (rapid updates don't queue up)
     * - Thread-safe state access
     */
    val state: StateFlow<State>

    /**
     * Stream of one-time side effects.
     *
     * Each side effect is delivered exactly once and should
     * be consumed by a single collector (typically in the UI
     * layer for navigation or showing transient messages).
     */
    val sideEffect: Flow<SideEffect>

    /**
     * Dispatches a user intent for processing.
     *
     * The `infix` modifier allows natural DSL-style syntax:
     * ```kotlin
     * viewModel dispatch LoginIntent.Submit
     * ```
     *
     * @param intent The user action to process. Will be routed to the appropriate processor.
     */
    infix fun dispatch(intent: Intent)
}
