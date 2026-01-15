package io.github.antonioimbesi.pulse.sample.counter

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterIntent
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterSideEffect
import io.github.antonioimbesi.pulse.sample.counter.contract.CounterState
import io.github.antonioimbesi.pulse.sample.counter.contract.generated.CounterIntentProcessorExecutor

@Module
@InstallIn(ViewModelComponent::class)
object CounterModule {

    @Provides
    internal fun provideMviEngineFactory(
        processorExecutor: CounterIntentProcessorExecutor
    ): MviEngineFactory<CounterState, CounterIntent, CounterSideEffect> =
        CounterMviEngineFactory(processorExecutor)
}
