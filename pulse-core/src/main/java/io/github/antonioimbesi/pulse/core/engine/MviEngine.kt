package io.github.antonioimbesi.pulse.core.engine

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MviEngine<UiState, Intention : Any, SideEffect> {
    val uiState: StateFlow<UiState>
    val sideEffect: Flow<SideEffect>
    infix fun dispatch(intention: Intention)
}