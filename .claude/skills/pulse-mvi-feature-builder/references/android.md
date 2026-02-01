# Pulse Android Integration

This document covers everything Android-specific. When the project targets Android, apply this on top of the core SKILL.md.

## Additional Dependency

```kotlin
dependencies {
    implementation("io.github.antonioimbesi.pulse:pulse-android:$version")
}
```

## MviViewModel

On Android, the MviHost is implemented as an Android ViewModel. This binds the MviEngine lifecycle to `viewModelScope` — when the ViewModel is cleared, the engine stops.

```kotlin
abstract class LoginViewModel(
    engineFactory: MviEngineFactory<LoginState, LoginIntent, LoginSideEffect>
) : MviViewModel<LoginState, LoginIntent, LoginSideEffect>(
    engineFactory = engineFactory,
    initialState = LoginState()
)
```

The ViewModel is a thin wrapper. All logic lives in processors. How the ViewModel is constructed and injected depends on your DI setup — see the appropriate `references/di-*.md`.

## Compose Integration

### Collecting State

Use `collectState()` — lifecycle-aware, pauses collection when below STARTED.

```kotlin
@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.collectState()
    
    when (val status = state.status) {
        LoginState.Status.Idle -> IdleContent()
        LoginState.Status.Loading -> CircularProgressIndicator()
        is LoginState.Status.Success -> SuccessContent(status.token)
        is LoginState.Status.Error -> ErrorContent(status.message)
    }
}
```

### Collecting Side Effects

Use `collectSideEffect { }` — lifecycle-aware, only processes effects when lifecycle is at least STARTED.

```kotlin
viewModel.collectSideEffect { effect ->
    when (effect) {
        LoginSideEffect.NavigateToHome -> navController.navigate("home")
        is LoginSideEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
    }
}
```

**Never** collect side effects manually with `LaunchedEffect`. This causes crashes when performing UI operations while the app is backgrounded.

### Dispatching Intents

Simple screen with one content composable:
```kotlin
LoginContent(
    state = state,
    onIntent = viewModel::dispatch
)
```

Complex screen with specialized composables — use scoped callbacks:
```kotlin
when (val results = state.results) {
    is FeatureState.ResultState.Success -> SuccessContent(
        data = results.data,
        onItemClick = { id -> viewModel dispatch FeatureIntent.SelectItem(id) },
        onRefresh = { viewModel dispatch FeatureIntent.Refresh }
    )
    is FeatureState.ResultState.Error -> ErrorContent(
        message = results.message,
        onRetry = { viewModel dispatch FeatureIntent.Retry }
    )
    // ...
}
```

Each specialized composable receives only the callbacks relevant to its context. Passing `(FeatureIntent) -> Unit` exposes intents that have no business being dispatched from that composable.

## Shared ViewModels Within a Feature

Multiple screens in the same feature can share one ViewModel. This is correct when screens are steps in a single user flow and need access to the same state.

```kotlin
// Checkout is one feature with three screens
class CheckoutViewModel(
    engineFactory: MviEngineFactory<CheckoutState, CheckoutIntent, CheckoutSideEffect>
) : MviViewModel<CheckoutState, CheckoutIntent, CheckoutSideEffect>(
    engineFactory = engineFactory,
    initialState = CheckoutState()
)

// All three screens receive the same ViewModel instance
@Composable
fun ShippingScreen(viewModel: CheckoutViewModel) { /* uses state.shippingAddress */ }

@Composable
fun PaymentScreen(viewModel: CheckoutViewModel) { /* uses state.paymentMethod */ }

@Composable
fun ReviewScreen(viewModel: CheckoutViewModel) { /* uses full state */ }
```

This is distinct from sharing state across different features, which should use navigation arguments or a shared repository instead.

## Cross-Feature Communication

Different features do not share ViewModels. They communicate through:

**Navigation with arguments** — for one-time data transfer between features:
```kotlin
// Feature A emits side effect
send(LoginSideEffect.NavigateToHome(userId = "123"))

// Feature A UI handles navigation
viewModel.collectSideEffect { effect ->
    when (effect) {
        is LoginSideEffect.NavigateToHome -> navController.navigate("home/${effect.userId}")
    }
}

// Feature B receives data via navigation args
@Composable
fun HomeScreen(userId: String) {
    val viewModel: HomeViewModel = // injected
    LaunchedEffect(userId) {
        viewModel dispatch HomeIntent.Init
    }
}
```

**Shared repository** — for reactive data shared across features:
```kotlin
class UserRepository {
    private val _currentUser = MutableStateFlow<User?>(null)
    fun observeCurrentUser(): Flow<User?> = _currentUser.asStateFlow()
    fun setCurrentUser(user: User) { _currentUser.value = user }
}

// Login feature writes
@Processor
class LoginProcessor(private val userRepo: UserRepository) : IntentProcessor<...> {
    override suspend fun ProcessorScope<...>.process(intent: LoginIntent.Login) {
        val user = authRepo.login(...)
        userRepo.setCurrentUser(user)
        send(LoginSideEffect.NavigateToHome)
    }
}

// Profile feature reads
@Processor
class ObserveUserProcessor(private val userRepo: UserRepository) : IntentProcessor<...> {
    override suspend fun ProcessorScope<...>.process(intent: ProfileIntent.Init) {
        userRepo.observeCurrentUser().collect { user ->
            reduce { copy(user = user) }
        }
    }
}
```

## Android Package Structure

```
com.yourapp.feature/
├── contract/
│   ├── FeatureState.kt
│   ├── FeatureIntent.kt
│   └── FeatureSideEffect.kt
├── processor/
│   ├── InitProcessor.kt
│   └── UpdateEmailProcessor.kt
├── di/
│   └── FeatureModule.kt          // Only present if using DI
├── ui/
│   └── FeatureScreen.kt
├── generated/ (KSP auto-generated)
│   └── FeatureIntentProcessorExecutor.kt
└── FeatureViewModel.kt
```
