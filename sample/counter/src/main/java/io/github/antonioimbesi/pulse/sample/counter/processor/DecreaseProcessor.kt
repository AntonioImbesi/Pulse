package io.github.antonioimbesi.pulse.sample.counter.processor

import io.github.antonioimbesi.pulse.core.processor.IntentionProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntention
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterState
import javax.inject.Inject

@Processor
class DecreaseProcessor @Inject constructor() :
    IntentionProcessor<CounterState, CounterIntention.Decrease, CounterSideEffect> {

    override suspend fun ProcessorScope<CounterState, CounterSideEffect>.process(
        intention: CounterIntention.Decrease
    ) {
        if (currentUiState.counter == 0U) {
            send(sideEffect = CounterSideEffect.BelowZero)
        } else {
            val decreasedCounter = currentUiState.counter - 1U
            reduce { copy(counter = decreasedCounter) }
        }
    }
}
