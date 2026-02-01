# Updating Existing Pulse MVI Features

Guide for adding and modifying components in existing Pulse MVI features.

**Prerequisites:** You already have a working Pulse feature and understand the fundamentals from SKILL.md.

---

## Adding New Intents and Processors

### Step 1: Add Intent to Sealed Interface

**File:** `com.yourapp.feature.contract.FeatureIntent.kt`

**Before:**
```kotlin
package com.yourapp.feature.contract

sealed interface LoginIntent {
    data object Login : LoginIntent
    data class UpdateEmail(val email: String) : LoginIntent
}
```

**After:**
```kotlin
package com.yourapp.feature.contract

sealed interface LoginIntent {
    data object Login : LoginIntent
    data class UpdateEmail(val email: String) : LoginIntent
    data object TogglePasswordVisibility : LoginIntent // NEW
}
```

**Important:**
- Follow user-intent naming (not UI events)
- Use `data object` for no parameters, `data class` for parameters
- Remember: 1 Intent → 1 Processor

### Step 2: Update State (If Needed)

**File:** `com.yourapp.feature.contract.FeatureState.kt`

Only update if the new intent requires new state fields.

**Before:**
```kotlin
package com.yourapp.feature.contract

data class LoginState(
    val email: String = "",
    val isLoading: Boolean = false
)
```

**After:**
```kotlin
package com.yourapp.feature.contract

data class LoginState(
    val email: String = "",
    val isLoading: Boolean = false,
    val isPasswordVisible: Boolean = false // NEW - with default value!
)
```

**CRITICAL:**
- Always add new fields with DEFAULT values
- Never remove fields without deprecation (breaking change)
- Never change field types without careful consideration

### Step 3: Create New Processor

**File:** `com.yourapp.feature.processor.TogglePasswordVisibilityProcessor.kt`

```kotlin
package com.yourapp.feature.processor

import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import com.yourapp.feature.contract.LoginState
import com.yourapp.feature.contract.LoginIntent
import com.yourapp.feature.contract.LoginSideEffect

@Processor
class TogglePasswordVisibilityProcessor : IntentProcessor<LoginState, LoginIntent.TogglePasswordVisibility, LoginSideEffect> {
    
    override suspend fun ProcessorScope<LoginState, LoginSideEffect>.process(
        intent: LoginIntent.TogglePasswordVisibility
    ) {
        reduce { copy(isPasswordVisible = !isPasswordVisible) }
    }
}
```

**Checklist:**
- [ ] Annotated with `@Processor`
- [ ] Implements correct `IntentProcessor<State, SpecificIntent, SideEffect>`
- [ ] Uses `reduce { }` for state updates
- [ ] Uses `send()` for side effects

### Step 4: Update Dependency Injection

#### Hilt (with javax.inject)
**No changes needed!** The executor regenerates automatically.

#### Hilt (without javax.inject)

**File:** `com.yourapp.feature.di.FeatureModule.kt`

Add provider for new processor:

```kotlin
@Provides
fun provideTogglePasswordVisibilityProcessor(): TogglePasswordVisibilityProcessor {
    return TogglePasswordVisibilityProcessor()
}

@Provides
fun provideProcessorExecutor(
    loginProcessor: LoginProcessor,
    updateEmailProcessor: UpdateEmailProcessor,
    togglePasswordVisibilityProcessor: TogglePasswordVisibilityProcessor // ADD
): LoginIntentProcessorExecutor {
    return LoginIntentProcessorExecutor(
        loginProcessor,
        updateEmailProcessor,
        togglePasswordVisibilityProcessor // ADD
    )
}
```

#### Koin

**File:** `com.yourapp.feature.di.FeatureModule.kt`

Add factory definition:

```kotlin
val loginModule = module {
    factoryOf(::LoginProcessor)
    factoryOf(::UpdateEmailProcessor)
    factoryOf(::TogglePasswordVisibilityProcessor) // ADD
    
    factoryOf(::LoginIntentProcessorExecutor) // Koin auto-injects the new processor
    
    factory<MviEngineFactory<LoginState, LoginIntent, LoginSideEffect>> {
        DefaultMviEngineFactory(get<LoginIntentProcessorExecutor>())
    }
    
    viewModelOf(::LoginViewModel)
}
```

