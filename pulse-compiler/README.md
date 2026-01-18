# Pulse Compiler

## Overview

The KSP (Kotlin Symbol Processing) plugin that powers Pulse's compile-time safety. It eliminates boilerplate by generating the routing logic between your Intents and Processors.

---

## Key Features

✅ **Zero Reflection**: All routing is generated as static Kotlin code  
✅ **Compile-Time Safety**: Fails build if intent handlers are missing or ambiguous  
✅ **Incremental Processing**: Fast builds that only regenerate what changed  
✅ **Zero Boilerplate**: No need to write manual `when` statements for instruction routing  
✅ **Debuggable**: Generated code is human-readable

---

## How It Works

### 1. Discovery
The compiler scans your source set for classes annotated with `@Processor`.

```kotlin
@Processor // <--- The compiler finds this call
class MyProcessor : IntentProcessor<...>
```

### 2. Validation
It verifies that your class:
*   Implements `IntentProcessor` correctly.
*   Handles a concrete `Intent` class (not a generic or abstract type).
*   Correctly defines State and SideEffect types.

### 3. Generation
It generates a `ProcessorExecutor` class in the `generated` package of your feature.

**Input:**
```kotlin
package com.app.login
@Processor class LoginClickedProcessor : IntentProcessor<LoginState, LoginIntent.LoginClicked, LoginEffect>
@Processor class EmailChangedProcessor : IntentProcessor<LoginState, LoginIntent.EmailChanged, LoginEffect>
```

**Output (Generated):**
```kotlin
package com.app.login.generated

class LoginIntentProcessorExecutor(
    private val p0: LoginClickedProcessor,
    private val p1: EmailChangedProcessor
) : ProcessorExecutor<LoginState, LoginIntent, LoginEffect> {
    override suspend fun execute(scope: ProcessorScope, intent: LoginIntent) {
        when(intent) {
             is LoginIntent.LoginClicked -> p0.process(scope, intent)
             is LoginIntent.EmailChanged -> p1.process(scope, intent)
        }
    }
}
```

---

## Troubleshooting

### "ProcessorExecutor not found"
*   **Cause**: You forgot to add the `ksp` dependency or didn't annotate any processors.
*   **Fix**: Ensure `ksp("io.github.antonioimbesi.pulse:pulse-compiler:$version")` is in your generated `build.gradle.kts` and at least one class has `@Processor`.

### "Compilation failed with KSP error"
*   **Cause**: You might have two processors handling the exact same Intent type.
*   **Fix**: Pulse requires a 1:1 mapping between Intent types and Processors. Check for duplicates.

---

## Checklist for Success

✅ **Annotate Processors**: Don't forget `@Processor` or the code won't generate.  
✅ **Implement Interfaces**: Processors must strictly implement `IntentProcessor`.  
✅ **Add KSP Dependency**: Make sure `pulse-compiler` is added as `ksp(...)` not `implementation(...)`.
