```kotlin
class TestMviEngine<State, Intent : Any, SideEffect>(
    initialState: State,
    processorExecutor: ProcessorExecutor<State, Intent, SideEffect>
) : MviEngine<State, Intent, SideEffect> {
    
    private val stateHistory = mutableListOf(initialState)
    private val sideEffectHistory = mutableListOf<SideEffect>()
    
    val states: List<State> get() = stateHistory.toList()
    val sideEffects: List<SideEffect> get() = sideEffectHistory.toList()
    
    suspend fun dispatchAndWait(intent: Intent) {
        // Dispatch and wait for processing to complete
    }
    
    fun assertStateAt(index: Int, predicate: (State) -> Boolean) {
        // Assertion helpers
    }
}
```