#### Manual DI

**File:** `com.yourapp.feature.FeatureViewModel.kt` (ViewModelFactory)

Add processor instantiation:

```kotlin
class LoginViewModelFactory(
    private val authRepository: AuthRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val processor1 = LoginProcessor(authRepository)
        val processor2 = UpdateEmailProcessor()
        val processor3 = TogglePasswordVisibilityProcessor() // ADD
        
        val executor = LoginIntentProcessorExecutor(
            processor1,
            processor2,
            processor3 // ADD
        )
        
        val engineFactory = DefaultMviEngineFactory(executor)
        return LoginViewModel(engineFactory) as T
    }
}
```

### Step 5: Update UI

**File:** `com.yourapp.feature.ui.FeatureScreen.kt`

Add UI element that dispatches the new intent:

```kotlin
@Composable
private fun LoginContent(
    state: LoginState,
    onIntent: (LoginIntent) -> Unit
) {
    // ... existing UI
    
    // NEW: Password visibility toggle
    IconButton(onClick = { onIntent(LoginIntent.TogglePasswordVisibility) }) {
        Icon(
            imageVector = if (state.isPasswordVisible) {
                Icons.Default.VisibilityOff
            } else {
                Icons.Default.Visibility
            },
            contentDescription = "Toggle password visibility"
        )
    }
}
```

### Step 6: Rebuild and Verify

```bash
./gradlew clean build
```

**Verify:**
1. Check `build/generated/ksp/.../FeatureIntentProcessorExecutor.kt` includes new processor
2. Test the new functionality in the app
3. Add tests for the new processor

### Step 7: Add Tests

**File:** `src/test/.../TogglePasswordVisibilityProcessorTest.kt`

```kotlin
package com.yourapp.feature

import com.yourapp.feature.contract.LoginState
import com.yourapp.feature.contract.LoginIntent
import com.yourapp.feature.contract.LoginSideEffect
import com.yourapp.feature.processor.TogglePasswordVisibilityProcessor
import io.github.antonioimbesi.pulse.test.testProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TogglePasswordVisibilityProcessorTest {
    
    @Test
    fun `toggle password visibility from hidden to visible`() = runTest {
        testProcessor(
            initialState = LoginState(isPasswordVisible = false),
            processor = TogglePasswordVisibilityProcessor(),
            intent = LoginIntent.TogglePasswordVisibility
        ) {
            finalState(LoginState(isPasswordVisible = true))
            noSideEffects()
        }
    }
    
    @Test
    fun `toggle password visibility from visible to hidden`() = runTest {
        testProcessor(
            initialState = LoginState(isPasswordVisible = true),
            processor = TogglePasswordVisibilityProcessor(),
            intent = LoginIntent.TogglePasswordVisibility
        ) {
            finalState(LoginState(isPasswordVisible = false))
            noSideEffects()
        }
    }
}
```

---

## Modifying Existing Processors

### Scenario 1: Changing Processor Logic

**When:**
- Business logic changes
- Error handling improvements
- Adding validation
- Performance optimizations

**Process:**
1. Open existing processor file
2. Modify `process()` method
3. Ensure `reduce { }` and `send()` usage is correct
4. Update tests

**Example - Before:**
```kotlin
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository
) : IntentProcessor<LoginState, LoginIntent.Login, LoginSideEffect> {
    override suspend fun ProcessorScope<LoginState, LoginSideEffect>.process(
        intent: LoginIntent.Login
    ) {
        reduce { copy(isLoading = true) }
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Result.Success -> {
                reduce { copy(isLoading = false) }
                send(LoginSideEffect.NavigateToHome)
            }
            is Result.Error -> {
                reduce { copy(isLoading = false, error = result.message) }
            }
        }
    }
}
```

