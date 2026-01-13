```kotlin
interface ProcessorContext<UiState, SideEffect> {
    suspend fun <T> async(
        block: suspend () -> T,
        onStart: (UiState.() -> UiState)? = null,
        onSuccess: (UiState.(T) -> UiState)? = null,
        onError: (UiState.(Throwable) -> UiState)? = null
    ): Result<T>
}

// Implementation:
override suspend fun <T> async(
    block: suspend () -> T,
    onStart: (UiState.() -> UiState)?,
    onSuccess: (UiState.(T) -> UiState)?,
    onError: (UiState.(Throwable) -> UiState)?
): Result<T> {
    onStart?.let { reduce(it) }
    return try {
        val result = block()
        onSuccess?.let { reduce { it(result) } }
        Result.success(result)
    } catch (e: Throwable) {
        onError?.let { reduce { it(e) } }
        Result.failure(e)
    }
}
```