# Pulse MVI Troubleshooting Guide

This guide covers common issues and their solutions when working with Pulse MVI.

## Build & Code Generation Issues

### ProcessorExecutor Not Found
**Symptoms:** Build error: "Unresolved reference: FeatureIntentProcessorExecutor"

**Solutions:**
1. Verify KSP plugin is applied: `id("com.google.devtools.ksp")`
2. Verify dependency: `ksp("io.github.antonioimbesi.pulse:pulse-compiler:$version")`
3. Rebuild project: `./gradlew clean build`
4. Check that at least one processor has @Processor annotation
5. Verify processor package matches intent package (or is a subpackage)
6. Check `build/generated/ksp/{variant}/kotlin/` for generated code

**Debug:**
```bash
# Check if generated code exists
find build/generated/ksp -name "*ProcessorExecutor.kt"

# Verify KSP is running
./gradlew clean build --info | grep ksp
```

### Multiple Processors for Same Intent
**Symptoms:** Build error from KSP about ambiguous intent handling

**Solution:** Each Intent subtype must have exactly ONE processor. Find and remove duplicate processors.

**Debug:**
```bash
# Search for processors handling the same intent
grep -r "IntentProcessor<.*YourIntent" .
```

### Processor Not Included in Generated Executor
**Symptoms:** New processor exists but isn't called

**Solutions:**
1. Ensure @Processor annotation is present
2. Verify processor is in same package or subpackage as Intent
3. Clean and rebuild: `./gradlew clean build`
4. Check generated executor source code
5. Verify Intent type in processor signature matches exactly

**Verify:**
```bash
# Check generated executor
cat build/generated/ksp/debug/kotlin/com/yourapp/feature/generated/FeatureIntentProcessorExecutor.kt
```

### Build Performance Issues
**Symptoms:** Slow KSP processing during build

**Solutions:**
1. Enable KSP incremental processing in gradle.properties:
   ```properties
   ksp.incremental=true
   ksp.incremental.log=true
   ```
2. Use build cache:
   ```properties
   org.gradle.caching=true
   ```
3. Split large features into smaller modules if needed

## Runtime Issues

### State Not Updating in UI
**Symptoms:** UI doesn't reflect state changes after dispatching intents

**Solutions:**
1. Verify using `reduce { copy(...) }` not direct assignment
2. Check State is a data class with val properties
3. Ensure collectState() is used in Compose
4. Verify intent is actually being dispatched: add log in processor
5. Check StateFlow is being collected properly

**Debug processor:**
```kotlin
override suspend fun ProcessorScope<FeatureState, FeatureSideEffect>.process(intent: FeatureIntent) {
    println("Processing: $intent") // Add logging
    reduce { 
        println("Old state: $this, New state: ${copy(...)}") // Log state change
        copy(...)
    }
}
```

### Side Effects Not Triggering
**Symptoms:** Navigation or toasts don't occur

**Solutions:**
1. Verify using `collectSideEffect { }` not manual collection
2. Check when expression handles all effect types exhaustively
3. Add log in collectSideEffect to verify collection is occurring
4. Ensure app is in foreground (STARTED state) when effect is sent
5. Check Channel buffer isn't full (shouldn't happen with BUFFERED)

**Debug:**
```kotlin
viewModel.collectSideEffect { effect ->
    println("Received effect: $effect") // Add logging
    when (effect) {
        // ...
    }
}
```

### App Crashes When Backgrounded
**Symptoms:** Crash when performing navigation/UI operations while app is in background

**Solution:** Use `collectSideEffect { }` instead of manual LaunchedEffect collection

**Wrong:**
```kotlin
LaunchedEffect(Unit) {
    viewModel.sideEffect.collect { effect ->
        navController.navigate(...) // Crashes when backgrounded!
    }
}
```

**Right:**
```kotlin
viewModel.collectSideEffect { effect ->
    when (effect) {
        is Effect.Navigate -> navController.navigate(...)
    }
}
```

## Dependency Injection Issues

### Injection Failures (Hilt)
**Symptoms:** "Cannot find X" or "No binding for Y"

**Solutions:**
1. Verify module is @InstallIn(ViewModelComponent::class)
2. Check ViewModel has @HiltViewModel annotation
3. Verify processors are provided or have @Inject constructors
4. Ensure MviEngineFactory is provided with correct type parameters
5. Clean and rebuild project: `./gradlew clean build`
6. Check Hilt is properly configured in app module

