package io.github.antonioimbesi.pulse.core.observer

/**
 * Observer hook invoked when state transitions occur in the MVI engine.
 */
fun interface StateObserver {
    /**
     * Called when a state transition completes.
     *
     * Note: The [Any] type is used because observers are registered at the engine level.
     * Cast to your specific state type if needed.
     *
     * @param oldState The state before the transition (type-erased to [Any]).
     * @param newState The state after the transition (type-erased to [Any]).
     */
    fun onState(oldState: Any, newState: Any)
}