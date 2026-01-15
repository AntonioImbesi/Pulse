package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import kotlinx.coroutines.test.TestScope

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
