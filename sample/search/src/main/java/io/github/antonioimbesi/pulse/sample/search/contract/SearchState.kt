package io.github.antonioimbesi.pulse.sample.search.contract

import io.github.antonioimbesi.pulse.sample.search.domain.Item

data class SearchState(
    val query: String = "",
    val results: ResultState = ResultState.Loading,
) {
    sealed interface ResultState {
        data object Loading : ResultState
        data object NoResults : ResultState
        data class HasResults(
            val results: List<Item>,
            val isPagingNewResults: Boolean = false
        ) : ResultState
    }
}
