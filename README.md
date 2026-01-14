# Pulse

A lightweight, type-safe MVI (Model-View-Intent) framework for Kotlin with compile-time code generation.

---

## Overview

Pulse provides a minimal yet powerful foundation for building reactive applications using the MVI pattern. It leverages Kotlin Symbol Processing (KSP) to generate boilerplate code at compile-time, ensuring type safety and eliminating runtime reflection.

```kotlin
// Define your contract
data class CounterState(val count: Int = 0)

sealed interface CounterIntention {
    data object Increment : CounterIntention
    data object Decrement : CounterIntention
}

// Create processors
@Processor
class IncrementProcessor : IntentionProcessor<CounterState, CounterIntention.Increment, CounterEffect> {
    override suspend fun ProcessorScope<CounterState, CounterEffect>.process(
        intention: CounterIntention.Increment
    ) {
        reduce { copy(count = count + 1) }
    }
}

// Use in ViewModel
@HiltViewModel
class CounterViewModel @Inject constructor(
    engineFactory: MviEngineFactory<CounterState, CounterIntention, CounterEffect>
) : MviViewModel<CounterState, CounterIntention, CounterEffect>(
    engineFactory = engineFactory,
    initialState = CounterState()
)

// Integrate with Compose
@Composable
fun CounterScreen(viewModel: CounterViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    
    Button(onClick = { viewModel dispatch CounterIntention.Increment }) {
        Text("Count: ${state.count}")
    }
}
```

---

## Key Features

✅ **Type-Safe**: Full compile-time verification of state, intentions, and side effects  
✅ **Zero Runtime Overhead**: All wiring generated at compile-time via KSP  
✅ **Coroutine-First**: Built on Kotlin Coroutines and Flow  
✅ **Minimal Boilerplate**: Annotate processors, get automatic execution routing  
✅ **Testable**: Clean separation of concerns with comprehensive testing utilities  
✅ **DI-Friendly**: Auto-generates `@Inject` constructors when javax.inject is available  
✅ **Android Ready**: First-class Jetpack Compose and ViewModel integration

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         UI Layer                            │
│         (Compose, dispatches intentions)                    │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    MviViewModel                             │
│     (Manages lifecycle, exposes state & effects)            │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                     MviEngine                               │
│   (Coordinates state, routes intentions to executor)        │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│              ProcessorExecutor (Generated)                  │
│       (Type-safe routing to appropriate processor)          │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│                Individual Processors                        │
│  (Handle specific intentions, update state, emit effects)   │
└─────────────────────────────────────────────────────────────┘
```

### Core Concepts

**MVI Contract**: Every feature defines three types:
- **State**: Immutable data class representing UI state
- **Intentions**: Sealed interface of user actions
- **Side Effects**: One-time events (navigation, toasts, etc.)

**Processors**: Pure functions that handle specific intention types:
- Annotated with `@Processor`
- Update state via `reduce {}`
- Emit side effects via `send()`
- Access current state via `currentUiState`

**Code Generation**: KSP generates type-safe routing at compile-time:
- Exhaustive when statements
- No reflection overhead
- Full IDE support with autocomplete

---

## Modules

| Module | Description | Documentation |
|--------|-------------|---------------|
| **pulse-core** | Core MVI framework | [README](./pulse-core/README.md) |
| **pulse-android** | Android ViewModel and Compose integration | [README](./pulse-android/README.md) |
| **pulse-test** | Testing utilities with fluent DSL | [README](./pulse-test/README.md) |
| **pulse-compiler** | KSP processor | [README](./pulse-compiler/README.md) |

---

## Quick Start

### 1. Add Dependencies

```kotlin
// project build.gradle.kts
plugins {
    id("com.google.devtools.ksp") version "<<ksp_version>>" apply false
}

// app build.gradle.kts
plugins {
    kotlin("android")
    id("com.google.devtools.ksp")
}

dependencies {
    // Core framework
    implementation("io.github.antonioimbesi.pulse:pulse-core:$version")
    ksp("io.github.antonioimbesi.pulse:pulse-compiler:$version")
    
    // Android integration (optional - highly recommended)
    implementation("io.github.antonioimbesi.pulse:pulse-android:$version")
    
    // Testing utilities (optional - highly recommended)
    testImplementation("io.github.antonioimbesi.pulse:pulse-test:$version")
}
```

### 2. Define Your MVI Contract

```kotlin
// State - What the UI displays
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

// Intentions - User actions
sealed interface LoginIntention {
    data class EmailChanged(val email: String) : LoginIntention
    data class PasswordChanged(val password: String) : LoginIntention
    data object LoginClicked : LoginIntention
}

