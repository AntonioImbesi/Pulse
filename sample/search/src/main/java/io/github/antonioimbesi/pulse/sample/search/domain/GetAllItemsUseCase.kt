package io.github.antonioimbesi.pulse.sample.search.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetAllItemsUseCase @Inject constructor() {
    operator fun invoke(): Flow<List<Item>> =
        // Simulate pagination
        flow {
            allItems.chunked(10).forEach { chunk ->
                delay(timeMillis = 1_500)
                emit(value = chunk)
            }
        }
}
