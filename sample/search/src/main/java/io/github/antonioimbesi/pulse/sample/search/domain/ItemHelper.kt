package io.github.antonioimbesi.pulse.sample.search.domain

val allItems = List(100) { index ->
    Item(
        id = index,
        title = "Item $index",
        description = "Description for item $index with ${
            listOf(
                "Android",
                "Kotlin",
                "Compose",
                "MVI"
            ).random()
        }"
    )
}