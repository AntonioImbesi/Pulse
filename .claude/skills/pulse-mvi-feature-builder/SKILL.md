---
name: pulse-mvi-feature-builder
description: Guide for implementing Pulse MVI features. Use when creating, updating, or migrating to Pulse MVI architecture, when working with ViewModels, or when implementing state management for any feature. Provides patterns, workflows, and best practices for type-safe MVI implementation across platforms.
---

# Pulse MVI Feature Builder

## When to Use This Skill

Apply this skill when:
- Creating new MVI features from scratch
- Adding or modifying processors in existing features
- Working with ViewModels or state management
- Migrating from other architectures (traditional ViewModel, Orbit, MVIKotlin, Redux)
- Writing tests for processors
- Troubleshooting MVI implementation issues

## Platform Detection

Pulse core is pure Kotlin - no platform dependencies. If the current project targets Android, apply `references/android.md` on top of this skill. That document takes priority for anything Android-specific (ViewModel, Compose, lifecycle).

## Core MVI Concepts

**State**: Immutable data class representing complete UI state
**Intent**: Sealed interface representing either `InitIntent` or user goals
**SideEffect**: Sealed interface of one-time events (navigation, toasts)
**Processor**: Handler for a specific Intent type
**MviEngine**: Orchestrates the data flow between components

Fundamental rule: 1 Intent → 1 Processor. Enforced at compile-time by KSP.

## State Design

State must be immutable and represent complete UI state at any moment.

**Pattern: Sealed Interface for Mutually Exclusive States (recommended)**
```kotlin
data class LoginState(
    val email: String = "",
    val password: String = "",
    val status: Status = Status.Idle
) {
    sealed interface Status {
        data object Idle : Status
        data object Loading : Status
        data class Success(val token: String) : Status
        data class Error(val message: String) : Status
    }
}
```

This prevents impossible states. Can't be Loading AND Error simultaneously. Compiler enforces exhaustive `when` handling.

**Pattern: Boolean Flags (only for truly independent toggles)**
```kotlin
data class SettingsState(
    val isDarkMode: Boolean = false,
    val isNotificationsEnabled: Boolean = false
)
```

**Rules:**
- `data class` with `val` properties only
- Provide default values for all fields
- No mutable collections
- Use sealed interfaces for mutually exclusive states
- Removing a field: just remove it. No deprecation needed.

## Intent Design

Intent represents exactly two things: `InitIntent` and user goals. Nothing else.

```kotlin
sealed interface LoginIntent {
    data object Init : LoginIntent
    data class UpdateEmail(val email: String) : LoginIntent
    data class UpdatePassword(val password: String) : LoginIntent
    data object Login : LoginIntent
}
```

**Naming:** Name by what the user wants to accomplish.
- ✅ `Login`, `UpdateEmail`, `RefreshData`, `Init`
- ❌ Everything else. Not UI events, not data changes, not internal logic steps, not state transitions, not system callbacks.

**Rationale:** Intent names must remain valid regardless of how the UI is implemented or what triggered the action internally. The only exception is `Init` which represents the initial setup of the feature.

**Types:**
- `data object` for intents without parameters
- `data class` for intents with parameters

## SideEffect Design

SideEffect represents one-time events that don't belong in persistent state.

```kotlin
sealed interface LoginSideEffect {
    data object NavigateToHome : LoginSideEffect
    data class ShowError(val message: String) : LoginSideEffect
}
```

**Use SideEffect for:** Navigation, toasts, dialogs, one-time animations
**Use State for:** Anything that needs to persist across configuration changes or be displayed persistently

## Processor Implementation

Processors handle specific Intent types and coordinate state updates.

```kotlin
import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope

@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor<LoginState, LoginIntent.Login, LoginSideEffect> {
    
    override suspend fun ProcessorScope<LoginState, LoginSideEffect>.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(status = LoginState.Status.Loading) }
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Result.Success -> {
                reduce { copy(status = LoginState.Status.Success(result.token)) }
                send(LoginSideEffect.NavigateToHome)
            }
            is Result.Error -> {
                reduce { copy(status = LoginState.Status.Error(result.message)) }
            }
        }
    }
}
```

**ProcessorScope API:**
- `currentState` - Read current state
- `reduce { copy(...) }` - Update state atomically
- `send(effect)` - Emit one-time side effect

**Rules:**
- Annotate with `@Processor` (required for KSP)
- Delegate business logic to use cases/repositories
- Access state inside `reduce { }` for read-modify-write
- Use `currentState` for read-only access outside reduce

**Organizing complex processors with private functions:**
```kotlin
@Processor
class SubmitFormProcessor(
    private val validator: Validator,
    private val repository: Repository
) : IntentProcessor<FormState, FormIntent.Submit, FormSideEffect> {
    
    override suspend fun ProcessorScope<FormState, FormSideEffect>.process(
        intent: FormIntent.Submit
    ) {
        if (!validateForm()) return
        submitToBackend()
    }
}

private suspend fun ProcessorScope<FormState, FormSideEffect>.validateForm(): Boolean {
    val errors = validator.validate(currentState)
    if (errors.isNotEmpty()) {
        reduce { copy(validationErrors = errors) }
        return false
    }
    return true
}

private suspend fun ProcessorScope<FormState, FormSideEffect>.submitToBackend() {
    reduce { copy(isSubmitting = true) }
    when (val result = repository.submit(currentState.data)) {
        is Result.Success -> {
            reduce { copy(isSubmitting = false) }
            send(FormSideEffect.NavigateToSuccess)
        }
        is Result.Error -> {
            reduce { copy(isSubmitting = false, error = result.message) }
        }
    }
}
```

