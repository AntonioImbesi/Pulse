# Migration from Orbit MVI to Pulse MVI

This guide covers migrating from Orbit MVI to Pulse MVI framework.

## Conceptual Mapping

| Orbit Concept | Pulse Equivalent | Notes |
|---------------|------------------|-------|
| `ContainerHost` | `MviViewModel` | Base ViewModel class |
| `container<State, SideEffect>()` | `MviEngineFactory` | State machine creation |
| `intent { }` block | `IntentProcessor` | Intent handling logic |
| `reduce { }` | `reduce { }` | Same! State updates |
| `postSideEffect()` | `send()` | Side effect emission |
| State | State | Same concept |
| SideEffect | SideEffect | Same concept |
| No direct equivalent | Intent (sealed interface) | Explicit intent types |

## Key Differences

### 1. **Explicit Intent Types**

**Orbit:** Intents are implicit (method calls that trigger `intent { }` blocks)
```kotlin
fun onEmailChanged(email: String) = intent {
    reduce { state.copy(email = email) }
}
```

**Pulse:** Intents are explicit sealed interface subtypes
```kotlin
sealed interface LoginIntent {
    data class UpdateEmail(val email: String) : LoginIntent
}

@Processor
class UpdateEmailProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(intent: LoginIntent.UpdateEmail) {
        reduce { copy(email = intent.email) }
    }
}
```

### 2. **One Intent Block → One Processor**

**Orbit:** Multiple `intent { }` blocks in one ViewModel
```kotlin
class LoginViewModel : ContainerHost {
    fun onEmailChanged(email: String) = intent { ... }
    fun onPasswordChanged(password: String) = intent { ... }
    fun onLoginClicked() = intent { ... }
}
```

**Pulse:** Each intent → dedicated processor class
```kotlin
@Processor class UpdateEmailProcessor : IntentProcessor
@Processor class UpdatePasswordProcessor : IntentProcessor
@Processor class LoginProcessor : IntentProcessor
```

### 3. **Code Generation**

**Orbit:** No code generation (runtime dispatch)
**Pulse:** KSP generates `ProcessorExecutor` at compile-time

## Migration Steps

### Step 1: Add Pulse Dependencies

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    // Keep Orbit temporarily for gradual migration
    implementation("io.github.antonioimbesi.pulse:pulse-core:$version")
    implementation("io.github.antonioimbesi.pulse:pulse-android:$version")
    ksp("io.github.antonioimbesi.pulse:pulse-compiler:$version")
}
```

### Step 2: Map Orbit Concepts to Pulse

#### Example Orbit ViewModel:
```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ContainerHost, ViewModel() {
    
    override val container = container(LoginState())
    
    fun onEmailChanged(email: String) = intent {
        reduce { state.copy(email = email) }
    }
    
    fun onPasswordChanged(password: String) = intent {
        reduce { state.copy(password = password) }
    }
    
    fun onLoginClicked() = intent {
        reduce { state.copy(isLoading = true) }
        
        when (val result = authRepository.login(state.email, state.password)) {
            is Result.Success -> {
                reduce { state.copy(isLoading = false) }
                postSideEffect(LoginSideEffect.NavigateToHome)
            }
            is Result.Error -> {
                reduce { state.copy(isLoading = false, error = result.message) }
                postSideEffect(LoginSideEffect.ShowError(result.message))
            }
        }
    }
}

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface LoginSideEffect {
    data object NavigateToHome : LoginSideEffect
    data class ShowError(val message: String) : LoginSideEffect
}
```

#### Converted to Pulse:

**1. Keep State (unchanged):**
```kotlin
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
```

**2. Keep SideEffect (unchanged):**
```kotlin
sealed interface LoginSideEffect {
    data object NavigateToHome : LoginSideEffect
    data class ShowError(val message: String) : LoginSideEffect
}
```

**3. Create Intent sealed interface:**
```kotlin
// NEW: Extract from method names
sealed interface LoginIntent {
    data class UpdateEmail(val email: String) : LoginIntent
    data class UpdatePassword(val password: String) : LoginIntent
    data object Login : LoginIntent
}
```

**4. Convert each `intent { }` block to a Processor:**

```kotlin
// From: fun onEmailChanged(email: String) = intent { ... }
@Processor
class UpdateEmailProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.UpdateEmail
    ) {
        reduce { copy(email = intent.email) } // Same as Orbit!
    }
}

