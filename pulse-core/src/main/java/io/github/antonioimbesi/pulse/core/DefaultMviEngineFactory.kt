package io.github.antonioimbesi.pulse.core

import io.github.antonioimbesi.pulse.core.engine.DefaultMviEngine
import io.github.antonioimbesi.pulse.core.engine.MviEngine
import io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class DefaultMviEngineFactory<UiState, Intention : Any, SideEffect>(
    private val processorExecutor: ProcessorExecutor<UiState, Intention, SideEffect>
) : MviEngineFactory<UiState, Intention, SideEffect> {

    override fun create(
        coroutineScope: CoroutineScope,
        initialState: UiState
    ): MviEngine<UiState, Intention, SideEffect> {
        return DefaultMviEngine(
            initialState = initialState,
            processorExecutor = processorExecutor,
            coroutineScope = coroutineScope,
            intentionDispatcher = Dispatchers.Default
        )
    }
}
