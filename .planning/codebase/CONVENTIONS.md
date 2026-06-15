# Coding Conventions

**Analysis Date:** 2026-06-15

## Naming Patterns

**Files:**
- Kotlin files use PascalCase matching the primary class/interface/object they contain: `AppRepository.kt`, `PauseViewModel.kt`, `BreatheDatabase.kt`
- Screen composable files are named `<Feature>Screen.kt` and live alongside their ViewModel: `HomeScreen.kt` / `HomeViewModel.kt`
- DAO interfaces are named `<Entity>Dao.kt`: `BlockedAppDao.kt`, `InterventionEventDao.kt`
- DI modules are named `<Area>Module.kt`: `DatabaseModule.kt`, `NetworkModule.kt`, `SystemServiceModule.kt`
- DTOs (remote data transfer objects) are named `<Entity>Dto.kt`: `QuoteDto.kt`

**Classes and Interfaces:**
- PascalCase throughout: `AppMonitorService`, `ForegroundAppDetector`, `ZenQuotesApi`
- ViewModels always suffixed with `ViewModel`: `HomeViewModel`, `PauseViewModel`, `StatsViewModel`
- Repositories always suffixed with `Repository`: `AppRepository`, `QuoteRepository`, `StatsRepository`
- Room DAO interfaces always suffixed with `Dao`: `BlockedAppDao`, `QuoteDao`
- Hilt DI modules are `object` types suffixed with `Module`

**Functions:**
- camelCase for all functions: `getRandomQuote()`, `refreshStats()`, `startMonitoring()`
- Boolean-returning functions use `is`/`has` prefix: `isBlocked()`, `checkUsagePermission()`
- Repository functions mirror DAO operations but use domain-meaningful names: `blockApp()` / `unblockApp()` (DAO uses `insert` / `delete`)
- Private helper functions use descriptive camelCase: `startOfToday()`, `startOfWeek()`, `buildNotification()`
- Factory methods in `companion object` use `newIntent(...)` pattern for Activities: `PauseActivity.newIntent()`
- Service start/stop methods in companion object: `AppMonitorService.start(context)`, `AppMonitorService.stop(context)`

**Variables and Properties:**
- camelCase: `currentPackage`, `approvedSessions`, `lastForeground`
- Private `MutableStateFlow` backing properties use underscore prefix: `_quote`, `_attemptCount`, `_selectedReason`
- Public `StateFlow` exposed without underscore and typed explicitly: `val quote: StateFlow<Quote?> = _quote`
- Compose `by` delegate pattern for state collection: `val apps by viewModel.blockedApps.collectAsState()`

**Constants:**
- SCREAMING_SNAKE_CASE inside `companion object`: `OUTCOME_DECLINED`, `OUTCOME_OPENED`, `NOTIF_ID`, `CHANNEL_ID`, `EXTRA_PACKAGE`
- File-level private color constants in Compose screens use PascalCase: `val BgTop = Color(0xFF0D0D1A)`, `val GlowPrimary = Color(0xFF6C63FF)`

**Data Classes:**
- Named in PascalCase: `BlockedApp`, `InterventionEvent`, `AppStat`, `InstalledApp`, `StatsUiState`
- UI state data classes co-located with their ViewModel: `StatsUiState` is in `StatsViewModel.kt`

## Code Style

**Formatting:**
- No linting config files found (`.eslintrc`, `biome.json`, etc. do not apply — this is Kotlin/Android). Standard Kotlin formatting conventions are followed.
- Trailing commas used in multi-line argument lists
- Star imports (`import androidx.compose.runtime.*`) are used freely in Compose-heavy screen files; more specific imports used elsewhere

**Line width:**
- Imports use wildcard (`*`) for packages with many frequently used members (Compose, Material3, coroutines)
- Single-expression functions used extensively in DAOs and repositories: `fun getBlockedApps(): Flow<List<BlockedApp>> = dao.getAll()`

**Braces and layout:**
- No braces omitted — all `if`/`else` blocks use braces or single-expression form
- Companion objects always at the bottom of the class

## Import Organization

**Order (observed pattern):**
1. Android / AndroidX imports
2. Compose imports
3. Hilt DI imports
4. Project-internal imports (`com.dgraciano.breathe.*`)
5. Kotlin stdlib imports (`javax.inject.*`, `kotlinx.*`)

**Wildcard usage:**
- Compose screen files use star imports for Compose, Material3, and runtime packages
- Non-UI files use specific imports

**No path aliases** — full package paths used throughout.

## Dependency Injection Pattern

