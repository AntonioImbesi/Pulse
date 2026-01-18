# Pulse Android

## Overview

Android-specific extensions for Pulse, providing seamless integration with Jetpack ViewModel and Compose. This module acts as the bridge between the pure Kotlin `pulse-core` and the Android framework.

---

## Core Concepts

### 1. MviViewModel

A base `ViewModel` that implements `MviHost` and manages the `MviEngine`. It acts as the container for your feature's state and logic.

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    engineFactory: MyEngineFactory
) : MviViewModel<State, Intent, Effect>(
    engineFactory = engineFactory,
    initialState = InitialState
)
```

### 2. Safe Side-Effect Collection

Collecting side effects (like Navigation or Toasts) must be done carefully in Android. `collectSideEffect` handles the complexity of pausing collection when the app is in the background.

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state by viewModel.collectState()
    
    // Safety: Only collects when Lifecycle is at least STARTED
    viewModel.collectSideEffect { effect ->
        when (effect) {
            is Effect.Navigate -> navController.navigate(...)
            is Effect.ShowError -> toast(...)
        }
    }
}
```

---

## Integration Guide

### 1. Hilt Setup (Recommended)

Pulse works best with Hilt. You need a standard Dagger Module to provide your Processors and the Engine Factory.

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
object LoginModule {

    @Provides
    fun provideLoginProcessor(repo: AuthRepository): LoginProcessor {
        return LoginProcessor(repo) // Manual construction or @Inject on Processor itself
    }

    // The Compiler generates LoginIntentProcessorExecutor
    @Provides
    fun provideEngineFactory(
        executor: LoginIntentProcessorExecutor 
    ): MviEngineFactory<LoginState, LoginIntent, LoginEffect> {
        return DefaultMviEngineFactory(executor)
    }
}
```

### 2. Koin Setup

If you prefer Koin, you can easily define a module to wire up the components.

```kotlin
val loginModule = module {
    // 1. Define Processors
    factoryOf(::LoginProcessor)
    
    // 2. Define Generated Executor
    factoryOf(::LoginIntentProcessorExecutor)
    
    // 3. Define Engine Factory
    // We bind the generic interface to the default implementation
    factory<MviEngineFactory<LoginState, LoginIntent, LoginEffect>> {
        DefaultMviEngineFactory(get<LoginIntentProcessorExecutor>())
    }
    
    // 4. Define ViewModel
    viewModelOf(::LoginViewModel)
}
```

### 3. Manual Dependency Injection

If you aren't using Hilt, you can just instantiate the factory manually in your ViewModel factory.

```kotlin
class MyViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val executor = MyIntentProcessorExecutor(processor1, processor2)
        val factory = DefaultMviEngineFactory(executor)
        return MyViewModel(factory) as T
    }
}
```

---

## Best Practices

### ✅ DO: Use collectSideEffect
Always use the provided extension for side effects to avoid crashes when the app is in the background.

```kotlin
// Good
viewModel.collectSideEffect { ... }

// Bad (unsafe in Compose)
LaunchedEffect(Unit) {
    viewModel.sideEffect.collect { ... } 
}
```

### ✅ DO: Inject Engine Factories
Let Hilt/Dagger provide the factory to keep your ViewModel testable and clean.
