package io.github.antonioimbesi.pulse.sample.search.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetFilteredItemsUseCase @Inject constructor() {
    operator fun invoke(query: String): Flow<List<Item>> {
        val filteredItems = allItems.filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }

        // Simulate pagination
        return flow {
            filteredItems.chunked(10).forEach { chunk ->
                delay(50)
                emit(chunk)
            }
        }
    }
}