Do not create fake intents for internal logic steps. Private functions are the correct pattern.

**Observing reactive flows:**
```kotlin
@Processor
class ObserveUserProcessor(
    private val userRepository: UserRepository
) : IntentProcessor<ProfileState, ProfileIntent.ObserveUser, ProfileSideEffect> {
    
    override suspend fun ProcessorScope<ProfileState, ProfileSideEffect>.process(
        intent: ProfileIntent.ObserveUser
    ) {
        userRepository.getUserFlow().collect { user ->
            reduce { copy(user = user) }
        }
    }
}
```

One-time fetch (`val data = repo.getData()`) loads once. Reactive flow (`repo.getFlow().collect { }`) continuously updates.

## Code Generation (KSP)

KSP generates `ProcessorExecutor` at compile-time in `{package}.generated` subpackage.

The generated executor:
- Routes each Intent to its Processor via type-safe `when` expression
- Includes all classes annotated with `@Processor`
- Enforces 1:1 Intent-to-Processor mapping at compile-time
- Auto-generates `@Inject` constructor when javax.inject is on classpath

Never implement `ProcessorExecutor` manually.

## Dependency Injection

Processors are plain classes with constructor dependencies. How those dependencies are provided depends on your DI setup.

See the appropriate reference for your project:
- `references/di-hilt.md` - Hilt (Android)
- `references/di-koin.md` - Koin
- `references/di-manual.md` - Manual wiring
- `references/di-none.md` - No DI (direct instantiation)

## Testing

Use `testProcessor` from `pulse-test`. Always use Given-When-Then naming.

```kotlin
import io.github.antonioimbesi.pulse.test.testProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Test

@Test
fun `given valid credentials, when login intent dispatched, then navigates to home`() = runTest {
    // See references/testing-guide.md for comprehensive patterns
    testProcessor(
        initialState = LoginState(),
        processor = LoginProcessor(mockRepository),
        intent = LoginIntent.Login
    ) {
        expectEvents {
            state { it.status is LoginState.Status.Loading }
            state { it.status is LoginState.Status.Success }
            sideEffect(LoginSideEffect.NavigateToHome)
        }
    }
}
```

**Assertion methods:**
- `finalState(expected)` - Verify end state
- `expectEvents { }` - Verify exact event sequence
- `expectStates(...)` - Verify state sequence only
- `expectSideEffects(...)` - Verify effect sequence only
- `noStateChanges()` / `noSideEffects()` - Assert nothing happened

See `references/testing-guide.md` for comprehensive patterns.

## StateFlow Behavior

StateFlow uses structural equality. If new state equals old state, no emission occurs.

```kotlin
data class State(val count: Int)
reduce { copy(count = 5) } // No emission if count is already 5
```

## Critical Anti-Patterns

**❌ Intents that aren't Init or user goals**
Wrong: `OnLoginClicked`, `DataLoaded`, `ValidateForm`, `OnLocationUpdated`
Right: `Login`, `Init` — only these two categories exist

**❌ Boolean flags for mutually exclusive states**
Wrong: `isLoading: Boolean, hasError: Boolean, hasData: Boolean`
Right: `sealed interface Status { Loading, Error, Success }`

**❌ Exposing all intents to specialized composables**
Wrong: passing `(FeatureIntent) -> Unit` to child composables
Right: pass only the specific callbacks each composable needs

**❌ Fake intents for internal logic**
Wrong: `sealed interface Intent { ValidateForm, Submit }` where ValidateForm is not a user action
Right: private functions inside the processor

**❌ Mutable state**
Wrong: `var`, `MutableList`
Right: `val`, `List`

See `references/anti-patterns.md` for full details.

## Package Structure

```
com.yourapp.feature/
├── contract/
│   ├── FeatureState.kt
│   ├── FeatureIntent.kt
│   └── FeatureSideEffect.kt
├── processor/
│   ├── LoadDataProcessor.kt
│   └── UpdateEmailProcessor.kt
├── generated/ (KSP auto-generated)
│   └── FeatureIntentProcessorExecutor.kt
└── [platform-specific host, e.g. FeatureViewModel.kt on Android]
```

## Build Configuration

```kotlin
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("io.github.antonioimbesi.pulse:pulse-core:$version")
    ksp("io.github.antonioimbesi.pulse:pulse-compiler:$version")
    testImplementation("io.github.antonioimbesi.pulse:pulse-test:$version")
}
```

Android projects add `pulse-android` dependency. See `references/android.md`.

## Reference Documentation

**Core workflows:**
- `references/creating-features.md`
- `references/updating-features.md`
- `references/testing-guide.md`
- `references/anti-patterns.md`
- `references/troubleshooting.md`

**Platform:**
- `references/android.md` — Android integration (ViewModel, Compose). Apply on top of this skill when targeting Android.

**Dependency injection:**
- `references/di-hilt.md`
- `references/di-koin.md`
- `references/di-manual.md`
- `references/di-none.md`

**Migration:**
- `references/migration-traditional-viewmodel.md`
- `references/migration-orbit-mvi.md`
- `references/migration-mvikotlin.md`
- `references/migration-redux.md`

**Navigation:**
- `references/decision-tree.md`
