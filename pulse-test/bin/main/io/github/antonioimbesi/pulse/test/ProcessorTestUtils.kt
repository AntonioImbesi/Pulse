package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.core.processor.InitProcessor
import io.github.antonioimbesi.pulse.core.processor.InitProcessorScope
import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope

// ============================
// IntentProcessor Test Functions
// ============================

/**
 * DSL entry point for processor testing.
 */
suspend fun <State, Intent, SideEffect> testProcessor(
    initialState: State,
    processor: IntentProcessor<State, Intent, SideEffect>,
    intent: Intent,
    assertions: ProcessorAssertions<State, SideEffect>.() -> Unit
) {
    val testScope = ProcessorTestScope<State, SideEffect>(initialState)

    with(processor) {
        testScope.process(intent)
    }

    ProcessorAssertions(testScope).assertions()
}

/**
 * Testing with async support using TestScope.
 */
suspend fun <State, Intent, SideEffect> TestScope.testProcessorAsync(
    initialState: State,
    processor: IntentProcessor<State, Intent, SideEffect>,
    intent: Intent,
    assertions: suspend ProcessorAssertions<State, SideEffect>.() -> Unit
) {
    val testScope = ProcessorTestScope<State, SideEffect>(initialState, this)

    try {
        with(processor) {
            testScope.process(intent)
        }

        ProcessorAssertions(testScope).assertions()
    } finally {
        testScope.cancelAllJobs()
    }
}

/**
 * Test a processor that handles flows with fine-grained control.
 */
suspend fun <State, Intent, SideEffect> TestScope.testProcessorWithFlows(
    initialState: State,
    processor: IntentProcessor<State, Intent, SideEffect>,
    intent: Intent,
    test: suspend AdvancedProcessorTestScope<State, SideEffect>.() -> Unit
) {
    val advancedScope = AdvancedProcessorTestScope<State, SideEffect>(initialState, this)

    try {
        with(processor) {
            advancedScope.getScope().process(intent)
        }

        advancedScope.test()
    } finally {
        advancedScope.cleanup()
    }
}

// ============================
// InitProcessor Test Overloads
// ============================

/**
 * DSL entry point for init processor testing (simple, non-async).
 *
 * Example:
 * ```kotlin
 * @Test
 * fun `test init processor completes without errors`() = runTest {
 *     val processor = InitMusicProcessor(FakeMusicPlayer())
 *
 *     testProcessor(
 *         initialState = MusicState(isPlaying = false),
 *         processor = processor
 *     ) {
 *         noStateChanges()
 *         noSideEffects()
 *     }
 * }
 * ```
 */
suspend fun <State, SideEffect> testProcessor(
    initialState: State,
    processor: InitProcessor<State, SideEffect>,
    assertions: ProcessorAssertions<State, SideEffect>.() -> Unit
) {
    val testScope = ProcessorTestScope<State, SideEffect>(initialState)
    val stateFlow = MutableStateFlow(initialState)

    // Simple init processor scope without coroutine support
    val initProcessorScope = object : InitProcessorScope<State, SideEffect> {
        override val currentState: State get() = testScope.currentState
        override fun reduce(reducer: State.() -> State) = testScope.reduce(reducer)
        override fun send(sideEffect: SideEffect) = testScope.send(sideEffect)

        override val coroutineScope: CoroutineScope
            get() = throw UnsupportedOperationException(
                "Use testProcessorAsync with TestScope for coroutine operations"
            )

        override val stateFlow: StateFlow<State>
            get() = stateFlow
    }

    with(processor) {
        initProcessorScope.init()
    }

    ProcessorAssertions(testScope).assertions()
}

/**
 * Testing init processor with async support using TestScope.
 *
 * Example:
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
 *         // Verify timer behavior
 *         // ...
 *     }
 * }
 * ```
 */
suspend fun <State, SideEffect> TestScope.testProcessorAsync(
    initialState: State,
    processor: InitProcessor<State, SideEffect>,
    assertions: suspend ProcessorAssertions<State, SideEffect>.() -> Unit
) {
    val testScope = ProcessorTestScope<State, SideEffect>(initialState, this)
    val stateFlow = MutableStateFlow(initialState)

    // Track state changes to keep stateFlow in sync
    val syncedTestScope = object : ProcessorScope<State, SideEffect> {
        override val currentState: State get() = testScope.currentState

        override fun reduce(reducer: State.() -> State) {
            testScope.reduce(reducer)
            stateFlow.value = testScope.currentState
        }

        override fun send(sideEffect: SideEffect) = testScope.send(sideEffect)
    }

    val initProcessorScope = object : InitProcessorScope<State, SideEffect> {
        override val currentState: State get() = syncedTestScope.currentState
        override fun reduce(reducer: State.() -> State) = syncedTestScope.reduce(reducer)
        override fun send(sideEffect: SideEffect) = syncedTestScope.send(sideEffect)

        override val coroutineScope: CoroutineScope
            get() = this@testProcessorAsync

        override val stateFlow: StateFlow<State>
            get() = stateFlow
    }

    try {
        with(processor) {
            initProcessorScope.init()
        }

        ProcessorAssertions(testScope).assertions()
    } finally {
        testScope.cancelAllJobs()
    }
}

/**
 * Test an init processor with fine-grained control over time and state.
 *
 * Example:
 * ```kotlin
 * @Test
 * fun `test timer advances with time`() = runTest {
 *     val processor = InitMusicProcessor(FakeMusicPlayer())
 *
 *     testProcessorWithFlows(
 *         initialState = MusicState(isPlaying = true),
 *         processor = processor
 *     ) {
 *         // Advance time
 *         advanceTimeBy(3000)
 *
 *         // Check state
 *         assertThat(finalState.playbackDuration).isEqualTo(3)
 *     }
 * }
 * ```
 */
suspend fun <State, SideEffect> TestScope.testProcessorWithFlows(
    initialState: State,
    processor: InitProcessor<State, SideEffect>,
    test: suspend AdvancedProcessorTestScope<State, SideEffect>.() -> Unit
) {
    val advancedScope = AdvancedProcessorTestScope<State, SideEffect>(initialState, this)
    val stateFlow = MutableStateFlow(initialState)

    // Keep state flow in sync
    val originalScope = advancedScope.getScope()
    val syncedScope = object : ProcessorScope<State, SideEffect> {
        override val currentState: State get() = originalScope.currentState

        override fun reduce(reducer: State.() -> State) {
            originalScope.reduce(reducer)
            stateFlow.value = originalScope.currentState
        }

        override fun send(sideEffect: SideEffect) = originalScope.send(sideEffect)
    }

    val initProcessorScope = object : InitProcessorScope<State, SideEffect> {
        override val currentState: State get() = syncedScope.currentState
        override fun reduce(reducer: State.() -> State) = syncedScope.reduce(reducer)
        override fun send(sideEffect: SideEffect) = syncedScope.send(sideEffect)

        override val coroutineScope: CoroutineScope
            get() = this@testProcessorWithFlows

        override val stateFlow: StateFlow<State>
            get() = stateFlow
    }

    try {
        with(processor) {
            initProcessorScope.init()
        }

        advancedScope.test()
    } finally {
        advancedScope.cleanup()
    }
}