package io.github.antonioimbesi.pulse.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * Collects a [Flow] in a lifecycle-aware manner within Jetpack Compose.
 *
 * This extension is specifically designed for safely collecting MVI side effects in the UI layer.
 * It ensures effects are only processed when the lifecycle is in an appropriate state,
 * preventing crashes from performing UI operations (navigation, toasts) when the
 * UI is stopped or destroyed.
 *
 * **Usage:**
 * ```kotlin
 * @Composable
 * fun LoginScreen(viewModel: LoginViewModel) {
 *     viewModel.sideEffect.collectAsEffectWithLifecycle { effect ->
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
 * @param block The suspend function invoked for each emitted value. Runs on [Dispatchers.Main.immediate].
 *
 * @see repeatOnLifecycle The underlying lifecycle API that handles collection suspension
 */
@Suppress("ComposableNaming")
@Composable
fun <T> Flow<T>.collectAsEffectWithLifecycle(
    vararg keys: Any?,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // LaunchedEffect restarts when lifecycle or keys change, ensuring proper cleanup
    LaunchedEffect(lifecycleOwner.lifecycle, *keys) {
        // repeatOnLifecycle suspends collection when below minActiveState
        // and resumes when the state is reached again
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            collect(block)
        }
    }
}
