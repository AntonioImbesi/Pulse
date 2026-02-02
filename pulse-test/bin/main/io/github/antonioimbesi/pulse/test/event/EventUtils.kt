package io.github.antonioimbesi.pulse.test.event

/**
 * Find the first state change event.
 */
fun <State, SideEffect> List<ProcessorEvent<State, SideEffect>>.firstState(): State? =
    filterIsInstance<ProcessorEvent.StateChange<State>>().firstOrNull()?.newState

/**
 * Find the last state change event.
 */
fun <State, SideEffect> List<ProcessorEvent<State, SideEffect>>.lastState(): State? =
    filterIsInstance<ProcessorEvent.StateChange<State>>().lastOrNull()?.newState

/**
 * Find the first side effect.
 */
fun <State, SideEffect> List<ProcessorEvent<State, SideEffect>>.firstEffect(): SideEffect? =
    filterIsInstance<ProcessorEvent.SideEffectEmitted<SideEffect>>().firstOrNull()?.sideEffect

/**
 * Find the last side effect.
 */
fun <State, SideEffect> List<ProcessorEvent<State, SideEffect>>.lastEffect(): SideEffect? =
    filterIsInstance<ProcessorEvent.SideEffectEmitted<SideEffect>>().lastOrNull()?.sideEffect
