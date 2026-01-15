package io.github.antonioimbesi.pulse.core.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MviEngine<State, Intent : Any, SideEffect> {
    val state: StateFlow<State>
    val sideEffect: Flow<SideEffect>
    infix fun dispatch(intent: Intent)
}