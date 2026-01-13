package io.github.antonioimbesi.pulse.core.processor

interface ProcessorScope<UiState, SideEffect> {
    val currentUiState: UiState
    fun reduce(reducer: UiState.() -> UiState)
    fun send(sideEffect: SideEffect)
}
