package io.github.antonioimbesi.pulse.test.event

sealed interface ProcessorEvent<out UiState, out SideEffect> {
    data class StateChange<UiState>(
        val oldState: UiState,
        val newState: UiState
    ) : ProcessorEvent<UiState, Nothing>

    data class SideEffectEmitted<SideEffect>(
        val sideEffect: SideEffect
    ) : ProcessorEvent<Nothing, SideEffect>
}