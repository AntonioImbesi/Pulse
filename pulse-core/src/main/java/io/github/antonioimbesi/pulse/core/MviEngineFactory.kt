package io.github.antonioimbesi.pulse.core

import io.github.antonioimbesi.pulse.core.engine.MviEngine
import kotlinx.coroutines.CoroutineScope

fun interface MviEngineFactory<State, Intent : Any, SideEffect> {
    fun create(
        coroutineScope: CoroutineScope,
        initialState: State
    ): MviEngine<State, Intent, SideEffect>
}
