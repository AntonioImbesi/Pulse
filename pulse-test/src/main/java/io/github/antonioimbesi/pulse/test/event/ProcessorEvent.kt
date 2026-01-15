package io.github.antonioimbesi.pulse.test.event

sealed interface ProcessorEvent<out State, out SideEffect> {
    data class StateChange<State>(
        val oldState: State,
        val newState: State
    ) : ProcessorEvent<State, Nothing>

    data class SideEffectEmitted<SideEffect>(
        val sideEffect: SideEffect
    ) : ProcessorEvent<Nothing, SideEffect>
}