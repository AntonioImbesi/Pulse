package io.github.antonioimbesi.pulse.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

@Suppress("ComposableNaming")
@Composable
fun <T> Flow<T>.collectAsEffectWithLifecycle(
    vararg keys: Any?,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    block: suspend (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner.lifecycle, *keys) {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            collect(block)
        }
    }
}
