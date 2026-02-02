package io.github.antonioimbesi.pulse.core

import io.github.antonioimbesi.pulse.core.engine.DefaultMviEngine
import io.github.antonioimbesi.pulse.core.engine.MviEngine
import io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

/**
 * Standard [MviEngineFactory] implementation that creates [DefaultMviEngine] instances.
 *
 * @param State The immutable state type for the created engines.
 * @param Intent The sealed interface of user actions to be processed.
 * @param SideEffect The type for one-time events.
 * @property processorExecutor The executor that routes intents to their processors.
 *
 * @see DefaultMviEngine The engine implementation created by this factory
 */
class DefaultMviEngineFactory<State, Intent : Any, SideEffect>(
    private val processorExecutor: ProcessorExecutor<State, Intent, SideEffect>
) : MviEngineFactory<State, Intent, SideEffect> {

    /**
     * Creates a [DefaultMviEngine] configured with default settings.
     *
     * @param coroutineScope Controls the engine lifecycle; cancellation stops intent processing.
     * @param initialState The starting state before any intents are processed.
     * @return A configured [DefaultMviEngine] ready for intent dispatch.
     */
    override fun create(
        coroutineScope: CoroutineScope,
        initialState: State
    ): MviEngine<State, Intent, SideEffect> {
        return DefaultMviEngine(
            initialState = initialState,
            processorExecutor = processorExecutor,
            coroutineScope = coroutineScope,
        )
    }
}