**Debug:**
```bash
# Check if generated Hilt components exist
find . -name "*_HiltModules*"
```

**Common Hilt Mistakes:**
```kotlin
// Wrong: Missing @HiltViewModel
class MyViewModel(...) : MviViewModel(...)

// Right:
@HiltViewModel
class MyViewModel @Inject constructor(...) : MviViewModel(...)

// Wrong: Wrong component
@InstallIn(SingletonComponent::class) // ViewModels need ViewModelComponent!

// Right:
@InstallIn(ViewModelComponent::class)
```

### Injection Failures (Koin)
**Symptoms:** "No definition found for X"

**Solutions:**
1. Verify module is included in startKoin { modules(...) }
2. Check factory/single definitions match usage (factory vs single)
3. Verify type parameters in MviEngineFactory definition match exactly
4. Use explicit type binding: `factory<MviEngineFactory<S, I, E>> { ... }`
5. Check dependency graph with Koin logger

**Debug:**
```kotlin
startKoin {
    logger(Level.ERROR) // Enable logging
    modules(featureModule)
}
```

**Common Koin Mistakes:**
```kotlin
// Wrong: Generic type not specified
factory { DefaultMviEngineFactory(get()) }

// Right: Explicit type binding
factory<MviEngineFactory<LoginState, LoginIntent, LoginSideEffect>> {
    DefaultMviEngineFactory(get<LoginIntentProcessorExecutor>())
}

// Wrong: Module not included
startKoin {
    modules(appModule) // Missing featureModule!
}

// Right:
startKoin {
    modules(appModule, featureModule)
}
```

### Wrong Processor Instance Injected
**Symptoms:** Processor has wrong dependencies or null values

**Solutions:**
1. Verify processor constructor parameters match DI provides
2. Check processor is being created by DI, not manually instantiated
3. Ensure all processor dependencies are provided in DI graph

## Type Issues

### Type Mismatch in Processor Signature
**Symptoms:** "Type mismatch" errors in processor or generated code

**Solution:** Ensure all type parameters match exactly across State, Intent, and SideEffect

**Wrong:**
```kotlin
// State
data class LoginState(...)

// Intent
sealed interface LoginIntent { ... }

// Processor - WRONG type parameters!
class LoginProcessor : IntentProcessor<ProfileState, LoginIntent.Login, ProfileSideEffect>
```

**Right:**
```kotlin
class LoginProcessor : IntentProcessor<LoginState, LoginIntent.Login, LoginSideEffect>
// All types must match the feature's contract
```

### Sealed Interface Exhaustiveness
**Symptoms:** "when expression must be exhaustive" compiler error

**Solution:** Handle all sealed interface subtypes in when expressions

**Wrong:**
```kotlin
when (state.data) {
    is DataState.Loading -> LoadingView()
    is DataState.Success -> DataView(state.data)
    // Missing Error case!
}
```

**Right:**
```kotlin
when (state.data) {
    is DataState.Idle -> IdleView()
    is DataState.Loading -> LoadingView()
    is DataState.Success -> DataView(state.data)
    is DataState.Error -> ErrorView(state.error)
}
```

## Testing Issues

### Tests Failing After Migration
**Symptoms:** Existing tests break after converting to Pulse

**Solutions:**
1. Update test dependencies: add `testImplementation("pulse-test:$version")`
2. Replace manual state verification with testProcessor { }
3. Use expectEvents { } for order-sensitive tests
4. Use finalState() for simple state verification
5. Update mocks to match processor constructors

**Example migration:**
```kotlin
// Old test
@Test
fun testLogin() = runTest {
    val viewModel = LoginViewModel(mockRepository)
    viewModel.onLoginClicked()
    advanceUntilIdle()
    assertEquals(expectedState, viewModel.uiState.value)
}

// New test
@Test
fun testLogin() = runTest {
    testProcessor(
        initialState = LoginState(),
        processor = LoginProcessor(mockRepository),
        intent = LoginIntent.Login
    ) {
        finalState(expectedState)
    }
}
```

### Test Flakiness with Side Effects
**Symptoms:** Tests sometimes pass, sometimes fail when checking side effects

**Solution:** Use expectEvents { } to verify order, or use expectSideEffects for order-insensitive checks

```kotlin
// Flaky: Order might vary
@Test
fun test() = runTest {
    testProcessor(...) {
        assertTrue(sideEffects.contains(Effect1))
        assertTrue(sideEffects.contains(Effect2))
    }
}

// Stable: Explicit order
@Test
fun test() = runTest {
    testProcessor(...) {
        expectEvents {
            sideEffect(Effect1)
            sideEffect(Effect2)
        }
    }
}
```

