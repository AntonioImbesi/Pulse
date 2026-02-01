# Creating New Pulse MVI Features

Step-by-step guide for creating a new feature from scratch using Pulse MVI.

**Prerequisites:** Read the fundamentals in the main SKILL.md first.

---

## Step 1: Define the MVI Contract

Create three files in the `contract/` package:

### 1.1 Create State

**File:** `com.yourapp.feature.contract.FeatureState.kt`

```kotlin
package com.yourapp.feature.contract

data class FeatureState(
    val query: String = "",
    val results: ResultState = ResultState.Idle
) {
    sealed interface ResultState {
        data object Idle : ResultState
        data object Loading : ResultState
        data object Empty : ResultState
        data class Success(
            val data: List<Item>,
            val isRefreshing: Boolean = false
        ) : ResultState
        data class Error(val message: String) : ResultState
    }
}
```

### 1.2 Create Intent

**File:** `com.yourapp.feature.contract.FeatureIntent.kt`

```kotlin
package com.yourapp.feature.contract

sealed interface FeatureIntent {
    data object LoadData : FeatureIntent
    data class UpdateQuery(val query: String) : FeatureIntent
    data class SubmitForm(val payload: DataType) : FeatureIntent
    data object RetryOperation : FeatureIntent
}
```

### 1.3 Create SideEffect

**File:** `com.yourapp.feature.contract.FeatureSideEffect.kt`

```kotlin
package com.yourapp.feature.contract

sealed interface FeatureSideEffect {
    data object NavigateBack : FeatureSideEffect
    data class NavigateToDetail(val id: String) : FeatureSideEffect
    data class ShowError(val message: String) : FeatureSideEffect
    data object ShowSuccess : FeatureSideEffect
}
```

---

## Step 2: Create Processors

Create one processor per intent in the `processor/` package:

### Example: Load Data Processor

**File:** `com.yourapp.feature.processor.LoadDataProcessor.kt`

```kotlin
package com.yourapp.feature.processor

import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect

@Processor
class LoadDataProcessor(
    private val repository: DataRepository
) : IntentProcessor<FeatureState, FeatureIntent.LoadData, FeatureSideEffect> {
    
    override suspend fun ProcessorScope<FeatureState, FeatureSideEffect>.process(
        intent: FeatureIntent.LoadData
    ) {
        reduce { copy(results = FeatureState.ResultState.Loading) }
        
        when (val result = repository.loadData()) {
            is Result.Success -> {
                reduce { 
                    copy(results = FeatureState.ResultState.Success(data = result.data))
                }
                send(FeatureSideEffect.ShowSuccess)
            }
            is Result.Error -> {
                reduce { 
                    copy(results = FeatureState.ResultState.Error(message = result.message))
                }
                send(FeatureSideEffect.ShowError(result.message))
            }
        }
    }
}
```

### Example: Update Query Processor

**File:** `com.yourapp.feature.processor.UpdateQueryProcessor.kt`

```kotlin
package com.yourapp.feature.processor

import io.github.antonioimbesi.pulse.core.processor.IntentProcessor
import io.github.antonioimbesi.pulse.core.processor.Processor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect

@Processor
class UpdateQueryProcessor : IntentProcessor<FeatureState, FeatureIntent.UpdateQuery, FeatureSideEffect> {
    
    override suspend fun ProcessorScope<FeatureState, FeatureSideEffect>.process(
        intent: FeatureIntent.UpdateQuery
    ) {
        reduce { copy(query = intent.query) }
    }
}
```

**Repeat for each Intent type** - remember the 1:1 mapping!

---

## Step 3: Create ViewModel

**File:** `com.yourapp.feature.FeatureViewModel.kt`

Choose your DI approach:

### Option A: Hilt (Recommended)

```kotlin
package com.yourapp.feature

import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.antonioimbesi.pulse.android.MviViewModel
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import javax.inject.Inject
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect

@HiltViewModel
class FeatureViewModel @Inject constructor(
    engineFactory: MviEngineFactory<FeatureState, FeatureIntent, FeatureSideEffect>
) : MviViewModel<FeatureState, FeatureIntent, FeatureSideEffect>(
    engineFactory = engineFactory,
    initialState = FeatureState()
)
```