// Side Effects - One-time events
sealed interface LoginEffect {
    data object NavigateToHome : LoginEffect
    data class ShowError(val message: String) : LoginEffect
}
```

### 3. Create Processors

```kotlin
@Processor
class EmailChangedProcessor : IntentionProcessor<LoginState, LoginIntention.EmailChanged, LoginEffect> {
    override suspend fun ProcessorScope<LoginState, LoginEffect>.process(
        intention: LoginIntention.EmailChanged
    ) {
        reduce { copy(email = intention.email, error = null) }
    }
}

@Processor
class LoginClickedProcessor(
    private val authRepository: AuthRepository
) : IntentionProcessor< LoginState, LoginIntention.LoginClicked, LoginEffect> {
    override suspend fun ProcessorScope<LoginState, LoginEffect>.process(
        intention: LoginIntention.LoginClicked
    ) {
        reduce { copy(isLoading = true, error = null) }
        
        when (val result = authRepository.login(currentUiState.email, currentUiState.password)) {
            is Success -> {
                reduce { copy(isLoading = false) }
                send(LoginEffect.NavigateToHome)
            }
            is Failure -> {
                reduce { copy(isLoading = false, error = result.message) }
                send(LoginEffect.ShowError(result.message))
            }
        }
    }
}
```

### 4. Setup Dependency Injection

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
object LoginModule {
    
    @Provides
    fun provideEmailChangedProcessor(): EmailChangedProcessor {
        return EmailChangedProcessor()
    }
    
    @Provides
    fun provideLoginClickedProcessor(
        authRepository: AuthRepository
    ): LoginClickedProcessor {
        return LoginClickedProcessor(authRepository)
    }
    
    // Generated LoginIntentionProcessorExecutor has @Inject constructor
    
    @Provides
    fun provideEngineFactory(
        processorExecutor: LoginIntentionProcessorExecutor
    ): MviEngineFactory<LoginState, LoginIntention, LoginEffect> {
        return DefaultMviEngineFactory(processorExecutor)
    }
}
```

### 5. Create ViewModel

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    engineFactory: MviEngineFactory<LoginState, LoginIntention, LoginEffect>
) : MviViewModel<LoginState, LoginIntention, LoginEffect>(
    engineFactory = engineFactory,
    initialState = LoginState()
)
```

### 6. Integrate with Compose

```kotlin
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(),
    onNavigateToHome: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    viewModel.sideEffect.collectAsEffectWithLifecycle { effect ->
        when (effect) {
            is LoginEffect.NavigateToHome -> onNavigateToHome()
            is LoginEffect.ShowError -> Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = state.email,
            onValueChange = { viewModel dispatch LoginIntention.EmailChanged(it) },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(8.dp))
        
        OutlinedTextField(
            value = state.password,
            onValueChange = { viewModel dispatch LoginIntention.PasswordChanged(it) },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel dispatch LoginIntention.LoginClicked },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            Text(if (state.isLoading) "Loading..." else "Login")
        }
    }
}
```

---

## Testing

Pulse includes comprehensive testing utilities:

```kotlin
@Test
fun `login success navigates to home`() = runTest {
    val fakeRepository = FakeAuthRepository()
    
    testProcessor(
        initialState = LoginState(),
        processor = LoginClickedProcessor(fakeRepository),
        intention = LoginIntention.LoginClicked
    ) {
        expectEvents {
            state(LoginState(isLoading = true))           // Loading starts
            state(LoginState(isLoading = false))          // Loading ends
            sideEffect(LoginEffect.NavigateToHome)        // Navigation triggered
        }
    }
}

