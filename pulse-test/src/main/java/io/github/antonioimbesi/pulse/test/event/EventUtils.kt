package io.github.antonioimbesi.pulse.test.event

/**
 * Find the first state change event.
 */
fun <UiState, SideEffect> List<ProcessorEvent<UiState, SideEffect>>.firstState(): UiState? =
    filterIsInstance<ProcessorEvent.StateChange<UiState>>().firstOrNull()?.newState

/**
 * Find the last state change event.
 */
fun <UiState, SideEffect> List<ProcessorEvent<UiState, SideEffect>>.lastState(): UiState? =
    filterIsInstance<ProcessorEvent.StateChange<UiState>>().lastOrNull()?.newState

/**
 * Find the first side effect.
 */
fun <UiState, SideEffect> List<ProcessorEvent<UiState, SideEffect>>.firstEffect(): SideEffect? =
    filterIsInstance<ProcessorEvent.SideEffectEmitted<SideEffect>>().firstOrNull()?.sideEffect

/**
 * Find the last side effect.
 */
fun <UiState, SideEffect> List<ProcessorEvent<UiState, SideEffect>>.lastEffect(): SideEffect? =
    filterIsInstance<ProcessorEvent.SideEffectEmitted<SideEffect>>().lastOrNull()?.sideEffect
