```kotlin
class TestMviEngine<UiState, Intention : Any, SideEffect>(
    initialState: UiState,
    processorExecutor: ProcessorExecutor<UiState, Intention, SideEffect>
) : MviEngine<UiState, Intention, SideEffect> {
    
    private val stateHistory = mutableListOf(initialState)
    private val sideEffectHistory = mutableListOf<SideEffect>()
    
    val states: List<UiState> get() = stateHistory.toList()
    val sideEffects: List<SideEffect> get() = sideEffectHistory.toList()
    
    suspend fun dispatchAndWait(intention: Intention) {
        // Dispatch and wait for processing to complete
    }
    
    fun assertStateAt(index: Int, predicate: (UiState) -> Boolean) {
        // Assertion helpers
    }
}
```