@Test
fun `login failure shows error`() = runTest {
    val fakeRepository = FakeAuthRepository(shouldFail = true)
    
    testProcessor(
        initialState = LoginState(),
        processor = LoginClickedProcessor(fakeRepository),
        intention = LoginIntention.LoginClicked
    ) {
        expectEvents {
            state { it.isLoading }
            state { !it.isLoading && it.error != null }
            sideEffect<LoginEffect.ShowError>()
        }
    }
}
```

See [pulse-test README](./pulse-test/README.md) for comprehensive testing patterns.

---

## Code Generation

When you build, KSP generates a `ProcessorExecutor` for each feature:

```kotlin
// Generated in com.example.login.generated package
internal class LoginIntentionProcessorExecutor @Inject constructor(
    private val emailChangedProcessor: EmailChangedProcessor,
    private val passwordChangedProcessor: PasswordChangedProcessor,
    private val loginClickedProcessor: LoginClickedProcessor
) : ProcessorExecutor<LoginState, LoginIntention, LoginEffect> {
    override suspend fun execute(
        context: ProcessorScope<LoginState, LoginEffect>,
        intention: LoginIntention
    ) {
        when (intention) {
            is LoginIntention.EmailChanged -> 
                with(emailChangedProcessor) { context.process(intention) }
            is LoginIntention.PasswordChanged -> 
                with(passwordChangedProcessor) { context.process(intention) }
            is LoginIntention.LoginClicked -> 
                with(loginClickedProcessor) { context.process(intention) }
        }
    }
}
```

Benefits:
- **Type-safe**: Compile error if intention not handled
- **No reflection**: Zero runtime overhead
- **DI-ready**: `@Inject` constructor when javax.inject available
- **Debuggable**: Generated code is readable and navigable

---

## Why Pulse?

### vs Manual MVI
- ❌ Manual: Boilerplate when/reduce functions, easy to forget intentions
- ✅ Pulse: Generated routing, compile-time exhaustiveness checking

### vs Orbit MVI
- ❌ Orbit: All logic in ViewModel, harder to test individual pieces
- ✅ Pulse: Processors are isolated, testable, reusable

### vs Redux-style
- ❌ Redux: Single monolithic reducer, hard to scale
- ✅ Pulse: Distributed processors, scales with feature complexity

### vs MVIKotlin
- ❌ MVIKotlin: Complex setup, steep learning curve
- ✅ Pulse: Simple annotation-based API, familiar concepts

---

## Best Practices

### ✅ DO: Keep Processors Focused
```kotlin
// Good: One responsibility
@Processor
class LoadUserProcessor(private val repo: UserRepository) : 
    IntentionProcessor<ProfileState, LoadUser, ProfileEffect> {
    override suspend fun ProcessorScope<ProfileState, ProfileEffect>.process(
        intention: LoadUser
    ) {
        reduce { copy(isLoading = true) }
        val user = repo.getUser(intention.userId)
        reduce { copy(isLoading = false, user = user) }
    }
}
```

### ✅ DO: Use Sealed Interfaces
```kotlin
// Good: Exhaustive, type-safe
sealed interface ProfileIntention {
    data class LoadUser(val userId: String) : ProfileIntention
    data object Refresh : ProfileIntention
}
```

### ✅ DO: Make State Immutable
```kotlin
// Good: Immutable data class
data class ProfileState(
    val user: User? = null,
    val isLoading: Boolean = false
)
```

### ❌ DON'T: Store Mutable State in Processors
```kotlin
// Bad: Processors should be stateless
@Processor
class BadProcessor : IntentionProcessor<...> {
    private var counter = 0  // ❌ Mutable state
}

// Good: Store in state
data class State(val counter: Int = 0)
```

### ❌ DON'T: Put Business Logic in ViewModel
```kotlin
// Bad: Logic in ViewModel
class MyViewModel(...) : MviViewModel(...) {
    fun validate(email: String) = email.contains("@") // ❌
}

// Good: Logic in processor
@Processor
class ValidateProcessor(private val validator: EmailValidator) : 
    IntentionProcessor<...> { }
```

---

## Performance

- **Compile-time**: ~1-2 seconds overhead for 50+ processors
- **Runtime**: Zero reflection, minimal allocation (immutable state)
- **Memory**: StateFlow replays latest state (one instance)
- **Concurrency**: Intentions processed concurrently by default

---

## Compatibility

- **Kotlin**: 1.9.0+
- **KSP**: 1.9.0-1.0.13+
- **Android**: API 21+ (pulse-android module)
- **Compose**: 1.5.0+ (pulse-android module)
- **Coroutines**: 1.7.0+

---

## Roadmap

- [ ] Time-travel debugging tools
- [ ] Processor composition utilities
- [ ] Multiplatform support (iOS, Desktop, Web)
- [ ] Built-in debouncing support
- [ ] Improve documentation
- [ ] Processor middleware/interceptors

---

## Contributing

This library is currently under active development and is not accepting external contributions at this time.
Once the API and direction are more stable, contribution guidelines will be opened.

---

## Sample Projects

- [Counter App](./sample/counter) - Simple counter demonstrating basics
- [Search Flow](./sample/search) - Search with debouncing

---

## Resources

- [Core Module Documentation](./pulse-core/README.md)
- [Android Module Documentation](./pulse-android/README.md)
- [Testing Guide](./pulse-test/README.md)
- [Development Skill](./CLAUDE_SKILL.md)
- [Migration Guide](./docs/MIGRATION.md)
- [FAQ](./docs/FAQ.md)

---

## License

```
Copyright 2024 Antonio Imbesi

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Support

- 🐛 [Report Issues](https://github.com/AntonioImbesi/Pulse/issues)
- 💬 [Discussions](https://github.com/AntonioImbesi/Pulse/discussions)

---

## Acknowledgments

Inspired by:
- [Orbit MVI](https://github.com/orbit-mvi/orbit-mvi)
- [MVIKotlin](https://github.com/arkivanov/MVIKotlin)
- [Mavericks](https://github.com/airbnb/mavericks)
- [Redux](https://redux.js.org/)

---

**Made with ❤️ by Antonio Imbesi**
