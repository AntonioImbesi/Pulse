package io.github.antonioimbesi.pulse.sample.counter

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntention
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterState
import io.github.antonioimbesi.pulse.sample.counter.contract.generated.CounterIntentionProcessorExecutor

@Module
@InstallIn(ViewModelComponent::class)
object CounterModule {

    @Provides
    internal fun provideMviEngineFactory(
        processorExecutor: CounterIntentionProcessorExecutor
    ): MviEngineFactory<CounterState, CounterIntention, CounterSideEffect> =
        CounterMviEngineFactory(processorExecutor)
}
