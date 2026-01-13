package io.github.antonioimbesi.pulse.sample.search

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.antonioimbesi.pulse.android.MviViewModel
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import io.github.antonioimbesi.pulse.sample.search.contract.SearchIntention
import io.github.antonioimbesi.pulse.sample.search.contract.SearchState
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val engineFactory: MviEngineFactory<SearchState, SearchIntention, Unit>
) : MviViewModel<SearchState, SearchIntention, Unit>(
    engineFactory = engineFactory,
    initialState = SearchState(),
)
