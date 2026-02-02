package io.github.antonioimbesi.pulse.core.engine

import io.github.antonioimbesi.pulse.core.observer.IntentObserver
import io.github.antonioimbesi.pulse.core.observer.SideEffectObserver
import io.github.antonioimbesi.pulse.core.observer.StateObserver
import io.github.antonioimbesi.pulse.core.processor.InitProcessor
import io.github.antonioimbesi.pulse.core.processor.InitProcessorScope
import io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Default implementation of [MviEngine] that coordinates state management and intent processing.
 *
 * This engine orchestrates the MVI cycle:
 * 1. Initializes resources via [InitProcessor] (if provided)
 * 2. Receives intents via [dispatch]
 * 3. Routes them through the [ProcessorExecutor] to the appropriate processor
 * 4. Processors update state via [ProcessorScope.reduce] and emit effects via [ProcessorScope.send]
 * 5. State changes and effects are propagated to collectors
 *
 * **Concurrency Model**: Each dispatched intent spawns a new coroutine on [intentDispatcher].
 * This means multiple intents can execute concurrently. State updates via [reduce] are
 * atomic (using [MutableStateFlow.updateAndGet]), but processors handling concurrent
 * intents should be designed to handle interleaved execution.
 *
 * **Observer Hooks**: Optional observers can monitor all intents, state transitions,
 * and side effects without modifying processor logic.
 *
 * @param State The immutable state type (typically a data class).
 * @param Intent The type for user actions.
 * @param SideEffect The type for one-time events.
 *
 * @property initialState The starting state when the engine is created.
 * @property processorExecutor The KSP-generated executor that routes intents to processors.
 * @property coroutineScope The scope controlling engine lifecycle; cancellation stops processing.
 * @property intentDispatcher The dispatcher for executing processor logic.
 *                            Defaults to [Dispatchers.Default] for CPU-bound work.
 * @property exceptionHandler Optional handler for uncaught processor exceptions.
 *                            If null, exceptions propagate to [coroutineScope]'s handler.
 * @property intentObservers Hooks called synchronously when an intent is dispatched.
 * @property stateObservers Hooks called synchronously on each state transition.
 * @property sideEffectObservers Hooks called synchronously when side effects are emitted.
 */
class DefaultMviEngine<State, Intent : Any, SideEffect>(
    private val initialState: State,
    private val processorExecutor: ProcessorExecutor<State, Intent, SideEffect>,
    private val coroutineScope: CoroutineScope,
    private val intentDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val exceptionHandler: CoroutineExceptionHandler? = null,
    private val intentObservers: List<IntentObserver> = emptyList(),
    private val stateObservers: List<StateObserver> = emptyList(),
    private val sideEffectObservers: List<SideEffectObserver> = emptyList(),
) : MviEngine<State, Intent, SideEffect> {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<State> get() = _state.asStateFlow()

    // BUFFERED capacity prevents side effects from being lost during brief collector absence
    // (e.g., during configuration changes when the UI temporarily detaches)
    private val _sideEffect = Channel<SideEffect>(Channel.Factory.BUFFERED)
    override val sideEffect: Flow<SideEffect> get() = _sideEffect.receiveAsFlow()

    init {
        // Initialize the init processor if one exists
        processorExecutor.getInitProcessor()?.let { initProcessor ->
            coroutineScope.launch(
                context = intentDispatcher + (exceptionHandler ?: EmptyCoroutineContext)
            ) {
                with(initProcessor) {
                    initProcessorScope.init()
                }
            }
        }
    }

    /**
     * Implementation of [ProcessorScope] that provides processors with controlled
     * access to state mutation and side effect emission.
     *
     * This scope is passed to processors and provides the only valid way to:
     * - Read current state ([currentState])
     * - Update state atomically ([reduce])
     * - Emit side effects ([send])
     */
    private val processorScope = object : ProcessorScope<State, SideEffect> {
        /**
         * Returns the current state at the moment of access.
         *
         * Note: In concurrent scenarios, this value may change between read and subsequent
         * reduce. For read-modify-write operations, access state within the [reduce] lambda.
         */
        override val currentState: State get() = state.value

        /**
         * Atomically updates state using the provided reducer function.
         *
         * The reducer receives the current state and must return the new state.
         * State observers are notified after the transition with both old and new values.
         *
         * Thread-safe: Uses [MutableStateFlow.updateAndGet] internally for atomic transitions.
         */
        override fun reduce(reducer: State.() -> State) {
            // Capture oldState before atomic update for observer notification
            val oldState = currentState
            val newState = _state.updateAndGet(reducer)
            stateObservers.forEach { observer ->
                // Cast to Any required because observers use type-erased signature for flexibility
                observer.onState(oldState as Any, newState as Any)
            }
        }

        /**
         * Emits a side effect to be consumed by the UI layer.
         *
         * Launches in [coroutineScope] to ensure delivery even if the calling
         * processor completes. Effects are buffered and won't be lost if no
         * collector is currently active.
         */
        override fun send(sideEffect: SideEffect) {
            coroutineScope.launch { _sideEffect.send(sideEffect) }
            sideEffectObservers.forEach { it.onSideEffect(sideEffect as Any) }
        }
    }

    /**
     * Implementation of [InitProcessorScope] that provides init processors with
     * engine-lifetime operations and reactive state utilities.
     */
    private val initProcessorScope = object : InitProcessorScope<State, SideEffect> {
        override val currentState: State get() = processorScope.currentState
        override fun reduce(reducer: State.() -> State) = processorScope.reduce(reducer)
        override fun send(sideEffect: SideEffect) = processorScope.send(sideEffect)
        override val coroutineScope: CoroutineScope get() = this@DefaultMviEngine.coroutineScope
        override val stateFlow: StateFlow<State> get() = state
    }

    /**
     * Dispatches an intent for asynchronous processing.
     *
     * Processing flow:
     * 1. A new coroutine is launched on [intentDispatcher] with optional [exceptionHandler]
     * 2. The [processorExecutor] routes the intent to the appropriate processor
     * 3. The processor runs with [processorScope] for state/effect operations
     *
     * This method returns immediately; processing happens asynchronously.
     *
     * @param intent The user action to process.
     */
    override infix fun dispatch(intent: Intent) {
        coroutineScope.launch(
            // Combine dispatcher with exception handler (or empty context if no handler)
            context = intentDispatcher + (exceptionHandler ?: EmptyCoroutineContext)
        ) {
            processorExecutor.execute(processorScope, intent)
        }
        intentObservers.forEach { it.onIntent(intent as Any) }
    }
}