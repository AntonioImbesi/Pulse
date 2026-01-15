package io.github.antonioimbesi.pulse.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.core.MviHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

abstract class MviViewModel<State, Intent : Any, SideEffect>(
    private val engineFactory: MviEngineFactory<State, Intent, SideEffect>,
    initialState: State,
) : ViewModel(), MviHost<State, Intent, SideEffect> {

    private val engine = engineFactory
        .create(viewModelScope, initialState)

    override val state: StateFlow<State> get() = engine.state
    override val sideEffect: Flow<SideEffect> get() = engine.sideEffect
    override infix fun dispatch(intent: Intent) = engine dispatch intent
}
