# Pulse MVI Testing Guide

## Overview

Soon...

## Key Features

✅ **Order Preservation**: Tracks state changes and side effects in execution order  
✅ **Zero Boilerplate**: Fluent DSL for readable tests  
✅ **Async Support**: Full support for coroutines, delays, and flows  
✅ **SOLID Principles**: Each component has a single responsibility  
✅ **Flexible Assertions**: Multiple ways to verify behavior

---

## Core Concepts

### ProcessorEvent

Every action in a processor generates an event:

```kotlin
sealed interface ProcessorEvent<out UiState, out SideEffect> {
    data class StateChange<UiState>(val oldState: UiState, val newState: UiState)
    data class SideEffectEmitted<SideEffect>(val sideEffect: SideEffect)
}
```

Events are captured in the order they execute, giving you complete visibility into processor behavior.

---

## Basic Testing Patterns

### 1. Simple State Transitions

```kotlin
@Test
fun `increment counter`() = runTest {
    testProcessor(
        initialState = CounterState(0),
        processor = IncrementProcessor(),
        intention = Increment
    ) {
        finalState(CounterState(1))
        noSideEffects()
    }
}
```

### 2. Verify Exact Event Sequence

```kotlin
@Test
fun `login flow - verify order`() = runTest {
    testProcessor(
        initialState = LoginState.Idle,
        processor = LoginProcessor(mockRepo),
        intention = Login("user", "pass")
    ) {
        expectEvents {
            state(LoginState.Loading)           // First: loading state
            state(LoginState.Success("id"))     // Then: success state
            sideEffect(NavigateToHome("id"))    // Finally: navigation effect
        }
    }
}
```

### 3. Verify Side Effect Comes Before State

```kotlin
@Test
fun `logout - effect before state`() = runTest {
    testProcessor(
        initialState = LoggedInState,
        processor = LogoutProcessor(),
        intention = Logout
    ) {
        expectEvents {
            sideEffect(ClearUserData)      // Side effect first
            sideEffect(NavigateToLogin)    // Another side effect
            state(LoggedOutState)          // State change last
        }
    }
}
```

---

## Advanced Patterns

### Testing Async Operations

```kotlin
@Test
fun `async operation with delays`() = runTest {
    testProcessorAsync(
        initialState = DataState.Idle,
        processor = FetchDataProcessor(api),
        intention = FetchData
    ) {
        expectEvents {
            state(DataState.Loading)
            state(DataState.Success(data))
            sideEffect(DataFetchedEffect)
        }
        // runTest automatically handles delays
    }
}
```

### Testing Flow Processors

```kotlin
@Test
fun `processor collecting from flow`() = runTest {
    val dataFlow = flowOf("item1", "item2", "item3")
    
    testProcessorWithFlows(
        initialState = StreamState(),
        processor = StreamProcessor(dataFlow),
        intention = StartStream
    ) {
        advanceUntilIdle()
        
        // Each emission creates: state change + effect
        expectEvents {
            state { it.items.contains("item1") }
            sideEffect(ItemReceived("item1"))
            state { it.items.contains("item2") }
            sideEffect(ItemReceived("item2"))
            state { it.items.contains("item3") }
            sideEffect(ItemReceived("item3"))
        }
    }
}
```

### Time-Based Testing

```kotlin
@Test
fun `debounced search`() = runTest {
    testProcessorWithFlows(
        initialState = SearchState(""),
        processor = SearchProcessor(),
        intention = TypeSearch("query")
    ) {
        // No results immediately
        assertEquals(0, eventCount())
        
        // Advance past debounce period
        advanceTimeBy(500)
        
        // Now events should occur
        assertTrue(eventCount() > 0)
        finalState(SearchState("query", results = listOf(...)))
    }
}
```

---

## Assertion Methods

### Event-Level Assertions

```kotlin
expectEvents {
    state(expectedState)                    // Exact state match
    state { state -> /* custom check */ }   // Custom assertion
    sideEffect(expectedEffect)              // Exact effect match
    sideEffect { effect -> /* check */ }    // Custom assertion
}
```

### State-Only Assertions

```kotlin
expectStates(state1, state2, state3)  // Verify state sequence only
noStateChanges()                       // Assert no state changes occurred
```

### Side Effect-Only Assertions

```kotlin
expectSideEffects(effect1, effect2)   // Verify effect sequence only
noSideEffects()                        // Assert no effects emitted
```

### Final State

```kotlin
finalState(expectedState)  // Verify the end result
```

### Event Count

```kotlin
eventCount(5)  // Verify total number of events
```

### Custom Assertions

```kotlin
assertEvents { events ->
    // Full access to event list
    assertTrue(events.size > 2)
    assertTrue(events.first() is ProcessorEvent.StateChange)
}
```

