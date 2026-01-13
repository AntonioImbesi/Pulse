package io.github.antonioimbesi.pulse.core.processor

fun interface IntentionProcessor<UiState, in Intention, SideEffect> {
    suspend fun ProcessorScope<UiState, SideEffect>.process(intention: Intention)
}
