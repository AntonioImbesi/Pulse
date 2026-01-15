package io.github.antonioimbesi.pulse.core.processor

interface ProcessorScope<State, SideEffect> {
    val currentState: State
    fun reduce(reducer: State.() -> State)
    fun send(sideEffect: SideEffect)
}
