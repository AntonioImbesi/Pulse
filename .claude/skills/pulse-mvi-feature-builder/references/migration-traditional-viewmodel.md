# Migration from Traditional ViewModel to Pulse MVI

This guide covers migrating from traditional Android ViewModels using LiveData or StateFlow to Pulse MVI.

## Assessment Phase

Before starting migration, identify your current architecture components:

**What to look for:**
1. ViewModels with LiveData/MutableLiveData or MutableStateFlow
2. Event handling mechanisms (SingleLiveEvent, Channel, SharedFlow, etc.)
3. Business logic location (in ViewModel, separate use cases, repositories)
4. How state is managed (multiple LiveData objects vs single state)

**Example current code to migrate:**
```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()
    
    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email) }
    }
    
    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password) }
    }
    
    fun onLoginClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            when (val result = authRepository.login(_uiState.value.email, _uiState.value.password)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    _events.send(LoginEvent.NavigateToHome)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
            }
        }
    }
}

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
}
```

## Migration Strategy: Gradual Conversion

### Step 1: Add Pulse Dependencies (No Breaking Changes)

```kotlin
// build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
}

dependencies {
    implementation("io.github.antonioimbesi.pulse:pulse-core:$version")
    implementation("io.github.antonioimbesi.pulse:pulse-android:$version")
    ksp("io.github.antonioimbesi.pulse:pulse-compiler:$version")
}
```

This step is **non-breaking** - your existing code continues to work.

### Step 2: Create MVI Contract from Existing State/Events

#### 2.1 Convert State (Usually 1:1 Mapping)

```kotlin
// Old
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

// New (rename or keep same name)
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
```

**Tip:** Consider upgrading to sealed interface pattern during migration:
```kotlin
data class LoginState(
    val email: String = "",
    val password: String = "",
    val loginStatus: LoginStatus = LoginStatus.Idle
) {
    sealed interface LoginStatus {
        data object Idle : LoginStatus
        data object Loading : LoginStatus
        data object Success : LoginStatus
        data class Error(val message: String) : LoginStatus
    }
}
```

#### 2.2 Convert Events to SideEffects

```kotlin
// Old
sealed interface LoginEvent {
    data object NavigateToHome : LoginEvent
}

// New
sealed interface LoginSideEffect {
    data object NavigateToHome : LoginSideEffect
    data class ShowError(val message: String) : LoginSideEffect
}
```

**Key difference:** Events become SideEffects. Same concept, different name in Pulse.

#### 2.3 Create Intents from ViewModel Methods

Analyze your ViewModel methods and convert them to Intents:

```kotlin
// From: fun onEmailChanged(email: String)
// From: fun onPasswordChanged(password: String)
// From: fun onLoginClicked()

sealed interface LoginIntent {
    data class UpdateEmail(val email: String) : LoginIntent
    data class UpdatePassword(val password: String) : LoginIntent
    data object Login : LoginIntent
}
```

**Naming guide:**
- `onXChanged` → `UpdateX` or `ChangeX`
- `onXClicked` → `X` (the action itself)
- `loadX` → `LoadX`
- `refreshX` → `RefreshX`

### Step 3: Extract Logic into Processors

#### 3.1 Simple State Updates → Simple Processors

```kotlin
// Old ViewModel method:
fun onEmailChanged(email: String) {
    _uiState.update { it.copy(email = email) }
}

// New Processor:
@Processor
class UpdateEmailProcessor : IntentProcessor<LoginState, LoginIntent.UpdateEmail, LoginSideEffect> {
    override suspend fun ProcessorScope<LoginState, LoginSideEffect>.process(
        intent: LoginIntent.UpdateEmail
    ) {
        reduce { copy(email = intent.email) }
    }
}
```

#### 3.2 Complex Business Logic → Use Case + Processor

```kotlin
// Old ViewModel method:
fun onLoginClicked() {
    viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true) }
        
        when (val result = authRepository.login(_uiState.value.email, _uiState.value.password)) {
            is Result.Success -> {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.NavigateToHome)
            }
            is Result.Error -> {
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
        }
    }
}

// New Processor (using existing repository):
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor<LoginState, LoginIntent.Login, LoginSideEffect> {
    override suspend fun ProcessorScope<LoginState, LoginSideEffect>.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(isLoading = true, error = null) }
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Result.Success -> {
                reduce { copy(isLoading = false) }
                send(LoginSideEffect.NavigateToHome)
            }
            is Result.Error -> {
                reduce { copy(isLoading = false, error = result.message) }
                send(LoginSideEffect.ShowError(result.message))
            }
        }
    }
}
```

**Key mappings:**
- `_uiState.update { }` → `reduce { }`
- `_uiState.value` → `currentState`
- `_events.send()` → `send()`
- `viewModelScope.launch { }` → Not needed (processors are already suspend)

