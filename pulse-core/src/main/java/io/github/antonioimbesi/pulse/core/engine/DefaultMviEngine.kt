package io.github.antonioimbesi.pulse.core.engine

import io.github.antonioimbesi.pulse.core.observer.IntentionObserver
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

class DefaultMviEngine<UiState, Intention : Any, SideEffect>(
    private val initialState: UiState,
    private val processorExecutor: ProcessorExecutor<UiState, Intention, SideEffect>,
    private val coroutineScope: CoroutineScope,
    private val intentionDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val exceptionHandler: CoroutineExceptionHandler? = null,
    private val intentionObservers: List<IntentionObserver> = emptyList(),
    private val stateObservers: List<StateObserver> = emptyList(),
    private val sideEffectObservers: List<SideEffectObserver> = emptyList(),
) : MviEngine<UiState, Intention, SideEffect> {
    private val _uiState = MutableStateFlow(initialState)
    override val uiState: StateFlow<UiState> get() = _uiState.asStateFlow()

    private val _sideEffect = Channel<SideEffect>(Channel.Factory.BUFFERED)
    override val sideEffect: Flow<SideEffect> get() = _sideEffect.receiveAsFlow()

    private val processorScope = object : ProcessorScope<UiState, SideEffect> {
        override val currentUiState: UiState get() = uiState.value
        override fun reduce(reducer: UiState.() -> UiState) {
            val oldState = currentUiState
            val newState = reducer(oldState)

            _uiState.update { newState }

            stateObservers.forEach {
                it.onState(oldState as Any, newState as Any)
            }
        }

        override fun send(sideEffect: SideEffect) {
            coroutineScope.launch { _sideEffect.send(sideEffect) }
            sideEffectObservers.forEach { it.onSideEffect(sideEffect as Any) }
        }
    }

    override infix fun dispatch(intention: Intention) {
        intentionObservers.forEach { it.onIntention(intention as Any) }
        coroutineScope.launch(
            context = intentionDispatcher + (exceptionHandler ?: EmptyCoroutineContext)
        ) {
            processorExecutor.execute(processorScope, intention)
        }
    }
}
