# Migration from MVIKotlin to Pulse MVI

This guide covers migrating from MVIKotlin framework to Pulse MVI.

## Conceptual Mapping

| MVIKotlin Concept | Pulse Equivalent | Notes |
|-------------------|------------------|-------|
| `Store` | `MviViewModel` + `MviEngine` | Store becomes ViewModel |
| `Intent` | `Intent` (sealed interface) | Same concept, different structure |
| `Action` | N/A (not needed) | Internal actions become direct reduce { } calls |
| `Message` | N/A (not needed) | Messages are eliminated |
| `State` | `State` | Same concept |
| `Label` | `SideEffect` | Rename only |
| `Reducer` | `reduce { }` in processors | Reducer logic moves to processors |
| `Executor` | `IntentProcessor` | One Intent → One Processor |
| `BootStrapper` | Init logic in processor | Rarely needed |

## Key Differences

### 1. **No Messages/Actions Layer**

**MVIKotlin:** Has Intent → Action → Message → State flow
**Pulse:** Has Intent → State flow (simpler!)

**MVIKotlin:**
```kotlin
Intent.EmailChanged → Action.UpdateEmail → Message.EmailUpdated → reduce(Message)
```

**Pulse:**
```kotlin
Intent.UpdateEmail → reduce { copy(email = ...) }
```

### 2. **No Separate Reducer**

**MVIKotlin:** Reducer is a separate component
**Pulse:** Reduction happens inline in processors with `reduce { }`

### 3. **One Executor → Multiple Processors**

**MVIKotlin:** One executor handles all intents
**Pulse:** One processor per intent type (better separation)

## Migration Steps

### Step 1: Identify MVIKotlin Components

**Example MVIKotlin Store:**
```kotlin
class LoginStoreFactory(
    private val storeFactory: StoreFactory,
    private val authRepository: AuthRepository
) {
    fun create(): LoginStore = object : LoginStore, Store by storeFactory.create(
        name = "LoginStore",
        initialState = State(),
        reducer = ReducerImpl,
        executorFactory = ::ExecutorImpl
    ) {}
    
    private inner class ExecutorImpl : CoroutineExecutor() {
        override fun executeIntent(intent: Intent, getState: () -> State) {
            when (intent) {
                is Intent.UpdateEmail -> dispatch(Message.EmailUpdated(intent.email))
                is Intent.UpdatePassword -> dispatch(Message.PasswordUpdated(intent.password))
                Intent.Login -> login(getState())
            }
        }
        
        private fun login(state: State) {
            scope.launch {
                dispatch(Message.LoadingStarted)
                when (val result = authRepository.login(state.email, state.password)) {
                    is Result.Success -> {
                        dispatch(Message.LoadingStopped)
                        publish(Label.NavigateToHome)
                    }
                    is Result.Error -> {
                        dispatch(Message.LoadingStopped)
                        dispatch(Message.ErrorOccurred(result.message))
                    }
                }
            }
        }
    }
    
    private object ReducerImpl : Reducer {
        override fun State.reduce(msg: Message): State = when (msg) {
            is Message.EmailUpdated -> copy(email = msg.email)
            is Message.PasswordUpdated -> copy(password = msg.password)
            Message.LoadingStarted -> copy(isLoading = true)
            Message.LoadingStopped -> copy(isLoading = false)
            is Message.ErrorOccurred -> copy(error = msg.message)
        }
    }
    
    sealed interface Intent {
        data class UpdateEmail(val email: String) : Intent
        data class UpdatePassword(val password: String) : Intent
        data object Login : Intent
    }
    
    sealed interface Message {
        data class EmailUpdated(val email: String) : Message
        data class PasswordUpdated(val password: String) : Message
        data object LoadingStarted : Message
        data object LoadingStopped : Message
        data class ErrorOccurred(val message: String) : Message
    }
    
    sealed interface Label {
        data object NavigateToHome : Label
    }
    
    data class State(
        val email: String = "",
        val password: String = "",
        val isLoading: Boolean = false,
        val error: String? = null
    )
}
```

### Step 2: Map to Pulse Structure

#### 2.1 Keep State (unchanged)

```kotlin
data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)
```

#### 2.2 Keep Intent (unchanged structure, may rename subtypes)

```kotlin
sealed interface LoginIntent {
    data class UpdateEmail(val email: String) : LoginIntent
    data class UpdatePassword(val password: String) : LoginIntent
    data object Login : LoginIntent
}
```