### Step 4: Convert ViewModel

#### Old ViewModel:
```kotlin
class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    private val _events = Channel<LoginEvent>()
    val events = _events.receiveAsFlow()
    
    fun onEmailChanged(email: String) { ... }
    fun onPasswordChanged(password: String) { ... }
    fun onLoginClicked() { ... }
}
```

#### New ViewModel:
```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    engineFactory: MviEngineFactory<LoginState, LoginIntent, LoginSideEffect>
) : MviViewModel<LoginState, LoginIntent, LoginSideEffect>(
    engineFactory = engineFactory,
    initialState = LoginState()
)
// All logic moved to processors - ViewModel is now just a thin wrapper
```

**What happened:**
- ❌ Removed: All state management code
- ❌ Removed: All event/channel code
- ❌ Removed: All business logic methods
- ✅ Added: MviViewModel inheritance
- ✅ Added: Engine factory injection

### Step 5: Setup Dependency Injection

See main SKILL.md for DI setup based on your framework (Hilt/Koin/Manual).

### Step 6: Update UI

#### Old UI:
```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                LoginEvent.NavigateToHome -> navController.navigate("home")
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

#### New UI:
```kotlin
@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    navController: NavController
) {
    val state by viewModel.collectState()
    
    viewModel.collectSideEffect { effect ->
        when (effect) {
            LoginSideEffect.NavigateToHome -> navController.navigate("home")
            is LoginSideEffect.ShowError -> {
                // Handle error display
            }
        }
    }
    
    LoginContent(
        state = state,
        onIntent = viewModel::dispatch // Single dispatch point
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
- `uiState.collectAsStateWithLifecycle()` → `collectState()`
- `events.collect { }` → `collectSideEffect { }`
- Multiple callback parameters → Single `onIntent` parameter
- Method calls (`viewModel::onXChanged`) → Intent dispatch (`onIntent(Intent.X)`)

## Migration Checklist

**Before migration:**
- [ ] Document all ViewModel methods and their behaviors
- [ ] Identify all state fields and their usage
- [ ] Identify all events/one-off actions
- [ ] Map dependencies (repositories, use cases)
- [ ] Create a test plan for regression testing

**During migration:**
- [ ] Add Pulse dependencies
- [ ] Create State from existing UI state
- [ ] Create SideEffect from existing events
- [ ] Create Intent for each ViewModel method
- [ ] Create one Processor per Intent
- [ ] Convert ViewModel to MviViewModel
- [ ] Setup DI module
- [ ] Update UI to use dispatch instead of method calls

**After migration:**
- [ ] Remove old ViewModel methods
- [ ] Remove old StateFlow/Channel code
- [ ] Test all user flows
- [ ] Verify code generation works
- [ ] Update tests to use Pulse test utilities
- [ ] Remove unused imports and dependencies

## Common Migration Patterns

### Pattern 1: Multiple LiveData → Single State

**Before:**
```kotlin
class ProductViewModel : ViewModel() {
    private val _products = MutableLiveData<List<Product>>()
    val products: LiveData<List<Product>> = _products
    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
}
```

**After:**
```kotlin
data class ProductState(
    val products: List<Product> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ProductViewModel @Inject constructor(
    engineFactory: MviEngineFactory<ProductState, ProductIntent, ProductSideEffect>
) : MviViewModel<ProductState, ProductIntent, ProductSideEffect>(
    engineFactory = engineFactory,
    initialState = ProductState()
)
```

### Pattern 2: SingleLiveEvent → SideEffect

**Before:**
```kotlin
class CheckoutViewModel : ViewModel() {
    private val _navigationEvent = SingleLiveEvent<NavigationEvent>()
    val navigationEvent: LiveData<NavigationEvent> = _navigationEvent
    
    fun onCheckoutComplete() {
        _navigationEvent.value = NavigationEvent.GoToConfirmation
    }
}
```

**After:**
```kotlin
sealed interface CheckoutSideEffect {
    data object NavigateToConfirmation : CheckoutSideEffect
}

@Processor
class CompleteCheckoutProcessor : IntentProcessor<CheckoutState, CheckoutIntent.Complete, CheckoutSideEffect> {
    override suspend fun ProcessorScope<CheckoutState, CheckoutSideEffect>.process(
        intent: CheckoutIntent.Complete
    ) {
        // ... business logic
        send(CheckoutSideEffect.NavigateToConfirmation)
    }
}
```

### Pattern 3: Validation in ViewModel → Validation Processor

**Before:**
```kotlin
class FormViewModel : ViewModel() {
    fun validateAndSubmit() {
        viewModelScope.launch {
            if (_uiState.value.email.isBlank()) {
                _uiState.update { it.copy(emailError = "Email required") }
                return@launch
            }
            
            // Submit...
        }
    }
}
```

**After:**
```kotlin
@Processor
class ValidateAndSubmitProcessor(
    private val validator: Validator
) : IntentProcessor<FormState, FormIntent.Submit, FormSideEffect> {
    override suspend fun ProcessorScope<FormState, FormSideEffect>.process(
        intent: FormIntent.Submit
    ) {
        val errors = validator.validate(currentState)
        
        if (errors.isNotEmpty()) {
            reduce { copy(validationErrors = errors) }
            return
        }
        
        // Proceed with submission
        send(FormSideEffect.TriggerSubmission)
    }
}
```

### Pattern 4: Combine/Zip Multiple Flows → Single Processor with Multiple Data Sources

**Before:**
```kotlin
class DashboardViewModel(
    private val userRepo: UserRepository,
    private val statsRepo: StatsRepository
) : ViewModel() {
    val dashboardData = combine(
        userRepo.getUserFlow(),
        statsRepo.getStatsFlow()
    ) { user, stats ->
        DashboardData(user, stats)
    }.stateIn(viewModelScope, SharingStarted.Lazily, DashboardData())
}
```

**After:**
```kotlin
@Processor
class LoadDashboardProcessor(
    private val userRepo: UserRepository,
    private val statsRepo: StatsRepository
) : IntentProcessor<DashboardState, DashboardIntent.Load, DashboardSideEffect> {
    override suspend fun ProcessorScope<DashboardState, DashboardSideEffect>.process(
        intent: DashboardIntent.Load
    ) {
        reduce { copy(isLoading = true) }
        
        val user = userRepo.getUser()
        val stats = statsRepo.getStats()
        
        reduce { 
            copy(
                isLoading = false,
                user = user,
                stats = stats
            )
        }
    }
}
```

## Troubleshooting Migration

### Issue: "Too many processors, refactor is overwhelming"

**Solution:** Migrate incrementally
1. Start with one screen/feature
2. Keep old ViewModel alongside new one temporarily
3. Switch UI to new ViewModel when ready
4. Delete old ViewModel after verification

### Issue: "Shared state between screens"

**Old approach:**
```kotlin
class SharedViewModel : ViewModel() {
    val sharedData = MutableStateFlow(...)
}
```

**Pulse approach:**
Two options:

**Option 1: Use SideEffects for communication**
```kotlin
// Screen A sends data via SideEffect
send(ScreenASideEffect.ShareData(data))

// In UI, dispatch to Screen B
viewModel.collectSideEffect { effect ->
    when (effect) {
        is ScreenASideEffect.ShareData -> {
            screenBViewModel dispatch ScreenBIntent.ReceiveData(effect.data)
        }
    }
}
```

**Option 2: Use shared repository/cache**
```kotlin
// Both features use the same repository
@Processor
class ScreenAProcessor(
    private val sharedRepo: SharedRepository
) : IntentProcessor<...> {
    override suspend fun ProcessorScope<...>.process(intent: ...) {
        sharedRepo.saveData(data)
    }
}

@Processor
class ScreenBProcessor(
    private val sharedRepo: SharedRepository
) : IntentProcessor<...> {
    override suspend fun ProcessorScope<...>.process(intent: ...) {
        val data = sharedRepo.getData()
        reduce { copy(data = data) }
    }
}
```

### Issue: "Testing is harder now"

**Before:**
```kotlin
@Test
fun testLogin() = runTest {
    val viewModel = LoginViewModel(mockRepo)
    viewModel.onLoginClicked()
    advanceUntilIdle()
    assertEquals(expectedState, viewModel.uiState.value)
}
```

**After (actually easier!):**
```kotlin
@Test
fun testLogin() = runTest {
    testProcessor(
        initialState = LoginState(),
        processor = LoginProcessor(mockRepo),
        intent = LoginIntent.Login
    ) {
        finalState(expectedState)
        expectSideEffects(LoginSideEffect.NavigateToHome)
    }
}
```

See `references/testing-guide.md` for comprehensive testing patterns.

## Benefits After Migration

✅ **Compile-time safety:** Missing intent handlers caught at compile time
✅ **Clear intent:** User actions are explicit (Intents)
✅ **Testable:** Each processor tested in isolation
✅ **Separation of concerns:** Business logic separate from coordination
✅ **Type-safe state:** Sealed interfaces prevent impossible states
✅ **Predictable:** Unidirectional data flow
✅ **Scalable:** Easy to add new intents/processors

## Next Steps

After successful migration:
1. Review `references/anti-patterns.md` to avoid common mistakes
2. Consider upgrading boolean flags to sealed interfaces
3. Add comprehensive tests using `references/testing-guide.md`
4. Share learnings with team for other features
