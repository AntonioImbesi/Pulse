package io.github.antonioimbesi.pulse.core.observer

/**
 * Observer hook invoked when intents are dispatched to the MVI engine.
 */
fun interface IntentObserver {
    /**
     * Called when an intent is dispatched.
     *
     * Note: The [Any] type is used because observers are registered at the engine level.
     * Cast to your specific intent type if needed.
     *
     * @param intent The dispatched intent (type-erased to [Any]).
     */
    fun onIntent(intent: Any)
}
