package io.github.antonioimbesi.pulse.sample.counter

import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.core.engine.DefaultMviEngine
import io.github.antonioimbesi.pulse.core.engine.MviEngine
import io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class CounterMviEngineFactory<State, Intent : Any, SideEffect>(
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
            intentDispatcher = Dispatchers.Default,
            intentObservers = listOf(LoggerMviObserver),
            stateObservers = listOf(LoggerMviObserver),
            sideEffectObservers = listOf(LoggerMviObserver),
        )
    }
}