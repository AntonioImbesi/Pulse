```kotlin
interface ProcessorContext<State, SideEffect> {
    // ... existing methods
    suspend fun dispatch(intent: Any) // NEW - internal dispatch
}

// Usage:
@Processor
class ComplexProcessor @Inject constructor(
    private val validationProcessor: ValidationProcessor
) : IntentProcessor<State, ComplexIntent, Effect> {
    
    override suspend fun ProcessorContext<State, Effect>.process(
        intent: ComplexIntent
    ) {
        // Delegate validation
        dispatch(ValidationIntent.Validate(intent.data))
        
        // Continue with complex logic
        reduce { /* ... */ }
    }
}
```