**After (adding validation and analytics):**
```kotlin
@Processor
class LoginProcessor(
    private val authRepository: AuthRepository,
    private val analytics: Analytics // NEW dependency
) : IntentProcessor<LoginState, LoginIntent.Login, LoginSideEffect> {
    override suspend fun ProcessorScope<LoginState, LoginSideEffect>.process(
        intent: LoginIntent.Login
    ) {
        // NEW: Validation
        if (currentState.email.isBlank()) {
            send(LoginSideEffect.ShowError("Email is required"))
            return
        }
        
        reduce { copy(isLoading = true, error = null) } // Clear previous error
        
        // NEW: Track login attempt
        analytics.track("login_attempt", mapOf("email" to currentState.email))
        
        when (val result = authRepository.login(currentState.email, currentState.password)) {
            is Result.Success -> {
                reduce { copy(isLoading = false) }
                analytics.track("login_success") // NEW
                send(LoginSideEffect.NavigateToHome)
            }
            is Result.Error -> {
                reduce { copy(isLoading = false, error = result.message) }
                analytics.track("login_error", mapOf("reason" to result.message)) // NEW
                send(LoginSideEffect.ShowError(result.message)) // NEW
            }
        }
    }
}
```

**Update DI for new dependency:**

**Hilt:**
```kotlin
@Provides
fun provideLoginProcessor(
    authRepository: AuthRepository,
    analytics: Analytics // ADD
): LoginProcessor {
    return LoginProcessor(authRepository, analytics)
}
```

**Koin:**
```kotlin
factoryOf(::LoginProcessor) // Auto-injects Analytics if it's in the graph
```

**Update tests:**
```kotlin
@Test
fun `login with blank email shows error`() = runTest {
    val mockAnalytics = MockAnalytics()
    
    testProcessor(
        initialState = LoginState(email = ""),
        processor = LoginProcessor(mockRepository, mockAnalytics),
        intent = LoginIntent.Login
    ) {
        noStateChanges() // Should not update state
        expectSideEffects(LoginSideEffect.ShowError("Email is required"))
    }
}
```

### Scenario 2: Splitting a Complex Processor

**When:**
- Processor does too much (violates Single Responsibility Principle)
- Logic becomes hard to test
- Multiple concerns in one processor

**Before - One processor doing too much:**
```kotlin
@Processor
class SubmitFormProcessor(
    private val validator: Validator,
    private val repository: Repository,
    private val analytics: Analytics
) : IntentProcessor<FormState, FormIntent.Submit, FormSideEffect> {
    override suspend fun ProcessorScope<FormState, FormSideEffect>.process(
        intent: FormIntent.Submit
    ) {
        // Validation
        val errors = validator.validate(currentState)
        if (errors.isNotEmpty()) {
            reduce { copy(validationErrors = errors) }
            return
        }
        
        // Submission
        reduce { copy(isSubmitting = true) }
        when (val result = repository.submit(currentState.data)) {
            is Result.Success -> {
                analytics.track("form_submitted")
                reduce { copy(isSubmitting = false) }
                send(FormSideEffect.NavigateToSuccess)
            }
            is Result.Error -> {
                analytics.track("form_error")
                reduce { copy(isSubmitting = false, error = result.message) }
            }
        }
    }
}
```

**After - Split into focused processors:**

**Step 1: Update Intent sealed interface:**
```kotlin
sealed interface FormIntent {
    data object ValidateForm : FormIntent // NEW
    data object SubmitForm : FormIntent  // Keep existing
}
```

**Step 2: Create validation processor:**
```kotlin
@Processor
class ValidateFormProcessor(
    private val validator: Validator
) : IntentProcessor<FormState, FormIntent.ValidateForm, FormSideEffect> {
    override suspend fun ProcessorScope<FormState, FormSideEffect>.process(
        intent: FormIntent.ValidateForm
    ) {
        val errors = validator.validate(currentState)
        
        if (errors.isEmpty()) {
            reduce { copy(validationErrors = emptyList()) }
            send(FormSideEffect.TriggerSubmission) // Tell UI to submit
        } else {
            reduce { copy(validationErrors = errors) }
        }
    }
}
```