### Option B: Koin

```kotlin
package com.yourapp.feature

import io.github.antonioimbesi.pulse.android.MviViewModel
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect

class FeatureViewModel(
    engineFactory: MviEngineFactory<FeatureState, FeatureIntent, FeatureSideEffect>
) : MviViewModel<FeatureState, FeatureIntent, FeatureSideEffect>(
    engineFactory = engineFactory,
    initialState = FeatureState()
)
```

### Option C: Manual DI

```kotlin
package com.yourapp.feature

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.antonioimbesi.pulse.android.MviViewModel
import io.github.antonioimbesi.pulse.core.DefaultMviEngineFactory
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect
import com.yourapp.feature.generated.FeatureIntentProcessorExecutor
import com.yourapp.feature.processor.LoadDataProcessor
import com.yourapp.feature.processor.UpdateQueryProcessor

class FeatureViewModel(
    engineFactory: MviEngineFactory<FeatureState, FeatureIntent, FeatureSideEffect>
) : MviViewModel<FeatureState, FeatureIntent, FeatureSideEffect>(
    engineFactory = engineFactory,
    initialState = FeatureState()
)

class FeatureViewModelFactory(
    private val repository: DataRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val loadDataProcessor = LoadDataProcessor(repository)
        val updateQueryProcessor = UpdateQueryProcessor()
        
        val executor = FeatureIntentProcessorExecutor(loadDataProcessor, updateQueryProcessor)
        val engineFactory = DefaultMviEngineFactory(executor)
        
        return FeatureViewModel(engineFactory) as T
    }
}
```

### Option D: No DI

```kotlin
package com.yourapp.feature

import io.github.antonioimbesi.pulse.android.MviViewModel
import io.github.antonioimbesi.pulse.core.DefaultMviEngineFactory
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect
import com.yourapp.feature.generated.FeatureIntentProcessorExecutor
import com.yourapp.feature.processor.LoadDataProcessor
import com.yourapp.feature.processor.UpdateQueryProcessor

class FeatureViewModel : MviViewModel<FeatureState, FeatureIntent, FeatureSideEffect>(
    engineFactory = DefaultMviEngineFactory(
        FeatureIntentProcessorExecutor(
            LoadDataProcessor(),
            UpdateQueryProcessor()
        )
    ),
    initialState = FeatureState()
)
```

---

## Step 4: Setup Dependency Injection

**File:** `com.yourapp.feature.di.FeatureModule.kt`

### Option A: Hilt (Recommended)

#### With javax.inject on classpath (Simplest):

```kotlin
package com.yourapp.feature.di

import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureSideEffect
import com.yourapp.feature.generated.FeatureIntentProcessorExecutor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.github.antonioimbesi.pulse.core.DefaultMviEngineFactory
import io.github.antonioimbesi.pulse.core.MviEngineFactory

@Module
@InstallIn(ViewModelComponent::class)
object FeatureModule {

    @Provides
    fun provideEngineFactory(
        executor: FeatureIntentProcessorExecutor // Auto-injected via @Inject
    ): MviEngineFactory<FeatureState, FeatureIntent, FeatureSideEffect> {
        return DefaultMviEngineFactory(executor)
    }
}
```

#### Without javax.inject (Manual providers):

