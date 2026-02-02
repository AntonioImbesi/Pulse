package io.github.antonioimbesi.pulse.core

import io.github.antonioimbesi.pulse.core.engine.MviEngine
import kotlinx.coroutines.CoroutineScope

/**
 * Factory interface for creating [MviEngine] instances with concrete dependencies.
 *
 * This factory pattern serves two critical purposes:
 * 1. **Deferred Engine Creation**: The engine requires a [CoroutineScope] tied to the host's
 *    lifecycle (e.g., `viewModelScope`), which isn't available during dependency injection.
 *    The factory allows DI to inject processor dependencies while deferring scope binding.
 *
 * 2. **Testability**: Tests can provide mock factories to inject test-specific engine
 *    configurations without modifying host code.
 *
 * @param State The immutable state type managed by engines created by this factory.
 * @param Intent The sealed interface of user actions that engines will process.
 * @param SideEffect The type for one-time events emitted by engines.
 *
 * @see DefaultMviEngineFactory Standard implementation using generated ProcessorExecutor
 */
fun interface MviEngineFactory<State, Intent : Any, SideEffect> {
    /**
     * Creates a new [MviEngine] bound to the specified scope and initial state.
     *
     * Each call creates a fresh engine instance. For typical usage (one engine per ViewModel),
     * this should be called once during host initialization.
     *
     * @param coroutineScope The scope that controls the engine's lifecycle. When cancelled,
     *                       the engine stops processing intents. Typically `viewModelScope` for ViewModels.
     * @param initialState The starting state for the engine. Should represent the default UI state
     *                     before any user interaction or data loading occurs.
     * @return A fully configured engine ready to dispatch intents.
     */
    fun create(
        coroutineScope: CoroutineScope,
        initialState: State
    ): MviEngine<State, Intent, SideEffect>
}