**Step 3: Simplify submission processor:**
```kotlin
@Processor
class SubmitFormProcessor(
    private val repository: Repository,
    private val analytics: Analytics
) : IntentProcessor<FormState, FormIntent.SubmitForm, FormSideEffect> {
    override suspend fun ProcessorScope<FormState, FormSideEffect>.process(
        intent: FormIntent.SubmitForm
    ) {
        reduce { copy(isSubmitting = true) }
        
        when (val result = repository.submit(currentState.data)) {
            is Result.Success -> {
                analytics.track("form_submitted")
                reduce { copy(isSubmitting = false) }
                send(FormSideEffect.NavigateToSuccess)
            }
            is Result.Error -> {
                analytics.track("form_error")
                reduce { copy(isSubmitting = false, error = result.message) }
            }
        }
    }
}
```

**Step 4: Update UI to validate first:**
```kotlin
// User clicks submit button
Button(onClick = { onIntent(FormIntent.ValidateForm) }) {
    Text("Submit")
}

// Handle validation trigger
viewModel.collectSideEffect { effect ->
    when (effect) {
        FormSideEffect.TriggerSubmission -> {
            viewModel dispatch FormIntent.SubmitForm
        }
        // ... other effects
    }
}
```

**Step 5: Update DI module to include both processors**

---

## Updating State Structure

### Adding Fields

**Always add with default values to maintain backward compatibility:**

**Before:**
```kotlin
data class ProfileState(
    val name: String = "",
    val email: String = ""
)
```

**After:**
```kotlin
data class ProfileState(
    val name: String = "",
    val email: String = "",
    val avatarUrl: String? = null,       // GOOD: nullable with default
    val isVerified: Boolean = false      // GOOD: boolean with default
)
```

**Update processors that use new fields:**
```kotlin
@Processor
class UpdateAvatarProcessor(
    private val repository: Repository
) : IntentProcessor<ProfileState, ProfileIntent.UpdateAvatar, ProfileSideEffect> {
    override suspend fun ProcessorScope<ProfileState, ProfileSideEffect>.process(
        intent: ProfileIntent.UpdateAvatar
    ) {
        reduce { copy(avatarUrl = intent.url) } // Use new field
    }
}
```

### Removing Fields (Breaking Change)

**Step 1: Deprecate first:**
```kotlin
data class ProfileState(
    val name: String = "",
    val email: String = "",
    @Deprecated("No longer used, will be removed in v2.0")
    val oldField: String = ""
)
```

**Step 2: Remove after deprecation period:**
```kotlin
data class ProfileState(
    val name: String = "",
    val email: String = ""
    // oldField removed
)
```

**Step 3: Update all processors that referenced the field**

### Renaming Fields (Breaking Change)

**Use migration property:**
```kotlin
data class ProfileState(
    val displayName: String = "", // NEW name
    val email: String = ""
) {
    @Deprecated("Use displayName instead")
    val name: String get() = displayName // Migration property
}
```

### Restructuring State (Grouping Related Fields)

**Before:**
```kotlin
data class CheckoutState(
    val shippingStreet: String = "",
    val shippingCity: String = "",
    val shippingZip: String = "",
    val billingStreet: String = "",
    val billingCity: String = "",
    val billingZip: String = "",
    val cardNumber: String = "",
    val cardExpiry: String = ""
)
```

**After (nested data classes):**
```kotlin
data class Address(
    val street: String = "",
    val city: String = "",
    val zip: String = ""
)

data class PaymentInfo(
    val cardNumber: String = "",
    val cardExpiry: String = ""
)

data class CheckoutState(
    val shippingAddress: Address = Address(),
    val billingAddress: Address = Address(),
    val paymentInfo: PaymentInfo = PaymentInfo()
)
```

**Update processors:**
```kotlin
// Before
reduce { copy(shippingStreet = newStreet) }

// After
reduce { copy(shippingAddress = shippingAddress.copy(street = newStreet)) }
```

### Migrating from Boolean Flags to Sealed Interface

**When:** You notice impossible states or complex conditional logic in UI.

See `references/anti-patterns.md` for detailed migration guide.

