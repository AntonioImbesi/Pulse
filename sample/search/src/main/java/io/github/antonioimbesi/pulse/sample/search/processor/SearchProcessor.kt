package io.github.antonioimbesi.pulse.sample.search.processor

import io.github.antonioimbesi.pulse.core.processor.IntentionProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import io.github.antonioimbesi.pulse.sample.search.contract.SearchIntention
import io.github.antonioimbesi.pulse.sample.search.contract.SearchState
import io.github.antonioimbesi.pulse.sample.search.contract.SearchState.ResultState
import io.github.antonioimbesi.pulse.sample.search.domain.GetAllItemsUseCase
import io.github.antonioimbesi.pulse.sample.search.domain.GetFilteredItemsUseCase
import io.github.antonioimbesi.pulse.sample.search.domain.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.launch
import javax.inject.Inject

@Processor
class SearchProcessor @Inject constructor(
    private val getAllItemsUseCase: GetAllItemsUseCase,
    private val getFilteredItemsUseCase: GetFilteredItemsUseCase
) : IntentionProcessor<SearchState, SearchIntention.Search, Unit> {

    private val processorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var searchJob: Job? = null

    @OptIn(FlowPreview::class)
    override suspend fun ProcessorScope<SearchState, Unit>.process(
        intention: SearchIntention.Search
    ) {
        reduce { copy(query = intention.query, results = ResultState.Loading) }

        searchJob?.cancel()
        searchJob = processorScope.launch {
            delay(500L) // Debounce
            executeSearch(intention.query)
        }
    }

    private suspend fun ProcessorScope<SearchState, Unit>.executeSearch(query: String) {
        val searchFlow = if (query.isEmpty()) {
            getAllItemsUseCase()
        } else {
            getFilteredItemsUseCase(query)
        }

        searchFlow
            .scan(emptyList<Item>()) { accumulator, newItems -> accumulator + newItems }
            .collect { results ->
                if (results.isNotEmpty()) {
                    reduce {
                        copy(results = ResultState.HasResults(results, isPagingNewResults = true))
                    }
                }
            }

        reduce {
            when (val current = results) {
                is ResultState.HasResults -> copy(results = current.copy(isPagingNewResults = false))
                else -> copy(results = ResultState.NoResults)
            }
        }
    }
}
