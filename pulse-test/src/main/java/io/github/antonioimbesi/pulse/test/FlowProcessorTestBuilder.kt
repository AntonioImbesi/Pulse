package io.github.antonioimbesi.pulse.test

import io.github.antonioimbesi.pulse.core.processor.IntentionProcessor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle

/**
 * Test builder for processors that emit flows.
 */
class FlowProcessorTestBuilder<UiState, Intention: Any, SideEffect, T>(
    private val testScope: TestScope,
    private val initialState: UiState,
    private val flow: Flow<T>
) {
    private lateinit var processor: IntentionProcessor<UiState, Intention, SideEffect>
    private lateinit var intention: Intention
    
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun whenProcessing(
        processor: IntentionProcessor<UiState, Intention, SideEffect>,
        intention: Intention,
        test: suspend FlowTestAssertions<UiState, SideEffect, T>.() -> Unit
    ) {
        this.processor = processor
        this.intention = intention
        
        val advancedScope = AdvancedProcessorTestScope<UiState, SideEffect>(initialState, testScope)
        val flowValues = mutableListOf<T>()
        
        try {
            // Collect flow values
            val flowJob = testScope.backgroundScope.launch {
                flow.toList(flowValues)
            }
            
            // Process intention
            with(processor) {
                advancedScope.getScope().process(intention)
            }
            
            testScope.advanceUntilIdle()
            flowJob.cancel()
            
            FlowTestAssertions(advancedScope, flowValues).test()
        } finally {
            advancedScope.cleanup()
        }
    }
}