```kotlin
package com.yourapp.feature.di

import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureSideEffect
import com.yourapp.feature.generated.FeatureIntentProcessorExecutor
import com.yourapp.feature.processor.LoadDataProcessor
import com.yourapp.feature.processor.UpdateQueryProcessor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import io.github.antonioimbesi.pulse.core.DefaultMviEngineFactory
import io.github.antonioimbesi.pulse.core.MviEngineFactory

@Module
@InstallIn(ViewModelComponent::class)
object FeatureModule {

    @Provides
    fun provideLoadDataProcessor(
        repository: DataRepository
    ): LoadDataProcessor {
        return LoadDataProcessor(repository)
    }

    @Provides
    fun provideUpdateQueryProcessor(): UpdateQueryProcessor {
        return UpdateQueryProcessor()
    }

    @Provides
    fun provideProcessorExecutor(
        loadDataProcessor: LoadDataProcessor,
        updateQueryProcessor: UpdateQueryProcessor
    ): FeatureIntentProcessorExecutor {
        return FeatureIntentProcessorExecutor(loadDataProcessor, updateQueryProcessor)
    }

    @Provides
    fun provideEngineFactory(
        executor: FeatureIntentProcessorExecutor
    ): MviEngineFactory<FeatureState, FeatureIntent, FeatureSideEffect> {
        return DefaultMviEngineFactory(executor)
    }
}
```

### Option B: Koin

**File:** `com.yourapp.feature.di.FeatureModule.kt`

```kotlin
package com.yourapp.feature.di

import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureSideEffect
import com.yourapp.feature.FeatureViewModel
import com.yourapp.feature.generated.FeatureIntentProcessorExecutor
import com.yourapp.feature.processor.LoadDataProcessor
import com.yourapp.feature.processor.UpdateQueryProcessor
import io.github.antonioimbesi.pulse.core.DefaultMviEngineFactory
import io.github.antonioimbesi.pulse.core.MviEngineFactory
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val featureModule = module {
    // 1. Processors
    factoryOf(::LoadDataProcessor)
    factoryOf(::UpdateQueryProcessor)
    
    // 2. Executor (auto-generated)
    factoryOf(::FeatureIntentProcessorExecutor)
    
    // 3. Engine Factory (explicit type binding required)
    factory<MviEngineFactory<FeatureState, FeatureIntent, FeatureSideEffect>> {
        DefaultMviEngineFactory(get<FeatureIntentProcessorExecutor>())
    }
    
    // 4. ViewModel
    viewModelOf(::FeatureViewModel)
}
```

**In Application class:**
```kotlin
startKoin {
    modules(featureModule)
}
```

### Option C & D: Manual/No DI

No module needed - DI is handled in ViewModel or ViewModelFactory.

---

## Step 5: Create UI

**File:** `com.yourapp.feature.ui.FeatureScreen.kt`

```kotlin
package com.yourapp.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.yourapp.feature.FeatureViewModel
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect
import io.github.antonioimbesi.pulse.android.collectState
import io.github.antonioimbesi.pulse.android.collectSideEffect

@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = hiltViewModel(),
    navController: NavController
) {
    val state by viewModel.collectState()
    
    // Handle side effects
    viewModel.collectSideEffect { effect ->
        when (effect) {
            FeatureSideEffect.NavigateBack -> {
                navController.popBackStack()
            }
            is FeatureSideEffect.NavigateToDetail -> {
                navController.navigate("detail/${effect.id}")
            }
            is FeatureSideEffect.ShowError -> {
                // Show snackbar/toast
            }
            FeatureSideEffect.ShowSuccess -> {
                // Show success message
            }
        }
    }
    
    FeatureContent(
        state = state,
        onIntent = viewModel::dispatch
    )
}

@Composable
private fun FeatureContent(
    state: FeatureState,
    onIntent: (FeatureIntent) -> Unit
) {
    Column {
        // Query input
        TextField(
            value = state.query,
            onValueChange = { onIntent(FeatureIntent.UpdateQuery(it)) }
        )
        
        // Results with sealed interface
        when (val results = state.results) {
            FeatureState.ResultState.Idle -> {
                Text("Start typing to search")
            }
            FeatureState.ResultState.Loading -> {
                LoadingIndicator()
            }
            FeatureState.ResultState.Empty -> {
                Text("No results found for '${state.query}'")
            }
            is FeatureState.ResultState.Success -> {
                LazyColumn {
                    items(results.data) { item ->
                        ItemRow(item)
                    }
                    if (results.isRefreshing) {
                        item { LoadingMoreIndicator() }
                    }
                }
            }
            is FeatureState.ResultState.Error -> {
                ErrorView(
                    message = results.message,
                    onRetry = { onIntent(FeatureIntent.LoadData) }
                )
            }
        }
        
        Button(onClick = { onIntent(FeatureIntent.LoadData) }) {
            Text("Load Data")
        }
    }
}
```

