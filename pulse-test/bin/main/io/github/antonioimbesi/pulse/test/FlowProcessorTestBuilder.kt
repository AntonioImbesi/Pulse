package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Test builder for processors that emit flows.
 */
class FlowProcessorTestBuilder<State, Intent: Any, SideEffect, T>(
    private val testScope: TestScope,
    private val initialState: State,
    private val flow: Flow<T>
) {
    private lateinit var processor: IntentProcessor<State, Intent, SideEffect>
    private lateinit var intent: Intent
    
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun whenProcessing(
        processor: IntentProcessor<State, Intent, SideEffect>,
        intent: Intent,
        test: suspend FlowTestAssertions<State, SideEffect, T>.() -> Unit
    ) {
        this.processor = processor
        this.intent = intent
        
        val advancedScope = AdvancedProcessorTestScope<State, SideEffect>(initialState, testScope)
        val flowValues = mutableListOf<T>()
        
        try {
            // Collect flow values
            val flowJob = testScope.backgroundScope.launch {
                flow.toList(flowValues)
            }
            
            // Process intent
            with(processor) {
                advancedScope.getScope().process(intent)
            }
            
            testScope.advanceUntilIdle()
            flowJob.cancel()
            
            FlowTestAssertions(advancedScope, flowValues).test()
        } finally {
            advancedScope.cleanup()
        }
    }
}
