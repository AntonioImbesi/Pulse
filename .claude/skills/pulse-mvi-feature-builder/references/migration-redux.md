# Migration from Redux-Style Architecture to Pulse MVI

This guide covers migrating from Redux-style architecture (actions, reducers, middleware) to Pulse MVI.

## Conceptual Mapping

| Redux Concept | Pulse Equivalent | Notes |
|---------------|------------------|-------|
| Store | `MviViewModel` + `MviEngine` | Centralized state management |
| Action (user-triggered) | `Intent` | User actions become Intents |
| Action (internal) | N/A (delete) | Internal actions become direct `reduce { }` calls |
| Reducer | `reduce { }` in processors | Reducer logic moves to processors |
| Middleware/Thunk | `IntentProcessor` | Async logic in processors |
| State | `State` | Same concept |
| Side Effect | `SideEffect` | One-time events |

## Key Differences

### 1. **User Actions vs Internal Actions**

**Redux:** Has both user-triggered and internal actions
```kotlin
// User-triggered
data class LoginButtonClicked(val email: String, val password: String) : Action

// Internal (system-triggered)
data class LoginStarted : Action
data class LoginSuccess(val token: String) : Action
data class LoginFailure(val error: String) : Action
```

**Pulse:** Only user-triggered actions become Intents. Internal actions become direct state updates.
```kotlin
sealed interface LoginIntent {
    data object Login : LoginIntent // Only user action
}

// Internal "actions" become direct reduce { } calls in processor:
reduce { copy(isLoading = true) }  // Was: LoginStarted
reduce { copy(token = token) }     // Was: LoginSuccess
reduce { copy(error = error) }     // Was: LoginFailure
```

### 2. **No Global Reducer**

**Redux:** Centralized reducer handles all actions
```kotlin
fun reducer(state: State, action: Action): State {
    return when (action) {
        is LoginStarted -> state.copy(isLoading = true)
        is LoginSuccess -> state.copy(isLoading = false, token = action.token)
        // ... 50 more cases
    }
}
```

**Pulse:** Each processor handles its own state updates
```kotlin
@Processor
class LoginProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(intent: ...) {
        reduce { copy(isLoading = true) }
        // ... logic
        reduce { copy(isLoading = false, token = token) }
    }
}
```

### 3. **Middleware → Processors**

**Redux:** Middleware intercepts actions for async logic
**Pulse:** Processors ARE the async logic handlers

## Migration Steps

### Step 1: Identify Redux Components

**Example Redux Implementation:**
```kotlin
// Actions
sealed interface LoginAction {
    data class SetEmail(val email: String) : LoginAction
    data class SetPassword(val password: String) : LoginAction
    data object StartLogin : LoginAction
    data class LoginSuccess(val token: String) : LoginAction
    data class LoginFailure(val error: String) : LoginAction
}

// State
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val token: String? = null,
    val error: String? = null
)

// Reducer
fun loginReducer(state: LoginState, action: LoginAction): LoginState {
    return when (action) {
        is LoginAction.SetEmail -> state.copy(email = action.email)
        is LoginAction.SetPassword -> state.copy(password = action.password)
        LoginAction.StartLogin -> state.copy(isLoading = true)
        is LoginAction.LoginSuccess -> state.copy(
            isLoading = false,
            token = action.token,
            error = null
        )
        is LoginAction.LoginFailure -> state.copy(
            isLoading = false,
            error = action.error
        )
    }
}

// Middleware/Thunk
class LoginMiddleware(private val authRepository: AuthRepository) {
    suspend fun login(email: String, password: String, dispatch: (LoginAction) -> Unit) {
        dispatch(LoginAction.StartLogin)
        
        when (val result = authRepository.login(email, password)) {
            is Result.Success -> dispatch(LoginAction.LoginSuccess(result.token))
            is Result.Error -> dispatch(LoginAction.LoginFailure(result.message))
        }
    }
}

// Store
class LoginStore(
    private val middleware: LoginMiddleware
) {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow = _state.asStateFlow()
    
    fun dispatch(action: LoginAction) {
        _state.value = loginReducer(_state.value, action)
    }
    
    fun login() {
        viewModelScope.launch {
            middleware.login(
                email = _state.value.email,
                password = _state.value.password,
                dispatch = ::dispatch
            )
        }
    }
}
```

