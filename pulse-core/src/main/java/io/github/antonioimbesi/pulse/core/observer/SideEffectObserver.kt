package io.github.antonioimbesi.pulse.core.observer

/**
 * Observer hook invoked when side effects are emitted from processors.
 */
fun interface SideEffectObserver {
    /**
     * Called when a side effect is emitted.
     *
     * Note: The [Any] type is used because observers are registered at the engine level.
     * Cast to your specific side effect type if needed.
     *
     * @param sideEffect The emitted side effect (type-erased to [Any]).
     */
    fun onSideEffect(sideEffect: Any)
}