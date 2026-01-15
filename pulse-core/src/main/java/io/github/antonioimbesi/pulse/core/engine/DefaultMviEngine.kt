package io.github.antonioimbesi.pulse.core.engine

import io.github.antonioimbesi.pulse.core.observer.IntentObserver
import io.github.antonioimbesi.pulse.core.observer.SideEffectObserver
import io.github.antonioimbesi.pulse.core.observer.StateObserver
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
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

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

    private val _sideEffect = Channel<SideEffect>(Channel.Factory.BUFFERED)
    override val sideEffect: Flow<SideEffect> get() = _sideEffect.receiveAsFlow()

    private val processorScope = object : ProcessorScope<State, SideEffect> {
        override val currentState: State get() = state.value
        override fun reduce(reducer: State.() -> State) {

            val oldState = currentState
            val newState = _state.updateAndGet(reducer)
            stateObservers.forEach { observer ->
                observer.onState(oldState as Any, newState as Any)
            }
        }

        override fun send(sideEffect: SideEffect) {
            coroutineScope.launch { _sideEffect.send(sideEffect) }
            sideEffectObservers.forEach { it.onSideEffect(sideEffect as Any) }
        }
    }

    override infix fun dispatch(intent: Intent) {
        intentObservers.forEach { it.onIntent(intent as Any) }
        coroutineScope.launch(
            context = intentDispatcher + (exceptionHandler ?: EmptyCoroutineContext)
        ) {
            processorExecutor.execute(processorScope, intent)
        }
    }
}
