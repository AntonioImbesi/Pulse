package io.github.antonioimbesi.pulse.sample.counter.processor

import io.github.antonioimbesi.pulse.core.processor.IntentionProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntention
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterState
import javax.inject.Inject

@Processor
class IncreaseProcessor @Inject constructor() : IntentionProcessor<CounterState, CounterIntention.Increase, CounterSideEffect> {

    override suspend fun ProcessorScope<CounterState, CounterSideEffect>.process(
        intention: CounterIntention.Increase
    ) {
        val increasedCounter = currentUiState.counter + 1U
        reduce { copy(counter = increasedCounter) }
    }
}
