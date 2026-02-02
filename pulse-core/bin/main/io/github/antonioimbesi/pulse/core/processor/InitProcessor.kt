package io.github.antonioimbesi.pulse.core.processor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Processor that runs once when the engine is created, before any user intents are processed.
 *
 * Use this for:
 * - Setting up long-lived reactive flows based on state
 * - Initializing resources that should exist for the engine's lifetime
 * - Starting background tasks (timers, polling, WebSocket connections)
 *
 * **Naming Convention:** Classes implementing this interface should be named `Init<Feature>Processor`
 * (e.g., `InitMusicProcessor`, `InitLoginProcessor`, `InitChatProcessor`)
 *
 * **Example - Music player with timer:**
 * ```kotlin
 * @Processor
 * class InitMusicProcessor(
 *     private val musicPlayer: MusicPlayer
 * ) : InitProcessor<MusicState, MusicEffect> {
 *     override suspend fun InitProcessorScope<MusicState, MusicEffect>.init() {
 *         // Timer that runs only while playing
 *         launchWhenState({ it.isPlaying }) {
 *             var elapsed = currentState.playbackDuration
 *             while (true) {
 *                 delay(1000)
 *                 reduce { copy(playbackDuration = ++elapsed) }
 *             }
 *         }
 *
 *         // Music position updates
 *         launchWhenState({ it.isPlaying }) {
 *             musicPlayer.positionFlow().collect { position ->
 *                 reduce { copy(currentPosition = position) }
 *             }
 *         }
 *
 *         // React to volume changes
 *         collectState({ it.volume }) { volume ->
 *             musicPlayer.setVolume(volume)
 *         }
 *     }
 * }
 * ```
 *
 * **Testing:**
 * ```kotlin
 * @Test
 * fun `test timer runs when playing`() = runTest {
 *     val processor = InitMusicProcessor(FakeMusicPlayer())
 *
 *     testProcessorAsync(
 *         initialState = MusicState(isPlaying = false),
 *         processor = processor
 *     ) {
 *         // Set playing to true
 *         getScope().reduce { copy(isPlaying = true) }
 *
 *         // Advance time
 *         advanceTimeBy(3000)
 *
 *         // Verify timer ticked 3 times
 *         assertThat(finalState.playbackDuration).isEqualTo(3)
 *     }
 * }
 * ```
 *
 * @param State The immutable state type this processor can read and update.
 * @param SideEffect The side effect type this processor can emit.
 *
 * @see Processor Annotation to enable KSP code generation
 * @see ProcessorScope The scope providing state access and mutation capabilities
 * @see InitProcessorScope The scope with additional engine-lifetime operations
 */
fun interface InitProcessor<State, SideEffect> {
    /**
     * Called once when the engine starts, before any user intents.
     *
     * @receiver The [InitProcessorScope] providing access to state, mutations, and engine-lifetime operations.
     */
    suspend fun InitProcessorScope<State, SideEffect>.init()
}

/**
 * Scope for initialization-time operations with access to engine-lifetime resources.
 *
 * Extends [ProcessorScope] to provide:
 * - All standard processor operations ([currentState], [reduce], [send])
 * - Engine's coroutine scope for launching long-lived jobs
 * - State flow for reactive programming
 *
 * All operations launched through this scope live for the engine's lifetime
 * and are automatically cancelled when the engine is destroyed.
 */
interface InitProcessorScope<State, SideEffect> : ProcessorScope<State, SideEffect> {
    /**
     * The engine's coroutine scope.
     * Jobs launched here live until the engine is destroyed.
     */
    val coroutineScope: CoroutineScope

    /**
     * The engine's state flow for reactive programming.
     */
    val stateFlow: StateFlow<State>
}

/**
 * Launches a coroutine that runs only when a state condition is true.
 *
 * The block is automatically cancelled when the condition becomes false
 * and restarted when it becomes true again.
 *
 * **Example - Timer that runs only while playing:**
 * ```kotlin
 * launchWhenState({ it.isPlaying }) {
 *     var elapsed = currentState.playbackDuration
 *     while (true) {
 *         delay(1000)
 *         reduce { copy(playbackDuration = ++elapsed) }
 *     }
 * }
 * ```
 *
 * @param predicate Determines when the block should run
 * @param block Code to execute while predicate is true
 */
fun <State, SideEffect> InitProcessorScope<State, SideEffect>.launchWhenState(
    predicate: (State) -> Boolean,
    block: suspend CoroutineScope.() -> Unit
) {
    coroutineScope.launch {
        stateFlow
            .map(predicate)
            .distinctUntilChanged()
            .collectLatest { isActive ->
                if (isActive) {
                    block()
                }
            }
    }
}

/**
 * Launches a coroutine that reacts to state property changes.
 *
 * The block is called with each distinct value of the selected property.
 *
 * **Example - React to volume changes:**
 * ```kotlin
 * collectState({ it.volume }) { volume ->
 *     musicPlayer.setVolume(volume)
 * }
 * ```
 *
 * @param selector Extracts the value to observe from state
 * @param block Code to execute for each distinct selected value
 */
fun <State, SideEffect, T> InitProcessorScope<State, SideEffect>.collectState(
    selector: (State) -> T,
    block: suspend (T) -> Unit
) {
    coroutineScope.launch {
        stateFlow
            .map(selector)
            .distinctUntilChanged()
            .collect(block)
    }
}