---

## Adding or Modifying Side Effects

### Adding New Side Effect

**Step 1: Add to sealed interface:**

**File:** `com.yourapp.feature.contract.FeatureSideEffect.kt`

```kotlin
sealed interface FeatureSideEffect {
    data object NavigateBack : FeatureSideEffect
    data class ShowToast(val message: String) : FeatureSideEffect // NEW
}
```

**Step 2: Update processors to emit new effect:**
```kotlin
override suspend fun ProcessorScope<State, SideEffect>.process(intent: Intent) {
    // ... logic
    send(FeatureSideEffect.ShowToast("Operation successful")) // NEW
}
```

**Step 3: Update UI to handle new effect:**
```kotlin
viewModel.collectSideEffect { effect ->
    when (effect) {
        FeatureSideEffect.NavigateBack -> navController.popBackStack()
        is FeatureSideEffect.ShowToast -> { // NEW
            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
        }
    }
}
```

### Modifying Existing Side Effect

**Adding data to existing effect:**

**Before:**
```kotlin
data object ShowError : FeatureSideEffect
```

**After:**
```kotlin
data class ShowError(
    val message: String,
    val code: Int? = null // Optional, maintains compatibility
) : FeatureSideEffect
```

**Update processors:**
```kotlin
// Before
send(FeatureSideEffect.ShowError)

// After
send(FeatureSideEffect.ShowError(message = "Failed to load", code = 500))
```

**Update UI handler:**
```kotlin
is FeatureSideEffect.ShowError -> {
    val errorMessage = if (effect.code != null) {
        "${effect.message} (Error ${effect.code})"
    } else {
        effect.message
    }
    snackbarHostState.showSnackbar(errorMessage)
}
```

---

## Update Checklist

**When Adding Intent/Processor:**
- [ ] Added Intent subtype to sealed interface (user-intent naming)
- [ ] Updated State with new fields (with defaults!) if needed
- [ ] Created new processor with `@Processor` annotation
- [ ] Updated DI configuration (if needed for your DI approach)
- [ ] Updated UI to dispatch new intent
- [ ] Rebuilt and verified executor includes new processor
- [ ] Added tests for new processor

**When Modifying Processor:**
- [ ] Updated processor logic
- [ ] Updated DI if new dependencies added
- [ ] Updated tests to cover new behavior
- [ ] Verified all existing tests still pass

**When Updating State:**
- [ ] Added new fields with default values
- [ ] Deprecated fields before removing (if breaking change)
- [ ] Updated all processors using changed fields
- [ ] Updated UI rendering logic
- [ ] Updated tests

**When Updating SideEffects:**
- [ ] Added/modified sealed interface subtypes
- [ ] Updated processors emitting the effects
- [ ] Updated UI to handle new/modified effects
- [ ] Updated tests

---

## Common Mistakes to Avoid

### ❌ Removing State Fields Without Migration
**Wrong:**
```kotlin
// v1
data class State(val oldField: String, val newField: String)

// v2 - BREAKS existing code!
data class State(val newField: String)
```

**Right:**
```kotlin
// v1.1 - Deprecate first
data class State(
    @Deprecated("Use newField") val oldField: String,
    val newField: String
)

// v2.0 - Remove after migration period
data class State(val newField: String)
```

### ❌ Adding State Fields Without Defaults
**Wrong:**
```kotlin
data class State(
    val name: String = "",
    val newField: String // No default - compilation error!
)
```

**Right:**
```kotlin
data class State(
    val name: String = "",
    val newField: String = "" // Has default
)
```

### ❌ Forgetting to Update DI
After adding a processor, if you're using Hilt without javax.inject or Koin/Manual DI, you must update the DI configuration.

### ❌ Not Rebuilding After Changes
Always rebuild after adding processors: `./gradlew clean build`

---

## Next Steps

- **Having issues?** Check `references/troubleshooting.md`
- **Avoid mistakes:** Read `references/anti-patterns.md`
- **Write tests:** See `references/testing-guide.md`
- **Need to migrate?** See appropriate `references/migration-*.md`