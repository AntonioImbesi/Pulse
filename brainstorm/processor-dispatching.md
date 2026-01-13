```kotlin
interface ProcessorContext<UiState, SideEffect> {
    // ... existing methods
    suspend fun dispatch(intention: Any) // NEW - internal dispatch
}

// Usage:
@Processor
class ComplexProcessor @Inject constructor(
    private val validationProcessor: ValidationProcessor
) : IntentionProcessor<State, ComplexIntention, Effect> {
    
    override suspend fun ProcessorContext<State, Effect>.process(
        intention: ComplexIntention
    ) {
        // Delegate validation
        dispatch(ValidationIntention.Validate(intention.data))
        
        // Continue with complex logic
        reduce { /* ... */ }
    }
}
```