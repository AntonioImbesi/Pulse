package io.github.antonioimbesi.pulse.core

import io.github.antonioimbesi.pulse.core.engine.DefaultMviEngine
import io.github.antonioimbesi.pulse.core.engine.MviEngine
import io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class DefaultMviEngineFactory<State, Intent : Any, SideEffect>(
    private val processorExecutor: ProcessorExecutor<State, Intent, SideEffect>
) : MviEngineFactory<State, Intent, SideEffect> {

    override fun create(
        coroutineScope: CoroutineScope,
        initialState: State
    ): MviEngine<State, Intent, SideEffect> {
        return DefaultMviEngine(
            initialState = initialState,
            processorExecutor = processorExecutor,
            coroutineScope = coroutineScope,
            intentDispatcher = Dispatchers.Default
        )
    }
}