// From: fun onPasswordChanged(password: String) = intent { ... }
@Processor
class UpdatePasswordProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.UpdatePassword
    ) {
        reduce { copy(password = intent.password) }
    }
}

// From: fun onLoginClicked() = intent { ... }
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(isLoading = true) }
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Result.Success -> {
                reduce { copy(isLoading = false) }
                send(LoginSideEffect.NavigateToHome) // Orbit: postSideEffect → Pulse: send
            }
            is Result.Error -> {
                reduce { copy(isLoading = false, error = result.message) }
                send(LoginSideEffect.ShowError(result.message))
            }
        }
    }
}
```

**5. Convert ViewModel:**

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    engineFactory: MviEngineFactory
) : MviViewModel(
    engineFactory = engineFactory,
    initialState = LoginState()
)
// That's it! All logic is in processors now.
```

### Step 3: Setup Dependency Injection

```kotlin
@Module
@InstallIn(ViewModelComponent::class)
object LoginModule {

    @Provides
    fun provideLoginProcessor(
        authRepository: AuthRepository
    ): LoginProcessor {
        return LoginProcessor(authRepository)
    }

    @Provides
    fun provideUpdateEmailProcessor(): UpdateEmailProcessor {
        return UpdateEmailProcessor()
    }

    @Provides
    fun provideUpdatePasswordProcessor(): UpdatePasswordProcessor {
        return UpdatePasswordProcessor()
    }

    @Provides
    fun provideEngineFactory(
        executor: LoginIntentProcessorExecutor // Auto-generated by KSP
    ): MviEngineFactory {
        return DefaultMviEngineFactory(executor)
    }
}
```

### Step 4: Update UI

**Orbit UI:**
```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.container.stateFlow.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.container.sideEffectFlow.collect { effect ->
            when (effect) {
                LoginSideEffect.NavigateToHome -> navController.navigate("home")
                is LoginSideEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }
    
    LoginContent(
        state = state,
        onEmailChange = viewModel::onEmailChanged,
        onPasswordChange = viewModel::onPasswordChanged,
        onLoginClick = viewModel::onLoginClicked
    )
}
```

**Pulse UI:**
```kotlin
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    navController: NavController,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val state by viewModel.collectState()
    
    viewModel.collectSideEffect { effect ->
        when (effect) {
            LoginSideEffect.NavigateToHome -> navController.navigate("home")
            is LoginSideEffect.ShowError -> {
                snackbarHostState.showSnackbar(effect.message)
            }
        }
    }
    
    LoginContent(
        state = state,
        onIntent = viewModel::dispatch // Single dispatch point!
    )
}

@Composable
private fun LoginContent(
    state: LoginState,
    onIntent: (LoginIntent) -> Unit
) {
    TextField(
        value = state.email,
        onValueChange = { onIntent(LoginIntent.UpdateEmail(it)) }
    )
    
    TextField(
        value = state.password,
        onValueChange = { onIntent(LoginIntent.UpdatePassword(it)) }
    )
    
    Button(onClick = { onIntent(LoginIntent.Login) }) {
        Text("Login")
    }
}
```

**Key changes:**
- `container.stateFlow.collectAsState()` → `collectState()`
- `container.sideEffectFlow.collect { }` → `collectSideEffect { }`
- Multiple method references → Single `onIntent` parameter
- `viewModel::onXChanged` → `onIntent(Intent.X)`

## Migration Patterns

### Pattern 1: Orbit Transformers → Multiple Processors

**Orbit:**
```kotlin
fun search(query: String) = intent {
    reduce { state.copy(query = query) }
}.transformLatest {
    flow {
        emit(searchRepository.search(query))
    }.collect { results ->
        reduce { state.copy(results = results) }
    }
}
```

**Pulse:**
```kotlin
// Split into two intents/processors
sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data object PerformSearch : SearchIntent
}

@Processor
class UpdateQueryProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: SearchIntent.UpdateQuery
    ) {
        reduce { copy(query = intent.query) }
        // Optionally trigger search
        send(SearchSideEffect.TriggerSearch)
    }
}

@Processor
class PerformSearchProcessor(
    private val searchRepository: SearchRepository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: SearchIntent.PerformSearch
    ) {
        reduce { copy(isSearching = true) }
        val results = searchRepository.search(currentState.query)
        reduce { copy(isSearching = false, results = results) }
    }
}
```

### Pattern 2: Orbit `orbit { }` → Processor with Flow Collection

