package io.github.antonioimbesi.pulse.core.processor

fun interface IntentProcessor<State, in Intent, SideEffect> {
    suspend fun ProcessorScope<State, SideEffect>.process(intent: Intent)
}