**Hilt annotations used:**
- `@HiltAndroidApp` on `Application` class (`BreatheApp`)
- `@AndroidEntryPoint` on Activities and Services (`MainActivity`, `PauseActivity`, `AppMonitorService`, `BootReceiver`)
- `@HiltViewModel` + `@Inject constructor(...)` on all ViewModels
- `@Singleton` + `@Inject constructor(...)` on all Repositories and `ForegroundAppDetector`
- `@Module` + `@InstallIn(SingletonComponent::class)` on all DI object modules

**Field injection** used only in Services where constructor injection is not available:
```kotlin
@Inject lateinit var detector: ForegroundAppDetector
@Inject lateinit var appRepository: AppRepository
```

**Constructor injection** preferred for all other components:
```kotlin
@Singleton
class AppRepository @Inject constructor(private val dao: BlockedAppDao) { ... }
```

## ViewModel Pattern

All ViewModels follow this structure:
1. Private `MutableStateFlow` backing properties for mutable state
2. Public `StateFlow` or `stateIn` properties exposed for the UI
3. Functions triggering side effects via `viewModelScope.launch { ... }`
4. Single-expression functions where possible

```kotlin
private val _quote = MutableStateFlow<Quote?>(null)
val quote: StateFlow<Quote?> = _quote

fun init(packageName: String, appName: String) {
    viewModelScope.launch {
        _quote.value = quoteRepo.getRandomQuote()
    }
}
```

For reactive DB streams, `stateIn` with `WhileSubscribed(5000)` is used:
```kotlin
val blockedApps = repo.getBlockedApps()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

## Compose Screen Pattern

**Screen composable signature:**
- Top-level screen composable is public, named `<Feature>Screen`
- Parameters are primitive/model values + lambda callbacks — screens do NOT take ViewModel directly (except the default `viewModel: XViewModel = hiltViewModel()` parameter for injection)
- Helper composables within the same file are `private`

```kotlin
@Composable
fun PauseScreen(
    appName: String,
    attemptCount: Int,
    quote: Quote?,
    selectedReason: String?,
    onReasonSelected: (String) -> Unit,
    onYes: () -> Unit,
    onNo: () -> Unit
) { ... }

@Composable
private fun TodaySummaryCard(...) { ... }
```

**State hoisting:** State is collected at the Activity or top-level Screen composable, then passed down as parameters — not collected in sub-composables.

**`LaunchedEffect(Unit)` pattern** used to trigger one-shot ViewModel calls on composition:
```kotlin
LaunchedEffect(Unit) {
    viewModel.startService()
    viewModel.refreshStats()
}
```

**Section comments** in Compose screens use `// ── Section name ─────` style.

## Error Handling

**Patterns:**
- `runCatching { ... }.getOrNull()` used for fallible network calls, suppressing exceptions silently:
  ```kotlin
  runCatching { api.getQuotes() }.getOrNull()?.let { dtos -> ... }
  ```
- `runCatching { ... }.getOrDefault(packageName)` used for fallible Android API calls:
  ```kotlin
  runCatching {
      val info = context.packageManager.getApplicationInfo(packageName, 0)
      context.packageManager.getApplicationLabel(info).toString()
  }.getOrDefault(packageName)
  ```
- No `try/catch` blocks — `runCatching` is the consistent mechanism
- No custom exception types
- No explicit error state in most ViewModels — failures are silently swallowed

**Nullable returns** used to signal absence rather than throwing: `suspend fun getRandomQuote(): Quote?`

## Logging

**No logging framework is used.** There are no `Log.d`, `Timber`, or structured logging calls anywhere in the codebase. The OkHttp logging interceptor (`HttpLoggingInterceptor.Level.BASIC`) in `NetworkModule.kt` provides network-level debug output only.

## Comments

**When to comment:**
- Inline section dividers inside long Compose composables: `// ── Top: attempt counter ──────────────────────────────────────`
- Explanatory notes for non-obvious platform behavior: `// Show over the lock screen — setShowWhenLocked replaces the deprecated manifest flag`
- Arithmetic clarification: `// +1 to count the current attempt (recorded on Yes/No tap)`

**No JSDoc/KDoc** on any functions or classes. Public API surface is undocumented.

## Module Design

**Packages follow feature + layer separation:**
- `data/model` — pure data classes (Room entities, DTOs, plain data)
- `data/db` — Room DAO interfaces
- `data/remote` — Retrofit API interfaces and DTOs
- `data/repository` — repository classes bridging DAO/API
- `di` — Hilt DI module objects
- `service` — Android background services and receivers
- `ui/<feature>` — one package per screen containing Screen + ViewModel
- `ui/nav` — navigation graph only
- `ui/theme` — color and theme definitions

**No barrel files** — each class imported by its full path.

**Single-responsibility files:** Every file contains exactly one primary class/interface/object.

---

*Convention analysis: 2026-06-15*
