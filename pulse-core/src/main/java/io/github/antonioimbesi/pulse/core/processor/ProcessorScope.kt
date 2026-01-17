package io.github.antonioimbesi.pulse.core.processor

/**
 * Provides processors with controlled access to state management and side effect emission.
 *
 * @param State The immutable state type that can be read and updated.
 * @param SideEffect The type of one-time events that can be emitted.
 */
interface ProcessorScope<State, SideEffect> {
    /**
     * The current UI state at the moment of access.
     */
    val currentState: State

    /**
     * Updates the state using a reducer function.
     *
     * **Usage:**
     * ```kotlin
     * reduce { copy(count = count + 1) }
     * ```
     *
     * @param reducer A function that receives the current state as the receiver and returns the new state.
     *                Typically uses `copy()` on data classes for immutable updates.
     */
    fun reduce(reducer: State.() -> State)

    /**
     * Emits a one-time side effect to be consumed by the UI layer.
     *
     * **Note:** Unlike state, side effects should represent actions, not data to display.
     * If the UI needs to show something persistently, put it in state instead.
     *
     * @param sideEffect The effect to emit to the UI layer.
     */
    fun send(sideEffect: SideEffect)
}