## Common Error Messages

### "Cannot inline bytecode built with JVM target 1.8"
**Cause:** Kotlin version mismatch between app and Pulse library

**Solution:** Ensure Kotlin version in your project matches or is newer than Pulse requirements

```kotlin
// build.gradle.kts
kotlin {
    jvmToolchain(17) // or 11, match Pulse requirements
}
```

### "Suspend function 'process' should be called only from a coroutine"
**Cause:** Trying to call processor directly outside coroutine scope

**Solution:** Processors are called automatically by the engine. Don't call process() directly.

**Wrong:**
```kotlin
val processor = LoadDataProcessor(repo)
processor.process(LoadData) // Wrong!
```

**Right:**
```kotlin
viewModel dispatch LoadData // Dispatch through ViewModel
```

### "lateinit property has not been initialized"
**Cause:** ViewModel or engine factory not properly injected

**Solutions:**
1. Verify DI setup is correct
2. Check ViewModel is created via hiltViewModel() or viewModel()
3. Ensure all dependencies are provided in DI graph

## Performance Issues

### Excessive Recompositions

**Symptoms:** UI updates too frequently, performance degraded

**Common Cause: Creating new objects unnecessarily**

```kotlin
// Bad: Creates new list object even if content is identical
reduce { copy(items = currentState.items.toList()) }
// StateFlow sees different list instance → emits → recomposition
// (even though content is the same!)

// Good: Only create new list if actually changed
if (newItem !in currentState.items) {
    reduce { copy(items = currentState.items + newItem) }
}
// No reduce call if unchanged → no emission → no recomposition
```

**Important Note:** StateFlow uses **structural equality** (`==`) for data classes. If the new state equals the old state, StateFlow will **NOT emit**, preventing unnecessary recompositions.

```kotlin
data class State(val count: Int)

// This will NOT cause emission if count is already 5:
reduce { copy(count = 5) }
// StateFlow compares: State(5) == State(5) → true → no emission

// However, creating "different but equivalent" objects will emit:
reduce { copy(items = currentState.items.toList()) }
// StateFlow compares: List(a,b,c) == List(a,b,c) → true by content
// But they're different instances! → emits
```

**Solutions:**

1. **Use immutable collections properly (don't recreate if unchanged)**
   ```kotlin
   // Bad: Always creates new list
   reduce { copy(items = currentState.items + newItems) }
   
   // Good: Only add if new
   if (newItems.isNotEmpty()) {
       reduce { copy(items = currentState.items + newItems) }
   }
   ```

2. **Use `remember { }` for expensive computations in UI**
   ```kotlin
   @Composable
   fun MyScreen(state: State) {
       val sortedItems = remember(state.items) {
           state.items.sortedBy { it.name }
       }
   }
   ```

3. **Avoid recreating equivalent collections**
   ```kotlin
   // Bad: Creates new list every time
   reduce { 
       copy(items = items.filter { it.isActive }.map { it.copy() })
   }
   
   // Good: Only process if changed
   val activeItems = currentState.items.filter { it.isActive }
   if (activeItems != currentState.activeItems) {
       reduce { copy(activeItems = activeItems) }
   }
   ```

**Key Principle:** StateFlow only emits when the new value is **not equal** to the old value. For data classes, this means structural equality. The issue arises when you create new instances that are structurally equal but not referentially equal (e.g., new list with same content).

### Memory Leaks
**Symptoms:** Memory usage grows over time

**Solutions:**
1. Ensure ViewModel is properly cleared when screen is destroyed
2. Don't hold references to ViewModel outside its scope
3. Cancel any launched coroutines in processors on ViewModel clear

**Check ViewModel lifecycle:**
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(...) : MviViewModel(...) {
   init {
      println("ViewModel created")
   }

   override fun onCleared() {
      println("ViewModel cleared") // Should be called!
      super.onCleared()
   }
}
```

## Getting Help

If you're still stuck after trying these solutions:

1. Check Pulse documentation: https://github.com/antonioimbesi/pulse
2. Search existing issues: https://github.com/antonioimbesi/pulse/issues
3. Create a minimal reproducible example and file an issue

**When asking for help, include:**
- Pulse version
- Kotlin version
- Build configuration (Hilt/Koin/Manual)
- Minimal code sample showing the issue
- Full error message and stack trace
- What you've already tried