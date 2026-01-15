package io.github.antonioimbesi.pulse.sample.counter.processor

import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntent
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterState
import javax.inject.Inject

@Processor
class DecreaseProcessor @Inject constructor() :
    IntentProcessor<CounterState, CounterIntent.Decrease, CounterSideEffect> {

    override suspend fun ProcessorScope<CounterState, CounterSideEffect>.process(
        intent: CounterIntent.Decrease
    ) {
        if (currentState.counter == 0U) {
            send(sideEffect = CounterSideEffect.BelowZero)
        } else {
            val decreasedCounter = currentState.counter - 1U
            reduce { copy(counter = decreasedCounter) }
        }
    }
}
