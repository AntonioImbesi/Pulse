package io.github.antonioimbesi.pulse.test.event

internal sealed interface ExpectedEvent<out UiState, out SideEffect> {
    data class State<UiState>(val assertion: (UiState) -> Unit) : ExpectedEvent<UiState, Nothing>
    data class Effect<SideEffect>(val assertion: (SideEffect) -> Unit) : ExpectedEvent<Nothing, SideEffect>
}