#### 2.3 Convert Label → SideEffect (rename only)

```kotlin
// Old: Label
sealed interface Label {
    data object NavigateToHome : Label
}

// New: SideEffect
sealed interface LoginSideEffect {
    data object NavigateToHome : LoginSideEffect
}
```

#### 2.4 DELETE Messages (not needed in Pulse!)

```kotlin
// DELETE THIS ENTIRE SEALED INTERFACE
sealed interface Message {
    data class EmailUpdated(val email: String) : Message
    data class PasswordUpdated(val password: String) : Message
    data object LoadingStarted : Message
    data object LoadingStopped : Message
    data class ErrorOccurred(val message: String) : Message
}
```

**Why delete?** In Pulse, state updates are direct via `reduce { }`. No intermediate messages needed.

#### 2.5 Convert Executor → Processors

**Key insight:** Each `Intent` handler in executor becomes a separate `Processor`.

**From executeIntent branch:**
```kotlin
is Intent.UpdateEmail -> dispatch(Message.EmailUpdated(intent.email))
```

**To Processor:**
```kotlin
@Processor
class UpdateEmailProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.UpdateEmail
    ) {
        reduce { copy(email = intent.email) } // Direct update, no Message!
    }
}
```

**From executeIntent complex logic:**
```kotlin
Intent.Login -> login(getState())

private fun login(state: State) {
    scope.launch {
        dispatch(Message.LoadingStarted)
        when (val result = authRepository.login(state.email, state.password)) {
            is Result.Success -> {
                dispatch(Message.LoadingStopped)
                publish(Label.NavigateToHome)
            }
            is Result.Error -> {
                dispatch(Message.LoadingStopped)
                dispatch(Message.ErrorOccurred(result.message))
            }
        }
    }
}
```

**To Processor:**
```kotlin
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(isLoading = true) } // No Message.LoadingStarted!
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Result.Success -> {
                reduce { copy(isLoading = false) } // No Message.LoadingStopped!
                send(LoginSideEffect.NavigateToHome) // Label → send
            }
            is Result.Error -> {
                reduce { copy(isLoading = false, error = result.message) } // Direct!
            }
        }
    }
}
```

#### 2.6 DELETE Reducer (logic is now in processors!)

```kotlin
// DELETE THIS ENTIRE REDUCER
private object ReducerImpl : Reducer {
    override fun State.reduce(msg: Message): State = when (msg) {
        is Message.EmailUpdated -> copy(email = msg.email)
        is Message.PasswordUpdated -> copy(password = msg.password)
        Message.LoadingStarted -> copy(isLoading = true)
        Message.LoadingStopped -> copy(isLoading = false)
        is Message.ErrorOccurred -> copy(error = msg.message)
    }
}
```

**Why delete?** Each processor handles its own state updates directly.

#### 2.7 Replace Store with ViewModel

**Old:**
```kotlin
class LoginStoreFactory(...)  {
    fun create(): LoginStore = ...
}

// In ViewModel
class LoginViewModel(storeFactory: LoginStoreFactory) : ViewModel() {
    private val store = storeFactory.create()
    val state = store.state
    val labels = store.labels
    
    fun accept(intent: Intent) = store.accept(intent)
}
```

**New:**
```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    engineFactory: MviEngineFactory
) : MviViewModel(
    engineFactory = engineFactory,
    initialState = LoginState()
)
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
        executor: LoginIntentProcessorExecutor // Auto-generated
    ): MviEngineFactory {
        return DefaultMviEngineFactory(executor)
    }
}
```

### Step 4: Update UI

**MVIKotlin UI:**
```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.labels.collect { label ->
            when (label) {
                LoginLabel.NavigateToHome -> navController.navigate("home")
            }
        }
    }
    
    LoginContent(
        state = state,
        onIntent = viewModel::accept
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
        }
    }
    
    LoginContent(
        state = state,
        onIntent = viewModel::dispatch
    )
}
```

**Key changes:**
- `store.state` → `viewModel.collectState()`
- `store.labels` → `viewModel.collectSideEffect { }`
- `store.accept(intent)` → `viewModel dispatch intent`

## Migration Patterns

### Pattern 1: Actions → Delete Them

