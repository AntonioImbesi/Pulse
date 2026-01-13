package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.core.processor.IntentionProcessor
import kotlinx.coroutines.test.TestScope

/**
 * DSL entry point for processor testing.
 */
suspend fun <UiState, Intention, SideEffect> testProcessor(
    initialState: UiState,
    processor: IntentionProcessor<UiState, Intention, SideEffect>,
    intention: Intention,
    assertions: ProcessorAssertions<UiState, SideEffect>.() -> Unit
) {
    val testScope = ProcessorTestScope<UiState, SideEffect>(initialState)

    with(processor) {
        testScope.process(intention)
    }

    ProcessorAssertions(testScope).assertions()
}

/**
 * Testing with async support using TestScope.
 */
suspend fun <UiState, Intention, SideEffect> TestScope.testProcessorAsync(
    initialState: UiState,
    processor: IntentionProcessor<UiState, Intention, SideEffect>,
    intention: Intention,
    assertions: suspend ProcessorAssertions<UiState, SideEffect>.() -> Unit
) {
    val testScope = ProcessorTestScope<UiState, SideEffect>(initialState, this)

    try {
        with(processor) {
            testScope.process(intention)
        }

        ProcessorAssertions(testScope).assertions()
    } finally {
        testScope.cancelAllJobs()
    }
}

/**
 * Test a processor that handles flows with fine-grained control.
 */
suspend fun <UiState, Intention, SideEffect> TestScope.testProcessorWithFlows(
    initialState: UiState,
    processor: IntentionProcessor<UiState, Intention, SideEffect>,
    intention: Intention,
    test: suspend AdvancedProcessorTestScope<UiState, SideEffect>.() -> Unit
) {
    val advancedScope = AdvancedProcessorTestScope<UiState, SideEffect>(initialState, this)

    try {
        with(processor) {
            advancedScope.getScope().process(intention)
        }

        advancedScope.test()
    } finally {
        advancedScope.cleanup()
    }
}
