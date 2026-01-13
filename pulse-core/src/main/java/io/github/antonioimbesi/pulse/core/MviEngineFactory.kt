package io.github.antonioimbesi.pulse.core

import io.github.antonioimbesi.pulse.core.engine.MviEngine
import kotlinx.coroutines.CoroutineScope

fun interface MviEngineFactory<UiState, Intention : Any, SideEffect> {
    fun create(
        coroutineScope: CoroutineScope,
        initialState: UiState
    ): MviEngine<UiState, Intention, SideEffect>
}
