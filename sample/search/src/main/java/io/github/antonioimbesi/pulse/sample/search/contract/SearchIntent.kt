package io.github.antonioimbesi.pulse.sample.search.contract

sealed interface SearchIntent {
    data class Search(val query: String = "") : SearchIntent
}