---

## Best Practices

### ✅ DO: Test Event Order When It Matters

```kotlin
// Good: State updates before navigation
expectEvents {
    state(ProfileUpdated)
    sideEffect(NavigateBack)
}
```

### ✅ DO: Test Async Operations with TestScope

```kotlin
// Good: Use testProcessorAsync for delays/flows
testProcessorAsync(...) {
    advanceUntilIdle()
    // assertions
}
```

### ✅ DO: Verify Final State for Simple Cases

```kotlin
// Good: Simple state change
testProcessor(...) {
    finalState(expectedState)
}
```

### ❌ DON'T: Over-specify Stable Flows

```kotlin
// Bad: Too rigid for a stable pattern
expectEvents {
    state(State1)
    state(State2)
    state(State3)
    state(State4)
}
```

### ❌ DON'T: Ignore Side Effect Order

```kotlin
// Bad: Order might matter!
expectSideEffects(effect1, effect2)  // Is this the right order?

// Better: Use expectEvents to verify order
expectEvents {
    sideEffect(effect1)
    sideEffect(effect2)
}
```

### ❌ DON'T: Test Implementation Details

```kotlin
// Bad: Testing internal state that might change
expectEvents {
    state { it.internalLoadingFlag == true }
    // ...
}

// Better: Test observable behavior
expectEvents {
    state { it is LoadingState }
    // ...
}
```

---

## Common Scenarios

### Scenario: Multi-Step Form Validation

```kotlin
@Test
fun `form validation - progressive feedback`() = runTest {
    testProcessor(
        initialState = FormState(),
        processor = ValidateFormProcessor(),
        intention = SubmitForm(data)
    ) {
        expectEvents {
            state(FormState(validating = true))
            sideEffect(ShowValidationSpinner)
            state(FormState(emailValid = true, validating = true))
            state(FormState(emailValid = true, phoneValid = true, validating = true))
            state(FormState(emailValid = true, phoneValid = true, allValid = true))
            sideEffect(HideValidationSpinner)
            sideEffect(NavigateToNextScreen)
        }
    }
}
```

### Scenario: Error Recovery

```kotlin
@Test
fun `retry after failure`() = runTest {
    var attemptCount = 0
    val processor = RetryProcessor { 
        if (++attemptCount < 3) throw Exception("Fail")
        else Result.Success
    }
    
    testProcessorAsync(initialState, processor, intention) {
        advanceUntilIdle()
        
        expectEvents {
            state(AttemptState(1))
            state(ErrorState)
            state(AttemptState(2))
            state(ErrorState)
            state(AttemptState(3))
            state(SuccessState)
            sideEffect(ShowSuccess)
        }
    }
}
```

### Scenario: Coordinated Effects

```kotlin
@Test
fun `multiple services notified in order`() = runTest {
    testProcessor(
        initialState = CheckoutState(),
        processor = CompleteCheckoutProcessor(),
        intention = Checkout
    ) {
        expectEvents {
            state(ProcessingPayment)
            sideEffect(ChargeCard)           // Must happen first
            state(PaymentSuccessful)
            sideEffect(UpdateInventory)      // Then update inventory
            sideEffect(SendConfirmationEmail) // Then notify user
            sideEffect(TriggerAnalytics)     // Finally analytics
            state(CheckoutComplete)
        }
    }
}
```

---

## Integration with CI/CD

These tests work seamlessly in CI/CD pipelines:

```kotlin
// Tests run deterministically with virtual time
@Test
fun `long running operation`() = runTest {
    testProcessorAsync(...) {
        advanceTimeBy(60_000) // Advance 1 minute instantly
        // assertions
    }
}
```

No flakiness from real timing - tests complete in milliseconds.

---

## Migration Guide

If you have existing MVI tests:

### Before (Manual Verification)
```kotlin
@Test
fun test() = runTest {
    val states = mutableListOf<State>()
    val effects = mutableListOf<Effect>()
    
    val processor = MyProcessor()
    // Manual tracking...
    
    assertEquals(expectedState, states.last())
    assertEquals(expectedEffect, effects.last())
}
```

### After (Testing Framework)
```kotlin
@Test
fun test() = runTest {
    testProcessor(initialState, processor, intention) {
        finalState(expectedState)
        expectSideEffects(expectedEffect)
    }
}
```

---

## Performance Considerations

- **Memory**: Events are stored in a list - fine for most tests (typically < 100 events)
- **Speed**: Overhead is negligible - tests run as fast as the processor code itself
- **Cleanup**: `testProcessorAsync` automatically cleans up coroutines

---

## Roadmap

Soon...