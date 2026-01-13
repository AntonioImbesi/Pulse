package io.github.antonioimbesi.pulse.core

import io.github.antonioimbesi.pulse.core.engine.MviEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MviHost<UiState, Intention : Any, SideEffect> {
    val uiState: StateFlow<UiState>
    val sideEffect: Flow<SideEffect>
    infix fun dispatch(intention: Intention)
}