### Step 2: Separate User Actions from Internal Actions

**Analyze each action:**
- User-triggered (button click, text input) → becomes Intent
- Internal (loading started, success, failure) → becomes `reduce { }` call

**From Redux:**
```kotlin
sealed interface LoginAction {
    data class SetEmail(val email: String) : LoginAction        // USER ACTION
    data class SetPassword(val password: String) : LoginAction   // USER ACTION
    data object StartLogin : LoginAction                         // USER ACTION
    data class LoginSuccess(val token: String) : LoginAction     // INTERNAL
    data class LoginFailure(val error: String) : LoginAction     // INTERNAL
}
```

**To Pulse:**
```kotlin
// Only user actions become Intents
sealed interface LoginIntent {
    data class UpdateEmail(val email: String) : LoginIntent
    data class UpdatePassword(val password: String) : LoginIntent
    data object Login : LoginIntent
}

// Internal actions (LoginSuccess, LoginFailure) are deleted!
// They become direct reduce { } calls in processors
```

### Step 3: Convert State (Usually Unchanged)

```kotlin
// State typically stays the same
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val token: String? = null,
    val error: String? = null
)
```

**Consider upgrading to sealed interfaces:**
```kotlin
data class LoginState(
    val email: String = "",
    val password: String = "",
    val loginStatus: LoginStatus = LoginStatus.Idle
) {
    sealed interface LoginStatus {
        data object Idle : LoginStatus
        data object Loading : LoginStatus
        data class Success(val token: String) : LoginStatus
        data class Error(val message: String) : LoginStatus
    }
}
```

### Step 4: Create SideEffects (New Concept)

```kotlin
// SideEffects for one-time events (navigation, toasts, etc.)
sealed interface LoginSideEffect {
    data object NavigateToHome : LoginSideEffect
    data class ShowError(val message: String) : LoginSideEffect
}
```

### Step 5: Convert Middleware → Processors

**Redux Middleware:**
```kotlin
class LoginMiddleware(private val authRepository: AuthRepository) {
    suspend fun login(email: String, password: String, dispatch: (LoginAction) -> Unit) {
        dispatch(LoginAction.StartLogin)
        
        when (val result = authRepository.login(email, password)) {
            is Result.Success -> dispatch(LoginAction.LoginSuccess(result.token))
            is Result.Error -> dispatch(LoginAction.LoginFailure(result.message))
        }
    }
}
```

**Pulse Processor:**
```kotlin
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(isLoading = true) } // Was: dispatch(LoginAction.StartLogin)
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Result.Success -> {
                reduce { copy(isLoading = false, token = result.token) } // Was: LoginSuccess action
                send(LoginSideEffect.NavigateToHome)
            }
            is Result.Error -> {
                reduce { copy(isLoading = false, error = result.message) } // Was: LoginFailure action
                send(LoginSideEffect.ShowError(result.message))
            }
        }
    }
}
```

### Step 6: Convert Simple Reducers → Simple Processors

**Redux Reducer Case:**
```kotlin
is LoginAction.SetEmail -> state.copy(email = action.email)
```

**Pulse Processor:**
```kotlin
@Processor
class UpdateEmailProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.UpdateEmail
    ) {
        reduce { copy(email = intent.email) }
    }
}
```

### Step 7: Delete Reducer (Logic Now in Processors)

```kotlin
// DELETE THIS ENTIRE FUNCTION
fun loginReducer(state: LoginState, action: LoginAction): LoginState {
    return when (action) {
        is LoginAction.SetEmail -> state.copy(email = action.email)
        is LoginAction.SetPassword -> state.copy(password = action.password)
        LoginAction.StartLogin -> state.copy(isLoading = true)
        is LoginAction.LoginSuccess -> state.copy(isLoading = false, token = action.token)
        is LoginAction.LoginFailure -> state.copy(isLoading = false, error = action.error)
    }
}
```

**Why delete?** Each processor handles its own state updates. No centralized reducer needed.

### Step 8: Replace Store with ViewModel

**Redux Store:**
```kotlin
class LoginStore(private val middleware: LoginMiddleware) {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow = _state.asStateFlow()
    
    fun dispatch(action: LoginAction) {
        _state.value = loginReducer(_state.value, action)
    }
    
    fun login() {
        viewModelScope.launch {
            middleware.login(_state.value.email, _state.value.password, ::dispatch)
        }
    }
}
```

