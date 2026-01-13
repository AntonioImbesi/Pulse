package io.github.antonioimbesi.pulse.sample.search.contract

sealed interface SearchIntention {
    data class Search(val query: String = "") : SearchIntention
}
