# Pulse MVI Testing Guide

Comprehensive guide for testing Pulse MVI features.

## Overview

Pulse provides a dedicated testing framework (`pulse-test`) that makes testing processors straightforward and type-safe.

## Setup

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("io.github.antonioimbesi.pulse:pulse-test:$pulse_version")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$kotlinx_version")
    testImplementation("junit:junit:$junit_version")
}
```

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
sealed interface ProcessorEvent {
    data class StateChange(val oldState: State, val newState: State) : ProcessorEvent
    data class SideEffectEmitted(val sideEffect: SideEffect) : ProcessorEvent
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
        initialState = CounterState(count = 0),
        processor = IncrementProcessor(),
        intent = CounterIntent.Increment
    ) {
        finalState(CounterState(count = 1))
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
        processor = LoginProcessor(mockRepository),
        intent = LoginIntent.Login
    ) {
        expectEvents {
            state(LoginState.Loading)           // First: loading state
            state(LoginState.Success("id"))     // Then: success state
            sideEffect(LoginSideEffect.NavigateToHome("id")) // Finally: navigation effect
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
        intent = LogoutIntent.Logout
    ) {
        expectEvents {
            sideEffect(LogoutSideEffect.ClearUserData)  // Side effect first
            sideEffect(LogoutSideEffect.NavigateToLogin) // Another side effect
            state(LoggedOutState)                        // State change last
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
        processor = FetchDataProcessor(mockApi),
        intent = DataIntent.FetchData
    ) {
        expectEvents {
            state(DataState.Loading)
            state(DataState.Success(data))
            sideEffect(DataSideEffect.DataFetched)
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
        intent = StreamIntent.StartStream
    ) {
        advanceUntilIdle()
        
        // Each emission creates: state change + effect
        expectEvents {
            state { it.items.contains("item1") }
            sideEffect(StreamSideEffect.ItemReceived("item1"))
            state { it.items.contains("item2") }
            sideEffect(StreamSideEffect.ItemReceived("item2"))
            state { it.items.contains("item3") }
            sideEffect(StreamSideEffect.ItemReceived("item3"))
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
        intent = SearchIntent.TypeSearch("query")
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

**Example:**
```kotlin
@Test
fun `load data with custom checks`() = runTest {
    testProcessor(
        initialState = DataState(),
        processor = LoadDataProcessor(mockRepo),
        intent = DataIntent.Load
    ) {
        expectEvents {
            state { it.isLoading }                    // Custom: check loading flag
            state { it.data != null && it.data.isNotEmpty() } // Custom: verify data
            sideEffect { effect -> effect is DataSideEffect.Success } // Custom: effect type
        }
    }
}
```

### State-Only Assertions

```kotlin
expectStates(state1, state2, state3)  // Verify state sequence only
noStateChanges()                       // Assert no state changes occurred
```

**Example:**
```kotlin
@Test
fun `toggle state three times`() = runTest {
    testProcessor(
        initialState = ToggleState(enabled = false),
        processor = ToggleProcessor(),
        intent = ToggleIntent.Toggle
    ) {
        expectStates(
            ToggleState(enabled = true)
        )
    }
}
```

### Side Effect-Only Assertions

```kotlin
expectSideEffects(effect1, effect2)   // Verify effect sequence only
noSideEffects()                        // Assert no effects emitted
```

**Example:**
```kotlin
@Test
fun `delete triggers confirmation effects`() = runTest {
    testProcessor(
        initialState = DeleteState(),
        processor = DeleteItemProcessor(),
        intent = DeleteIntent.DeleteItem("123")
    ) {
        expectSideEffects(
            DeleteSideEffect.ShowConfirmation("123"),
            DeleteSideEffect.ItemDeleted
        )
    }
}
```

### Final State

```kotlin
finalState(expectedState)  // Verify the end result
```

**Example:**
```kotlin
@Test
fun `login sets token in state`() = runTest {
    testProcessor(
        initialState = LoginState(),
        processor = LoginProcessor(mockRepo),
        intent = LoginIntent.Login
    ) {
        finalState(LoginState(token = "abc123", isLoading = false))
    }
}
```

### Event Count

```kotlin
eventCount(5)  // Verify total number of events
```

**Example:**
```kotlin
@Test
fun `multi-step flow generates expected events`() = runTest {
    testProcessor(
        initialState = FormState(),
        processor = SubmitFormProcessor(mockRepo),
        intent = FormIntent.Submit
    ) {
        eventCount(4) // 2 state changes + 2 side effects
    }
}
```

### Custom Assertions

```kotlin
assertEvents { events ->
    // Full access to event list
    assertTrue(events.size > 2)
    assertTrue(events.first() is ProcessorEvent.StateChange)
}
```

**Example:**
```kotlin
@Test
fun `verify state changes before effects`() = runTest {
    testProcessor(
        initialState = State(),
        processor = MyProcessor(),
        intent = MyIntent
    ) {
        assertEvents { events ->
            val stateChanges = events.filterIsInstance<ProcessorEvent.StateChange>()
            val sideEffects = events.filterIsInstance<ProcessorEvent.SideEffectEmitted>()
            
            // All state changes happen before side effects
            assertTrue(stateChanges.isNotEmpty())
            assertTrue(sideEffects.isNotEmpty())
            
            val lastStateIndex = events.indexOfLast { it is ProcessorEvent.StateChange }
            val firstEffectIndex = events.indexOfFirst { it is ProcessorEvent.SideEffectEmitted }
            
            assertTrue(lastStateIndex < firstEffectIndex)
        }
    }
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

### ✅ DO: Use Custom Assertions for Complex Logic

```kotlin
// Good: Verify specific conditions
expectEvents {
    state { state ->
        state.items.size == 3 &&
        state.items.all { it.isValid }
    }
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

// Better: Test what matters
finalState(State4)
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

## Common Testing Scenarios

### Scenario 1: Multi-Step Form Validation

```kotlin
@Test
fun `form validation - progressive feedback`() = runTest {
    testProcessor(
        initialState = FormState(),
        processor = ValidateFormProcessor(),
        intent = FormIntent.SubmitForm(data)
    ) {
        expectEvents {
            state(FormState(validating = true))
            sideEffect(FormSideEffect.ShowValidationSpinner)
            state(FormState(emailValid = true, validating = true))
            state(FormState(emailValid = true, phoneValid = true, validating = true))
            state(FormState(emailValid = true, phoneValid = true, allValid = true))
            sideEffect(FormSideEffect.HideValidationSpinner)
            sideEffect(FormSideEffect.NavigateToNextScreen)
        }
    }
}
```

### Scenario 2: Error Recovery

```kotlin
@Test
fun `retry after failure`() = runTest {
    var attemptCount = 0
    val processor = RetryProcessor { 
        if (++attemptCount < 3) throw Exception("Fail")
        else Result.Success
    }
    
    testProcessorAsync(
        initialState = RetryState(),
        processor = processor,
        intent = RetryIntent.Start
    ) {
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

### Scenario 3: Coordinated Effects

```kotlin
@Test
fun `multiple services notified in order`() = runTest {
    testProcessor(
        initialState = CheckoutState(),
        processor = CompleteCheckoutProcessor(),
        intent = CheckoutIntent.Checkout
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

### Scenario 4: Loading States with Sealed Interface

```kotlin
@Test
fun `data loading transitions through correct states`() = runTest {
    testProcessor(
        initialState = DataState(data = DataState.DataStatus.Idle),
        processor = LoadDataProcessor(mockRepo),
        intent = DataIntent.Load
    ) {
        expectEvents {
            state { it.data is DataState.DataStatus.Loading }
            state { it.data is DataState.DataStatus.Success }
        }
        
        // Verify final state has data
        assertEvents { events ->
            val finalState = events.last() as ProcessorEvent.StateChange
            val successState = finalState.newState.data as DataState.DataStatus.Success
            assertTrue(successState.items.isNotEmpty())
        }
    }
}
```

### Scenario 5: Pagination

```kotlin
@Test
fun `load more appends items`() = runTest {
    val initialState = ListState(
        data = ListState.DataStatus.Success(
            items = listOf("item1", "item2"),
            hasMore = true
        )
    )
    
    testProcessor(
        initialState = initialState,
        processor = LoadMoreProcessor(mockRepo),
        intent = ListIntent.LoadMore
    ) {
        expectEvents {
            // Set loading more flag
            state { state ->
                val data = state.data as ListState.DataStatus.Success
                data.isLoadingMore
            }
            
            // Add new items
            state { state ->
                val data = state.data as ListState.DataStatus.Success
                data.items.size == 4 && !data.isLoadingMore
            }
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
    testProcessorAsync(
        initialState = State(),
        processor = LongRunningProcessor(),
        intent = Intent.Start
    ) {
        advanceTimeBy(60_000) // Advance 1 minute instantly
        finalState(CompletedState)
    }
}
```

No flakiness from real timing - tests complete in milliseconds.

---

## Testing Edge Cases

### Testing Error Scenarios

```kotlin
@Test
fun `network error shows error state`() = runTest {
    val failingRepo = mockk {
        coEvery { loadData() } throws IOException("No network")
    }
    
    testProcessor(
        initialState = DataState(),
        processor = LoadDataProcessor(failingRepo),
        intent = DataIntent.Load
    ) {
        expectEvents {
            state { it.isLoading }
            state { it.error == "No network" && !it.isLoading }
            sideEffect(DataSideEffect.ShowError("No network"))
        }
    }
}
```

### Testing Empty Results

```kotlin
@Test
fun `empty search results`() = runTest {
    val emptyRepo = mockk {
        coEvery { search(any()) } returns emptyList()
    }
    
    testProcessor(
        initialState = SearchState(),
        processor = SearchProcessor(emptyRepo),
        intent = SearchIntent.Search("query")
    ) {
        finalState(SearchState(
            query = "query",
            results = SearchState.ResultStatus.Empty
        ))
        noSideEffects()
    }
}
```

### Testing Concurrent Intents

```kotlin
@Test
fun `last search query wins`() = runTest {
    testProcessorAsync(
        initialState = SearchState(),
        processor = SearchProcessor(mockRepo),
        intent = SearchIntent.Search("first")
    ) {
        // Dispatch second search while first is processing
        advanceTimeBy(100)
        viewModel dispatch SearchIntent.Search("second")
        
        advanceUntilIdle()
        
        // Only second search result should be in state
        finalState(SearchState(query = "second", results = ...))
    }
}
```

---

## Migration Guide

If you have existing MVI tests:

### Before (Manual Verification)
```kotlin
@Test
fun test() = runTest {
    val states = mutableListOf()
    val effects = mutableListOf()
    
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
    testProcessor(
        initialState = State(),
        processor = MyProcessor(),
        intent = MyIntent
    ) {
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

## Summary

The Pulse testing framework provides:

✅ **Order verification**: `expectEvents { }` for sequential assertions  
✅ **Flexibility**: Multiple assertion methods for different scenarios  
✅ **Type safety**: Compile-time checking of state and effect types  
✅ **Simplicity**: Minimal boilerplate, readable DSL  
✅ **Reliability**: Deterministic async testing with TestScope

Use `testProcessor` for simple tests, `testProcessorAsync` for time-sensitive tests, and `expectEvents { }` when order matters.