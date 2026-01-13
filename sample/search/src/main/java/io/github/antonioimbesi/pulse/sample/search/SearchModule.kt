package io.github.antonioimbesi.pulse.sample.search

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.core.engine.DefaultMviEngine
import io.github.antonioimbesi.pulse.sample.search.contract.SearchIntention
import io.github.antonioimbesi.pulse.sample.search.contract.SearchState
import io.github.antonioimbesi.pulse.sample.search.contract.generated.SearchIntentionProcessorExecutor

@Module
@InstallIn(ViewModelComponent::class)
object SearchModule {

    @Provides
    internal fun provideMviEngineFactory(
        processorExecutor: SearchIntentionProcessorExecutor
    ): MviEngineFactory<SearchState, SearchIntention, Unit> =
        MviEngineFactory { coroutineScope, initialState ->
            DefaultMviEngine(
                initialState = initialState,
                processorExecutor = processorExecutor,
                coroutineScope = coroutineScope,
            )
        }
}
