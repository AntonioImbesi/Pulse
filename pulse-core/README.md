# Pulse Core

## Overview

The pure Kotlin foundation of the Pulse MVI framework. This module provides the essential interfaces and base classes for implementing the MVI pattern, independent of any specific UI framework.

---

## Key Features

✅ **Pure Kotlin**: No Android dependencies, usable in any Kotlin project (including KMP in the future)  
✅ **Type-Safe Contract**: Generic interfaces ensure strict adherence to MVI principles  
✅ **Atomic State**: State updates are reduced atomically prevents race conditions  
✅ **Reflection-Free**: Designed for compile-time code generation  
✅ **Coroutine-First**: Built entirely on top of Kotlin Coroutines and Flows

---

## Core Concepts

### 1. The MVI Contract

Every feature in Pulse is built around three types:

```kotlin
interface MviHost<State, Intent : Any, SideEffect>
```

*   **State**: Immutable data class representing the current UI state.
*   **Intent**: Sealed interface representing user actions.
*   **SideEffect**: One-time events.

### 2. IntentProcessor

The core component for handling intents and managing state transitions. Each processor handles a specific `Intent` type.

```kotlin
@Processor
class MyProcessor : IntentProcessor<MyState, MyIntent.SpecificAction, MyEffect> {
    override suspend fun ProcessorScope<MyState, MyEffect>.process(intent: MyIntent.SpecificAction) {
        // Access state: currentState
        // Update state: reduce { ... }
        // Send effect: send(Effect)
    }
}
```

### 3. MviEngine

The engine orchestrates the data flow:
1.  Receives `Intent` from the host.
2.  Delegates to the appropriate `IntentProcessor`.
3.  Manages `State` updates atomically.
4.  Multicasts `SideEffect`s.

---

## Code Examples

### Standard Processor
A simple processor that updates state.

```kotlin
@Processor
class IncrementProcessor : IntentProcessor<CounterState, Increment, Nothing> {
    override suspend fun ProcessorScope<CounterState, Nothing>.process(intent: Increment) {
        reduce { state -> state.copy(count = state.count + 1) }
    }
}
```

### Async Processor
Handling network calls or long-running operations.

```kotlin
@Processor
class LoadUserProcessor(private val repo: UserRepository) : IntentProcessor<UserState, LoadUser, UserEffect> {
    override suspend fun ProcessorScope<UserState, UserEffect>.process(intent: LoadUser) {
        // 1. Emitting loading state
        reduce { it.copy(isLoading = true) }
        
        try {
            // 2. Perform async work
            val user = repo.fetchUser(intent.userId)
            
            // 3. Update state with result
            reduce { it.copy(isLoading = false, user = user) }
        } catch (e: Exception) {
            // 4. Handle error
            reduce { it.copy(isLoading = false) }
            send(UserEffect.ShowError(e.message))
        }
    }
}
```

### Flow Processor
Reacting to a stream of data (e.g., location updates).

```kotlin
@Processor
class TrackLocationProcessor(private val locationManager: LocationManager) : IntentProcessor<MapState, StartTracking, MapEffect> {
    override suspend fun ProcessorScope<MapState, MapEffect>.process(intent: StartTracking) {
        locationManager.locationFlow()
            .collect { location ->
                 reduce { it.copy(currentLocation = location) }
            }
    }
}
```

---

## Best Practices

### ✅ DO: Keep State Immutable
```kotlin
// Good
data class CounterState(val count: Int = 0)

// Bad
class CounterState { var count: Int = 0 }
```

### ✅ DO: Use Sealed Interfaces for Intents
```kotlin
// Good
sealed interface CounterIntent {
    data object Increment : CounterIntent
}
```

### ❌ DON'T: Perform Side Effects in reducers
```kotlin
// Bad
reduce { 
    println("Log") // Side effect in reducer
    copy(count = count + 1) 
}

// Good
println("Log")
reduce { copy(count = count + 1) }
```

### ✅ DO: Use Data Objects for simple Intents
Using `data object` instead of `object` or `class` is more efficient and provides better `toString()` implementation.
```kotlin
// Good
data object Increment : CounterIntent

// Bad
object Increment : CounterIntent
class Increment : CounterIntent()
```

### ✅ DO: Handle Exceptions in Processors
Processors are the end of the line. Uncaught exceptions here can crash the application or break the engine loop.
```kotlin
// Good
try {
    val result = repo.fetch()
    reduce { ... }
} catch (e: Exception) {
    send(Effect.Error)
}
```

### ✅ DO: Unit Test Processors in Isolation
Since processors are pure classes with injected dependencies, they are trivial to test without Android mocks.

### ❌ DON'T: Store Mutable State in Processors
Processors should be stateless logic containers. All state should live in the `State` class.
```kotlin
// Bad
class MyProcessor : IntentProcessor<...> {
    private var count = 0 // Data loss on process death!
}
```

### ❌ DON'T: Use Global Scope
Avoid `GlobalScope` or creating your own scope in processors. Use standard structured concurrency patterns or the provided scope if one eventually exists (currently `process` is a suspend function, so you are already in a scope).

### ❌ DON'T: Put View Classes in Logic
Keep `pulse-core` pure. Do not reference `android.view.View`, `Context`, or other framework classes in your processors. This ensures your business logic is portable and testable.
