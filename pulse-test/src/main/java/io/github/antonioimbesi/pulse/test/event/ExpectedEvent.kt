package io.github.antonioimbesi.pulse.test.event

internal sealed interface ExpectedEvent<out State, out SideEffect> {
    data class State<State>(val assertion: (State) -> Unit) : ExpectedEvent<State, Nothing>
    data class Effect<SideEffect>(val assertion: (SideEffect) -> Unit) : ExpectedEvent<Nothing, SideEffect>
}
