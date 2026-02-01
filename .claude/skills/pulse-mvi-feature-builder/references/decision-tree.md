# Pulse MVI Decision Tree

## First: Platform and DI

**What platform is this project targeting?**
- Android → Read `references/android.md` after SKILL.md
- Pure Kotlin → Core SKILL.md is sufficient

**What DI framework is the project using?**
- Hilt → `references/di-hilt.md`
- Koin → `references/di-koin.md`
- Manual wiring → `references/di-manual.md`
- None → `references/di-none.md`

---

## What do you want to do?

### I need to create a new feature
→ `references/creating-features.md`

### I need to add a user action to an existing feature
→ `references/updating-features.md` — "Adding New Intents and Processors"

Steps: Add Intent subtype → Create Processor → Update DI if needed → Update UI → Write test

### I need to change how an existing action works
→ `references/updating-features.md` — "Modifying Existing Processors"

### I need to add new data to state
→ `references/updating-features.md` — "Adding Fields"

Add with default value. That's it.

### I need to remove data from state
→ Just remove it. Update processors and UI that referenced it. No deprecation.

### I need to add navigation or a toast
→ `references/updating-features.md` — "Adding or Modifying Side Effects"

Add to SideEffect sealed interface → `send()` in processor → handle in UI

### I need to organize a complex processor
→ Use private functions. Do not create fake intents.
→ `references/updating-features.md` — "Organizing Complex Processor"

### I need to migrate existing code
- From ViewModel/LiveData → `references/migration-traditional-viewmodel.md`
- From Orbit MVI → `references/migration-orbit-mvi.md`
- From MVIKotlin → `references/migration-mvikotlin.md`
- From Redux → `references/migration-redux.md`

### I need to write tests
→ `references/testing-guide.md`

- Simple state change → `testProcessor` + `finalState()`
- Event order matters → `testProcessor` + `expectEvents { }`
- Async / delays / flows → `testProcessorAsync` + `advanceUntilIdle()`
- Always use Given-When-Then naming

### I have a build error
- "ProcessorExecutor not found" → Check `ksp(pulse-compiler)` dependency, check `@Processor` annotation, rebuild
- "Ambiguous intent" → Two processors handle same intent, remove one
- Type mismatch → Verify State/Intent/SideEffect types match in processor, ViewModel, and DI
  → `references/troubleshooting.md`

### Something doesn't work at runtime
- State not updating → `references/troubleshooting.md` — "State Not Updating"
- Side effects not firing → `references/troubleshooting.md` — "Side Effects Not Triggering"
- Crashes when backgrounded → Using `LaunchedEffect` instead of `collectSideEffect`. See `references/android.md`
- Injection failures → See appropriate `references/di-*.md`
  → `references/troubleshooting.md`

---

## Common Decisions

### Should this be an Intent?

```
Is this triggered by the user (button, swipe, input)?
├── YES → Is it Init? → Use Init intent
│         Otherwise → Name it by the user's goal → It's an Intent ✅
└── NO → It's not an Intent ❌
         Handle it internally (private function, repository call, etc.)
```

Examples of things that are NOT intents: `DataLoaded`, `OnLocationUpdated`, `ValidateForm`, `UpdateInternalCounter`, `OnTimerExpired`

### Should this go in State or SideEffect?

```
Does the UI need to display this persistently?
├── YES → State
└── NO → Does it need to happen exactly once?
    ├── YES → SideEffect (navigation, toast, dialog)
    └── NO → Neither — handle internally in the processor
```

### Should these screens share a ViewModel?

```
Are they part of the same feature (same user flow)?
├── YES → Share ViewModel ✅
└── NO → Separate ViewModels
         Communicate via navigation args or shared repository
```

### Should I use a sealed interface or boolean flags in State?

```
Are the states mutually exclusive?
├── YES → Sealed interface (Loading OR Error OR Success)
└── NO → Boolean flags are OK (isDarkMode AND isNotificationsEnabled)
```

### How should I organize a large processor?

```
Is all this logic part of ONE user intent?
├── YES → Keep one processor, use private functions
└── NO → These are actually separate user intents
         Split into separate processors
```
