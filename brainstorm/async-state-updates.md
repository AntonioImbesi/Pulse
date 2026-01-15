```kotlin
interface ProcessorContext<State, SideEffect> {
    suspend fun <T> async(
        block: suspend () -> T,
        onStart: (State.() -> State)? = null,
        onSuccess: (State.(T) -> State)? = null,
        onError: (State.(Throwable) -> State)? = null
    ): Result<T>
}

// Implementation:
override suspend fun <T> async(
    block: suspend () -> T,
    onStart: (State.() -> State)?,
    onSuccess: (State.(T) -> State)?,
    onError: (State.(Throwable) -> State)?
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