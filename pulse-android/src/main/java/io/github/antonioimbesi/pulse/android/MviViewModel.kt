package io.github.antonioimbesi.pulse.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.core.MviHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

abstract class MviViewModel<UiState, Intention : Any, SideEffect>(
    private val engineFactory: MviEngineFactory<UiState, Intention, SideEffect>,
    initialState: UiState,
) : ViewModel(), MviHost<UiState, Intention, SideEffect> {

    private val engine = engineFactory
        .create(viewModelScope, initialState)

    override val uiState: StateFlow<UiState> get() = engine.uiState
    override val sideEffect: Flow<SideEffect> get() = engine.sideEffect
    override infix fun dispatch(intention: Intention) = engine dispatch intention
}