**Orbit:**
```kotlin
fun observeUser() = orbit {
    userRepository.getUserFlow()
        .collect { user ->
            reduce { state.copy(user = user) }
        }
}
```

**Pulse:**
```kotlin
@Processor
class ObserveUserProcessor(
    private val userRepository: UserRepository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: ProfileIntent.ObserveUser
    ) {
        userRepository.getUserFlow()
            .collect { user ->
                reduce { copy(user = user) }
            }
    }
}
```

### Pattern 3: Orbit `sideEffect { }` Block → Inline `send()`

**Orbit:**
```kotlin
fun deleteItem(id: String) = intent {
    repository.delete(id)
    reduce { state.copy(items = state.items.filter { it.id != id }) }
}.sideEffect {
    post(SideEffect.ShowDeleted)
}
```

**Pulse:**
```kotlin
@Processor
class DeleteItemProcessor(
    private val repository: Repository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: ListIntent.DeleteItem
    ) {
        repository.delete(intent.id)
        reduce { copy(items = items.filter { it.id != intent.id }) }
        send(ListSideEffect.ShowDeleted) // Inline, no separate block
    }
}
```

## Testing Migration

### Orbit Test:
```kotlin
@Test
fun `test login success`() = runTest {
    val viewModel = LoginViewModel(mockRepository)
    
    viewModel.onLoginClicked()
    
    viewModel.container.stateFlow.test {
        assertEquals(LoginState(isLoading = false), awaitItem())
    }
    
    viewModel.container.sideEffectFlow.test {
        assertEquals(LoginSideEffect.NavigateToHome, awaitItem())
    }
}
```

### Pulse Test:
```kotlin
@Test
fun `test login success`() = runTest {
    testProcessor(
        initialState = LoginState(),
        processor = LoginProcessor(mockRepository),
        intent = LoginIntent.Login
    ) {
        finalState(LoginState(isLoading = false))
        expectSideEffects(LoginSideEffect.NavigateToHome)
    }
}
```

See `references/testing-guide.md` for comprehensive testing patterns.

## Checklist

**Before Migration:**
- [ ] Identify all `intent { }` blocks in Orbit ViewModel
- [ ] List all method names that trigger intents
- [ ] Document State and SideEffect types
- [ ] Note any Orbit-specific features used (transformers, orbit blocks)

**During Migration:**
- [ ] Keep State and SideEffect unchanged
- [ ] Create Intent sealed interface from method names
- [ ] Convert each `intent { }` to a Processor
- [ ] Create ViewModel extending MviViewModel
- [ ] Setup DI module
- [ ] Update UI to use dispatch

**After Migration:**
- [ ] Remove Orbit dependencies
- [ ] Remove old ViewModel
- [ ] Update tests to use Pulse test utilities
- [ ] Verify all user flows work correctly

## Common Challenges

### Challenge: "Orbit's `reduce { state.X }` vs Pulse's `reduce { copy(X) }`"

**Orbit** uses `state` reference inside reduce:
```kotlin
reduce { state.copy(email = email) }
```

**Pulse** uses receiver:
```kotlin
reduce { copy(email = intent.email) }
```

The `this` inside reduce is the current state in Pulse.

### Challenge: "Multiple intent blocks in sequence"

**Orbit** allows chaining:
```kotlin
fun validateAndSubmit() = intent {
    reduce { state.copy(validating = true) }
}.intent {
    if (isValid(state)) {
        submitForm()
    }
}
```

**Pulse** - split into separate processors:
```kotlin
@Processor
class ValidateFormProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(intent: ...) {
        reduce { copy(validating = true) }
        if (isValid(currentState)) {
            send(SideEffect.TriggerSubmit)
        }
    }
}

@Processor
class SubmitFormProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(intent: ...) {
        // Submit logic
    }
}

// In UI
viewModel.collectSideEffect { effect ->
    when (effect) {
        SideEffect.TriggerSubmit -> viewModel dispatch Intent.SubmitForm
    }
}
```

## Benefits After Migration

✅ **Compile-time safety:** KSP generates type-safe routing
✅ **Better testability:** Each processor tested in isolation
✅ **Explicit intents:** User actions are clearly defined types
✅ **IDE support:** Better autocomplete and refactoring
✅ **No reflection:** All dispatch is compile-time checked

## Resources

- Pulse documentation: https://github.com/antonioimbesi/pulse
- Testing guide: `references/testing-guide.md`
- Anti-patterns: `references/anti-patterns.md`