**MVIKotlin:**
```kotlin
sealed interface Action {
    data class LoadUser(val id: String) : Action
}

override fun executeAction(action: Action, getState: () -> State) {
    when (action) {
        is Action.LoadUser -> {
            scope.launch {
                val user = repository.getUser(action.id)
                dispatch(Message.UserLoaded(user))
            }
        }
    }
}
```

**Pulse:** Actions don't exist. Convert to Intent + Processor:
```kotlin
sealed interface ProfileIntent {
    data class LoadUser(val id: String) : ProfileIntent
}

@Processor
class LoadUserProcessor(
    private val repository: Repository
) : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: ProfileIntent.LoadUser
    ) {
        val user = repository.getUser(intent.id)
        reduce { copy(user = user) } // Direct, no Message!
    }
}
```

### Pattern 2: BootStrapper → Init Processor or ViewModel Init

**MVIKotlin:**
```kotlin
private class BootStrapperImpl : CoroutineBootstrapper() {
    override fun invoke() {
        scope.launch {
            dispatch(Action.Init)
        }
    }
}
```

**Pulse Option 1:** Init in ViewModel
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    engineFactory: MviEngineFactory
) : MviViewModel(...) {
    init {
        dispatch(MyIntent.Init)
    }
}
```

**Pulse Option 2:** Init processor
```kotlin
@Processor
class InitProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(intent: MyIntent.Init) {
        // Initialization logic
    }
}
```

### Pattern 3: Multiple Messages for One Intent → Single Processor

**MVIKotlin:**
```kotlin
Intent.Submit -> {
    dispatch(Message.ValidationStarted)
    if (isValid(getState())) {
        dispatch(Message.ValidationPassed)
        dispatch(Message.SubmissionStarted)
        submit()
        dispatch(Message.SubmissionCompleted)
    } else {
        dispatch(Message.ValidationFailed)
    }
}
```

**Pulse:**
```kotlin
@Processor
class SubmitProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: FormIntent.Submit
    ) {
        reduce { copy(isValidating = true) }
        
        if (isValid(currentState)) {
            reduce { copy(isValidating = false, isSubmitting = true) }
            submit()
            reduce { copy(isSubmitting = false) }
        } else {
            reduce { copy(isValidating = false, validationError = "Invalid") }
        }
    }
}
```

### Pattern 4: Complex Reducer → Multiple Small Processors

**MVIKotlin:**
```kotlin
private object ReducerImpl : Reducer {
    override fun State.reduce(msg: Message): State = when (msg) {
        is Message.EmailUpdated -> copy(email = msg.email)
        is Message.PasswordUpdated -> copy(password = msg.password)
        is Message.ShowPasswordChanged -> copy(showPassword = !showPassword)
        Message.LoadingStarted -> copy(isLoading = true)
        Message.LoadingStopped -> copy(isLoading = false)
        is Message.ErrorOccurred -> copy(error = msg.message)
        is Message.UserLoggedIn -> copy(isLoading = false, token = msg.token)
        // ... many more cases
    }
}
```

**Pulse:** Each case becomes its own focused processor. The reducer disappears entirely - logic is distributed to processors.

## Complete Example

### Before (MVIKotlin):
```kotlin
class CounterStoreFactory(private val storeFactory: StoreFactory) {
    fun create() = object : CounterStore, Store by storeFactory.create(
        name = "CounterStore",
        initialState = State(0),
        reducer = ReducerImpl,
        executorFactory = ::ExecutorImpl
    ) {}
    
    private class ExecutorImpl : CoroutineExecutor() {
        override fun executeIntent(intent: Intent, getState: () -> State) {
            when (intent) {
                Intent.Increment -> dispatch(Message.ValueChanged(getState().value + 1))
                Intent.Decrement -> dispatch(Message.ValueChanged(getState().value - 1))
                Intent.Reset -> dispatch(Message.ValueChanged(0))
            }
        }
    }
    
    private object ReducerImpl : Reducer {
        override fun State.reduce(msg: Message): State = when (msg) {
            is Message.ValueChanged -> copy(value = msg.value)
        }
    }
    
    sealed interface Intent {
        data object Increment : Intent
        data object Decrement : Intent
        data object Reset : Intent
    }
    
    sealed interface Message {
        data class ValueChanged(val value: Int) : Message
    }
    
    sealed interface Label
    
    data class State(val value: Int)
}
```

### After (Pulse):
```kotlin
// State - unchanged
data class CounterState(val value: Int = 0)