**Pulse ViewModel:**
```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    engineFactory: MviEngineFactory
) : MviViewModel(
    engineFactory = engineFactory,
    initialState = LoginState()
)
// That's it! All logic is in processors.
```

### Step 9: Setup Dependency Injection

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
        executor: LoginIntentProcessorExecutor
    ): MviEngineFactory {
        return DefaultMviEngineFactory(executor)
    }
}
```

### Step 10: Update UI

**Redux UI:**
```kotlin
@Composable
fun LoginScreen(store: LoginStore) {
    val state by store.state.collectAsState()
    
    LoginContent(
        state = state,
        onEmailChange = { store.dispatch(LoginAction.SetEmail(it)) },
        onPasswordChange = { store.dispatch(LoginAction.SetPassword(it)) },
        onLoginClick = { store.login() }
    )
}
```

**Pulse UI:**
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
                // Show error toast/snackbar
            }
        }
    }
    
    LoginContent(
        state = state,
        onIntent = viewModel::dispatch
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

## Migration Patterns

### Pattern 1: Action Creator → Intent

**Redux:**
```kotlin
fun login(email: String, password: String) = LoginAction.StartLogin

// Usage
store.dispatch(login(email, password))
```

**Pulse:**
```kotlin
sealed interface LoginIntent {
    data object Login : LoginIntent
}

// Usage
viewModel dispatch LoginIntent.Login
```

### Pattern 2: Async Action Creator (Thunk) → Processor

**Redux Thunk:**
```kotlin
fun loginThunk(email: String, password: String) = { dispatch: (Action) -> Unit ->
    dispatch(LoginAction.StartLogin)
    
    GlobalScope.launch {
        val result = authRepository.login(email, password)
        when (result) {
            is Success -> dispatch(LoginAction.LoginSuccess(result.token))
            is Error -> dispatch(LoginAction.LoginFailure(result.error))
        }
    }
}
```

**Pulse Processor:**
```kotlin
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(isLoading = true) }
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Success -> {
                reduce { copy(isLoading = false, token = result.token) }
                send(LoginSideEffect.NavigateToHome)
            }
            is Error -> {
                reduce { copy(isLoading = false, error = result.error) }
            }
        }
    }
}
```

### Pattern 3: Saga → Multiple Processors with SideEffects

**Redux Saga:**
```kotlin
fun* loginSaga() {
    while (true) {
        val action = take(LoginAction.StartLogin)
        yield put(LoginAction.ShowLoading)
        
        try {
            val token = call(api.login, action.email, action.password)
            yield put(LoginAction.LoginSuccess(token))
            yield put(NavigationAction.NavigateToHome)
        } catch (e: Exception) {
            yield put(LoginAction.LoginFailure(e.message))
            yield put(NotificationAction.ShowError(e.message))
        }
    }
}
```

**Pulse:**
```kotlin
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(isLoading = true) }
        
        try {
            val token = authRepository.login(currentState.email, currentState.password)
            reduce { copy(isLoading = false, token = token) }
            send(LoginSideEffect.NavigateToHome)
        } catch (e: Exception) {
            reduce { copy(isLoading = false, error = e.message) }
            send(LoginSideEffect.ShowError(e.message ?: "Unknown error"))
        }
    }
}
```

### Pattern 4: Combined Reducers → Multiple Features

**Redux:**
```kotlin
fun rootReducer(state: AppState, action: Action): AppState {
    return AppState(
        login = loginReducer(state.login, action),
        profile = profileReducer(state.profile, action),
        settings = settingsReducer(state.settings, action)
    )
}
```

**Pulse:** Each feature has its own ViewModel. No global state combiner needed.
```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(...) : MviViewModel(...)

@HiltViewModel
class ProfileViewModel @Inject constructor(...) : MviViewModel(...)

@HiltViewModel
class SettingsViewModel @Inject constructor(...) : MviViewModel(...)
```

Features communicate via SideEffects or shared repositories, not shared state.

### Pattern 5: Selectors → Computed Properties

**Redux Selector:**
```kotlin
fun selectIsFormValid(state: LoginState): Boolean {
    return state.email.isNotBlank() && state.password.length >= 8
}

