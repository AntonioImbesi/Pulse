package io.github.antonioimbesi.pulse.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.core.MviHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android ViewModel base class that integrates the Pulse MVI framework with the Android lifecycle.
 *
 * This class bridges [MviHost] with Android's [ViewModel] by:
 * - Binding the [MviEngine][io.github.antonioimbesi.pulse.core.engine.MviEngine] lifecycle
 *   to [viewModelScope], ensuring automatic cleanup on ViewModel destruction
 * - Providing a clean inheritance point for feature-specific ViewModels
 * - Delegating all MVI operations to the underlying engine
 *
 * **Usage:**
 * ```kotlin
 * class LoginViewModel @Inject constructor(
 *     engineFactory: LoginEngineFactory
 * ) : MviViewModel<LoginState, LoginIntent, LoginEffect>(
 *     engineFactory = engineFactory,
 *     initialState = LoginState()
 * )
 * ```
 *
 * @param State The immutable UI state type.
 * @param Intent The sealed interface representing all possible user actions.
 * @param SideEffect The type for one-time events.
 *
 * @property engineFactory Factory that creates the MVI engine.
 * @param initialState The starting state before any user interaction.
 *
 * @see MviHost The MVI contract this class implements
 * @see MviEngineFactory The factory interface for engine creation
 */
abstract class MviViewModel<State, Intent : Any, SideEffect>(
    private val engineFactory: MviEngineFactory<State, Intent, SideEffect>,
    initialState: State,
) : ViewModel(), MviHost<State, Intent, SideEffect> {

    // Engine is created once during ViewModel initialization and tied to viewModelScope.
    // When the ViewModel is cleared, viewModelScope cancels and the engine stops processing.
    private val engine = engineFactory
        .create(viewModelScope, initialState)

    override val state: StateFlow<State> get() = engine.state
    override val sideEffect: Flow<SideEffect> get() = engine.sideEffect
    override infix fun dispatch(intent: Intent) = engine dispatch intent
}