// Intent - unchanged
sealed interface CounterIntent {
    data object Increment : CounterIntent
    data object Decrement : CounterIntent
    data object Reset : CounterIntent
}

// SideEffect - empty, none needed
sealed interface CounterSideEffect

// Processors - one per intent
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
class ResetProcessor : IntentProcessor {
    override suspend fun ProcessorScope.process(
        intent: CounterIntent.Reset
    ) {
        reduce { copy(value = 0) }
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
- ❌ StoreFactory
- ❌ Executor
- ❌ Messages
- ❌ Reducer
- ❌ Boilerplate store creation

**What's cleaner:**
- ✅ Each processor is focused and testable
- ✅ No message layer (direct state updates)
- ✅ Compile-time routing via KSP
- ✅ Simpler ViewModel

## Testing Migration

### MVIKotlin Test:
```kotlin
@Test
fun testIncrement() = runTest {
    val store = CounterStoreFactory(DefaultStoreFactory()).create()
    
    store.accept(Intent.Increment)
    
    assertEquals(State(1), store.state.value)
}
```

### Pulse Test:
```kotlin
@Test
fun testIncrement() = runTest {
    testProcessor(
        initialState = CounterState(0),
        processor = IncrementProcessor(),
        intent = CounterIntent.Increment
    ) {
        finalState(CounterState(1))
    }
}
```

See `references/testing-guide.md` for comprehensive testing patterns.

## Migration Checklist

**Before Migration:**
- [ ] Document all Intents, Messages, Labels, and State
- [ ] Identify all executeIntent cases in Executor
- [ ] Map reducer cases to their triggering messages
- [ ] Note any BootStrapper logic

**During Migration:**
- [ ] Keep State unchanged (or upgrade to sealed interfaces)
- [ ] Keep Intent structure (may rename subtypes)
- [ ] Rename Label → SideEffect
- [ ] DELETE Message sealed interface entirely
- [ ] Convert each executeIntent case → Processor
- [ ] DELETE Reducer entirely (logic moves to processors)
- [ ] Replace Store with MviViewModel
- [ ] Setup DI module
- [ ] Update UI collection

**After Migration:**
- [ ] Remove MVIKotlin dependencies
- [ ] Delete StoreFactory classes
- [ ] Update all tests to use Pulse test utilities
- [ ] Verify all user flows work

## Common Challenges

### Challenge: "Messages gave me fine-grained state updates"

**MVIKotlin way:**
```kotlin
Message.LoadingStarted → copy(isLoading = true)
Message.LoadingStopped → copy(isLoading = false)
Message.ErrorOccurred → copy(error = msg.error)
```

**Pulse way:** Just update state directly in processor
```kotlin
reduce { copy(isLoading = true) }
// ... do work
reduce { copy(isLoading = false) }
// Or if error:
reduce { copy(isLoading = false, error = error.message) }
```

You don't need messages as an intermediate step.

### Challenge: "Reducer was centralized, now it's distributed"

This is **by design**. In Pulse:
- Each processor handles its own state updates
- Better testability (test each processor in isolation)
- Better separation of concerns (no giant when expression)
- Easier to understand (intent logic is colocated with state updates)

### Challenge: "How do I share logic between processors?"

**Extract to use cases:**
```kotlin
class ValidateEmailUseCase {
    operator fun invoke(email: String): Boolean {
        return email.contains("@")
    }
}

@Processor
class UpdateEmailProcessor(
    private val validateEmail: ValidateEmailUseCase
) : IntentProcessor {
    override suspend fun ProcessorScope.process(intent: ...) {
        if (validateEmail(intent.email)) {
            reduce { copy(email = intent.email, emailError = null) }
        } else {
            reduce { copy(emailError = "Invalid email") }
        }
    }
}
```

## Benefits After Migration

✅ **Simpler architecture:** No Messages, Actions, or Reducer layer  
✅ **Better separation:** One processor per intent (SRP)  
✅ **Compile-time safety:** KSP-generated routing  
✅ **Easier testing:** Test processors in isolation  
✅ **Less boilerplate:** No StoreFactory, no complex setup  
✅ **Better IDE support:** Jump to definition works for processors

## Resources

- Pulse documentation: https://github.com/antonioimbesi/pulse
- Testing guide: `references/testing-guide.md`
- Anti-patterns: `references/anti-patterns.md`