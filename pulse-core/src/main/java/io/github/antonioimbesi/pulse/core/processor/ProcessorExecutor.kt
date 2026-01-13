package io.github.antonioimbesi.pulse.core.processor

fun interface ProcessorExecutor<UiState, in Intention, SideEffect> {
    suspend fun execute(context: ProcessorScope<UiState, SideEffect>, intention: Intention): Unit
}