---

## Step 6: Build and Verify

### 6.1 Build the Project

```bash
./gradlew clean build
```

### 6.2 Verify Code Generation

Check that the executor was generated:

```
build/generated/ksp/debug/kotlin/com/yourapp/feature/generated/FeatureIntentProcessorExecutor.kt
```

**Generated code should look like:**
```kotlin
package com.yourapp.feature.generated

import javax.inject.Inject
import io.github.antonioimbesi.pulse.core.processor.ProcessorExecutor
import io.github.antonioimbesi.pulse.core.processor.ProcessorScope
import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect
import com.yourapp.feature.processor.LoadDataProcessor
import com.yourapp.feature.processor.UpdateQueryProcessor

internal class FeatureIntentProcessorExecutor @Inject constructor(
    private val loadDataProcessor: LoadDataProcessor,
    private val updateQueryProcessor: UpdateQueryProcessor
) : ProcessorExecutor<FeatureState, FeatureIntent, FeatureSideEffect> {
    override suspend fun execute(
        processorScope: ProcessorScope<FeatureState, FeatureSideEffect>,
        intent: FeatureIntent
    ) {
        when (intent) {
            is FeatureIntent.LoadData -> 
                with(loadDataProcessor) { processorScope.process(intent) }
            is FeatureIntent.UpdateQuery -> 
                with(updateQueryProcessor) { processorScope.process(intent) }
        }
    }
}
```

---

## Step 7: Write Tests

**File:** `src/test/.../FeatureProcessorsTest.kt`

```kotlin
package com.yourapp.feature

import com.yourapp.feature.contract.FeatureState
import com.yourapp.feature.contract.FeatureIntent
import com.yourapp.feature.contract.FeatureSideEffect
import com.yourapp.feature.processor.LoadDataProcessor
import io.github.antonioimbesi.pulse.test.testProcessor
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoadDataProcessorTest {
    
    @Test
    fun `load data success`() = runTest {
        val mockRepository = MockDataRepository(
            result = Result.Success(listOf(Item("1"), Item("2")))
        )
        
        testProcessor(
            initialState = FeatureState(),
            processor = LoadDataProcessor(mockRepository),
            intent = FeatureIntent.LoadData
        ) {
            expectEvents {
                state { it.results is FeatureState.ResultState.Loading }
                state { it.results is FeatureState.ResultState.Success }
                sideEffect(FeatureSideEffect.ShowSuccess)
            }
        }
    }
    
    @Test
    fun `load data error`() = runTest {
        val mockRepository = MockDataRepository(
            result = Result.Error("Network error")
        )
        
        testProcessor(
            initialState = FeatureState(),
            processor = LoadDataProcessor(mockRepository),
            intent = FeatureIntent.LoadData
        ) {
            expectEvents {
                state { it.results is FeatureState.ResultState.Loading }
                state { it.results is FeatureState.ResultState.Error }
            }
            expectSideEffects(FeatureSideEffect.ShowError("Network error"))
        }
    }
}
```

See `references/testing-guide.md` for comprehensive testing patterns.

---

## Creation Checklist

- [ ] Created contract files: State, Intent, SideEffect in `contract/` package
- [ ] Created one processor per intent in `processor/` package
- [ ] Each processor annotated with `@Processor`
- [ ] Created ViewModel with proper DI annotation
- [ ] Setup DI module (if using Hilt/Koin)
- [ ] Created UI with `collectState()` and `collectSideEffect { }`
- [ ] Built project successfully
- [ ] Verified executor generation in `build/generated/ksp/`
- [ ] Wrote tests for processors
- [ ] Tested all user flows in app

---

## Next Steps

- **Add more features:** Repeat this process for other screens
- **Update existing features:** See `references/updating-features.md`
- **Avoid mistakes:** Read `references/anti-patterns.md`
- **Having issues?** Check `references/troubleshooting.md`