// Usage
val isValid = selectIsFormValid(store.state.value)
```

**Pulse:** Add computed properties to State
```kotlin
data class LoginState(
    val email: String = "",
    val password: String = ""
) {
    val isFormValid: Boolean
        get() = email.isNotBlank() && password.length >= 8
}

// Usage
if (state.isFormValid) { ... }
```

## Complete Example

### Before (Redux):
```kotlin
// Actions
sealed interface CounterAction {
    data object Increment : CounterAction
    data object Decrement : CounterAction
    data class IncrementBy(val amount: Int) : CounterAction
    data class ValueChanged(val newValue: Int) : CounterAction  // Internal
}

// State
data class CounterState(val value: Int = 0)

// Reducer
fun counterReducer(state: CounterState, action: CounterAction): CounterState {
    return when (action) {
        CounterAction.Increment -> state.copy(value = state.value + 1)
        CounterAction.Decrement -> state.copy(value = state.value - 1)
        is CounterAction.IncrementBy -> state.copy(value = state.value + action.amount)
        is CounterAction.ValueChanged -> state.copy(value = action.newValue)
    }
}

// Store
class CounterStore {
    private val _state = MutableStateFlow(CounterState())
    val state: StateFlow = _state.asStateFlow()
    
    fun dispatch(action: CounterAction) {
        _state.value = counterReducer(_state.value, action)
    }
}
```

### After (Pulse):
```kotlin
// State - unchanged
data class CounterState(val value: Int = 0)

// Intent - only user actions
sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Decrement : CounterIntent
    data class IncrementBy(val amount: Int) : CounterIntent
}

// SideEffect - none needed for this feature
sealed interface CounterSideEffect

// Processors
@Processor
class IncrementProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: CounterIntent.Increment
    ) {
        reduce { copy(value = value + 1) }
    }
}

@Processor
class DecrementProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: CounterIntent.Decrement
    ) {
        reduce { copy(value = value - 1) }
    }
}

@Processor
class IncrementByProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: CounterIntent.IncrementBy
    ) {
        reduce { copy(value = value + intent.amount) }
    }
}

// ViewModel
@HiltViewModel
class CounterViewModel @Inject constructor(
    engineFactory: MviEngineFactory
) : MviViewModel(
    engineFactory = engineFactory,
    initialState = CounterState()
)
```

**What disappeared:**
- ❌ Reducer function
- ❌ Internal actions (ValueChanged)
- ❌ Store boilerplate

## Migration Checklist

**Before Migration:**
- [ ] List all Actions (user-triggered vs internal)
- [ ] Document Reducer cases
- [ ] Identify Middleware/Thunks/Sagas
- [ ] Map State structure
- [ ] Note any selectors

**During Migration:**
- [ ] Separate user actions → Intents
- [ ] Delete internal actions (become reduce { })
- [ ] Keep State (or upgrade to sealed interfaces)
- [ ] Create SideEffects for one-time events
- [ ] Convert Middleware/Thunks → Processors
- [ ] Convert Reducer cases → Processor reduce { }
- [ ] Delete Reducer function
- [ ] Replace Store with ViewModel
- [ ] Setup DI
- [ ] Update UI

**After Migration:**
- [ ] Remove Redux dependencies
- [ ] Delete all Action types (both user and internal)
- [ ] Delete Reducer
- [ ] Delete Middleware/Thunks
- [ ] Update tests
- [ ] Verify all flows work

## Benefits After Migration

✅ **Simpler mental model:** No internal actions, just direct state updates  
✅ **Better separation:** One processor per user intent  
✅ **No boilerplate:** No action creators, no reducer switch statements  
✅ **Type-safe:** Compile-time routing via KSP  
✅ **Testable:** Test processors in isolation  
✅ **Less code:** Significantly less boilerplate overall

## Key Insight

The biggest conceptual shift from Redux to Pulse:

**Redux:** Everything is an action (user actions + internal actions) → Reducer
**Pulse:** Only user actions are Intents → Processors directly update state

Internal "actions" like `LoadingStarted`, `LoadingComplete`, `DataFetched` don't exist in Pulse. They become direct `reduce { }` calls in the appropriate processor.

## Resources

- Pulse documentation: https://github.com/antonioimbesi/pulse
- Testing guide: `references/testing-guide.md`
- Anti-patterns: `references/anti-patterns.md`