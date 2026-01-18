package io.github.antonioimbesi.pulse.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.antonioimbesi.pulse.core.MviHost

/**
 * Collects the [MviHost.sideEffect] flow safely with lifecycle awareness.
 *
 * This extension is specifically designed for safely collecting MVI side effects in the UI layer.
 * It ensures effects are only processed when the lifecycle is in an appropriate state,
 * preventing crashes from performing UI operations (navigation, toasts, ...) when the
 * UI is stopped or destroyed.
 *
 * **Usage:**
 * ```kotlin
 * @Composable
 * fun LoginScreen(viewModel: LoginViewModel) {
 *     viewModel.collectSideEffect { effect ->
 *         when (effect) {
 *             is LoginEffect.NavigateToHome -> navController.navigate("home")
 *             is LoginEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
 *         }
 *     }
 * }
 * ```
 *
 * @param keys Additional keys that will cause the collection to restart when changed.
 *             Useful when the effect handler depends on external values that may change.
 * @param minActiveState The minimum lifecycle state to collect in. Defaults to [Lifecycle.State.STARTED]
 *                       because most UI operations (navigation, dialogs) require the activity
 *                       to be visible. Use [Lifecycle.State.RESUMED] for operations requiring
 *                       full foreground (e.g., camera access).
 * @param block The suspend function invoked for each emitted value. Runs on [kotlinx.coroutines.Dispatchers.Main.immediate].
 */
@Suppress("ComposableNaming")
@Composable
fun <State, Intent : Any, SideEffect> MviHost<State, Intent, SideEffect>.collectSideEffect(
    vararg keys: Any?,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (SideEffect) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // LaunchedEffect restarts when lifecycle or keys change, ensuring proper cleanup
    LaunchedEffect(lifecycleOwner.lifecycle, *keys) {
        // repeatOnLifecycle suspends collection when below minActiveState
        // and resumes when the state is reached again
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            sideEffect.collect(block)
        }
    }
}

/**
 * Collects the [MviHost.state] flow safely with lifecycle awareness.
 *
 * This extension delegates to [collectAsStateWithLifecycle] to ensure the state flow is
 * collected safely. It will automatically pause collection when the lifecycle falls below
 * [minActiveState], conserving resources.
 *
 * **Usage:**
 * ```kotlin
 * @Composable
 * fun CounterScreen(viewModel: CounterViewModel) {
 *     // Automatically collects safely with the lifecycle
 *     val state by viewModel.collectState()
 *
 *     Text("Count: ${state.count}")
 * }
 * ```
 *
 * @param minActiveState The minimum lifecycle state to collect in. Defaults to [Lifecycle.State.STARTED].
 * @param context The [kotlin.coroutines.CoroutineContext] to use for collection.
 * @return A [androidx.compose.runtime.State] object representing the latest value of the [MviHost.state] flow.
 */
@Composable
fun <State, Intent : Any, SideEffect> MviHost<State, Intent, SideEffect>.collectState(
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext
): androidx.compose.runtime.State<State> {
    return state.collectAsStateWithLifecycle(
        lifecycleOwner = LocalLifecycleOwner.current,
        minActiveState = minActiveState,
        context = context
    )
}
