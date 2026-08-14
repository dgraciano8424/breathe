# Coding Conventions

**Analysis Date:** 2026-06-15
**Revised:** 2026-08-14 — re-derived from the tree at `96eb5df`. Two claims in the
2026-06-15 version had become the opposite of the truth: it stated that no logging
framework was used and that nothing carried KDoc. Both are now standard practice here.
Examples built on the deleted quote/network classes have been replaced with real ones.

## Naming Patterns

**Files:**
- Kotlin files use PascalCase matching the primary class/interface/object they contain: `AppRepository.kt`, `PauseViewModel.kt`, `BreatheDatabase.kt`
- Screen composable files are named `<Feature>Screen.kt` and live alongside their ViewModel: `HomeScreen.kt` / `HomeViewModel.kt`
- DAO interfaces are named `<Entity>Dao.kt`: `BlockedAppDao.kt`, `InterventionEventDao.kt`
- DI modules are named `<Area>Module.kt`: `DatabaseModule.kt`, `SystemServiceModule.kt`, `CoroutinesModule.kt`
- Shared Compose helpers live in `ui/components/` and are named for what they draw: `WaveBackground.kt`, `NimbusBuddy.kt`, `ConfettiOverlay.kt`

**Classes and Interfaces:**
- PascalCase throughout: `BreatheAccessibilityService`, `SessionApprovalStore`, `PauseOverlayHost`
- ViewModels always suffixed with `ViewModel`: `HomeViewModel`, `PauseViewModel`, `StatsViewModel`
- Repositories always suffixed with `Repository`: `AppRepository`, `StatsRepository`, `AchievementRepository`, `MentalHealthTipsRepository`
- Room DAO interfaces always suffixed with `Dao`: `BlockedAppDao`, `InterventionEventDao`
- Hilt DI modules are `object` types suffixed with `Module`

**Functions:**
- camelCase for all functions: `refreshStats()`, `getPauseSeconds()`, `resolveAppName()`
- Boolean-returning functions use `is`/`has`/`can` prefix: `isBlocked()`, `isApproved()`, `isEnabled()`, `canShow()`
- Repository functions use domain-meaningful names rather than mirroring the DAO: `blockApp()` / `unblockApp()` where the DAO says `insert` / `delete`
- Private helper functions use descriptive camelCase: `startOfToday()`, `startOfWeek()`, `showInternal()`
- Factory methods in `companion object` use the `newIntent(...)` pattern for Activities: `PauseActivity.newIntent()` (`ui/pause/PauseActivity.kt:87`)
- Static permission/state checks live in the companion object of the class they concern: `BreatheAccessibilityService.isEnabled(context)`

**Variables and Properties:**
- camelCase: `currentPackage`, `lastForeground`, `blockedPackages`
- Private `MutableStateFlow` backing properties use underscore prefix: `_attemptCount`, `_selectedReason`, `_isMonitoringActive`
- Public `StateFlow` exposed without underscore and typed explicitly: `val attemptCount: StateFlow<Int> = _attemptCount`
- Compose `by` delegate pattern for state collection: `val apps by viewModel.blockedApps.collectAsState()`
- State shared across threads is explicitly marked: `@Volatile var isShowing` in `PauseOverlayHost`, `ConcurrentHashMap.newKeySet()` in `SessionApprovalStore`

**Constants:**
- SCREAMING_SNAKE_CASE inside `companion object`: `OUTCOME_DECLINED`, `OUTCOME_OPENED`, `DEFAULT_PAUSE_SECONDS`
- A `private const val TAG` in the companion object of any class that logs
- File-level private color constants in Compose screens use PascalCase: `val BreathePrimary = Color(...)`

**Data Classes:**
- Named in PascalCase: `BlockedApp`, `InterventionEvent`, `AppStat`, `MentalHealthTip`, `UserProgress`
- UI state and view-specific wrappers co-located with their ViewModel: `StatsUiState` in `StatsViewModel.kt`, `BlockedAppWithStats` in `HomeViewModel.kt`

## Code Style

**Formatting:**
- No lint/format config beyond `kotlin.code.style=official` in `gradle.properties`
- Trailing commas used in multi-line argument lists
- Star imports (`import androidx.compose.runtime.*`) used freely in Compose-heavy screen files; specific imports elsewhere

**Line width:**
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
5. Kotlin stdlib and `javax.inject.*` / `kotlinx.*`

**No path aliases** — full package paths used throughout.

## Dependency Injection Pattern

**Hilt annotations used:**
- `@HiltAndroidApp` on the `Application` class (`BreatheApp`)
- `@AndroidEntryPoint` on Activities and the accessibility service (`MainActivity`, `PauseActivity`, `BreatheAccessibilityService`)
- `@HiltViewModel` + `@Inject constructor(...)` on all ViewModels
- `@Singleton` + `@Inject constructor(...)` on repositories, `SessionApprovalStore`, `SessionTimeHelper`, `PauseOverlayHost`
- `@Module` + `@InstallIn(SingletonComponent::class)` on all DI object modules
- Custom qualifier `@ApplicationScope` for the process-lifetime `CoroutineScope` (`di/CoroutinesModule.kt`)

**Field injection** used only where constructor injection is unavailable — that is, in
system-instantiated components:
```kotlin
@Inject lateinit var appRepository: AppRepository
@Inject lateinit var sessionApprovalStore: SessionApprovalStore
@Inject lateinit var pauseOverlayHost: PauseOverlayHost
```

**Constructor injection** preferred everywhere else:
```kotlin
@Singleton
class AppRepository @Inject constructor(private val dao: BlockedAppDao) { ... }
```

**Scope choice is deliberate and worth preserving:** work that must outlive the component
that started it takes the injected `@ApplicationScope`, not `viewModelScope`. The pause
screen is torn down the instant the user chooses, so its outcome write would be cancelled
otherwise. See `PauseViewModel.recordDeclined()` / `recordOpened()`.

## ViewModel Pattern

1. Private `MutableStateFlow` backing properties for mutable state
2. Public `StateFlow` (or `stateIn`) exposed for the UI
3. Functions triggering side effects via `viewModelScope.launch { ... }` — or `appScope` when the write must survive teardown
4. Single-expression functions where possible

```kotlin
private val _attemptCount = MutableStateFlow(0)
val attemptCount: StateFlow<Int> = _attemptCount

fun init(packageName: String, appName: String) {
    currentPackage = packageName
    // Reset so a retargeted pause never inherits the previous app's duration.
    _pauseSeconds.value = BlockedApp.DEFAULT_PAUSE_SECONDS
    viewModelScope.launch {
        _pauseSeconds.value = appRepo.getPauseSeconds(packageName)
        _attemptCount.value = statsRepo.getTodayAttemptCount(packageName) + 1
    }
}
```

For reactive DB streams, `stateIn` with `WhileSubscribed(5000)`:
```kotlin
val apps = repo.getBlockedApps()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

## Compose Screen Pattern

**Screen composable signature:**
- Top-level screen composable is public, named `<Feature>Screen`
- Parameters are primitive/model values plus lambda callbacks; screens do not take a ViewModel except as the default `viewModel: XViewModel = hiltViewModel()` parameter
- Helper composables in the same file are `private`

```kotlin
@Composable
fun PauseScreen(
    appName: String,
    attemptCount: Int,
    tip: MentalHealthTip,
    alternativeActivity: String,
    selectedReason: String?,
    pauseSeconds: Int,
    onReasonSelected: (String) -> Unit,
    ...
) { ... }

@Composable
private fun TodaySummaryCard(...) { ... }
```

That parameter-only signature is what allows `PauseScreen` to be rendered by both
`PauseOverlayHost` (a `ComposeView` in a `WindowManager` overlay) and `PauseActivity`
(the fallback) with no branching inside the screen itself. Keep it free of Activity
assumptions.

**State hoisting:** state is collected at the host (overlay or Activity) or top-level
Screen composable and passed down; sub-composables do not collect.

**Accessibility/motion:** a shared `rememberReducedMotion()` (`ui/components/ReducedMotion.kt`)
gates animation when the system animation scale is zero. New animated components are
expected to respect it.

**Section comments** in Compose screens use `// ── Section name ─────` style.

## Error Handling

**Patterns:**
- `runCatching { ... }.getOrDefault(...)` for fallible Android API calls with a sensible fallback:
  ```kotlin
  private fun resolveAppName(packageName: String): String = runCatching {
      val info = packageManager.getApplicationInfo(packageName, 0)
      packageManager.getApplicationLabel(info).toString()
  }.getOrDefault(packageName)
  ```
- `runCatching { ... }.onFailure { Log.w(TAG, "...", it) }` where the failure is worth a breadcrumb but not a crash — the overlay detach path, the widget refresh
- `try`/`catch` where a real fallback path exists, as in `PauseOverlayHost` falling back to `PauseActivity` when the window is rejected
- No custom exception types
- Error state is surfaced to the UI only where the user can act on it. The app picker has real `isLoading` / `errorMessage` state with retry; the pause path logs instead, because by the time a write fails the screen is gone

**Nullable returns** used to signal absence rather than throwing.

## Logging

**Android `Log`, used sparingly and deliberately.** Each logging class declares a
`private const val TAG` in its companion object and logs at `w` or `e` only:

```kotlin
Log.e(TAG, "Failed to record ${event.outcome} for ${event.packageName}", it)
Log.w(TAG, "Overlay rejected; falling back to the pause activity", e)
```

Current call sites: `PauseViewModel`, `PauseOverlayHost`, `AppSelectViewModel`,
`PauseCountWidget`. No logging framework, no Timber, no structured logging.

`Log.d` and `Log.v` are stripped from release builds by an `-assumenosideeffects` rule in
`proguard-rules.pro`; warnings and errors are kept, because they are what makes a field
failure diagnosable. **Write anything that matters in production at `w` or `e`** — a `d`
call is a debug-only aid by construction.

## Comments

The codebase is comment-dense by deliberate choice, and the density carries meaning.

**KDoc** is used on classes and functions whose *reason for existing* is not obvious from
the signature — currently around 40 blocks across 18 files. The pattern is to explain the
constraint, not the mechanics:

```kotlin
/**
 * A [CoroutineScope] that lives as long as the process, for work that must finish
 * even if the component that started it goes away.
 */
```

**Inline comments** explain platform behaviour, policy constraints, and decisions that
look wrong without context. These are the highest-value comments here and should not be
"cleaned up":

```kotlin
// Approval is per-visit: leaving the app ends it. Only the app being left is
// revoked, and lastForeground is never reset to null, so an approval cannot
// outlive the visit it was granted for.
```

```kotlin
// Deliberately NOT android:isAccessibilityTool="true". Google Play policy excludes
// monitoring apps and launchers from that flag, and claiming it falsely risks app
// suspension and developer account termination.
```

**Section dividers** inside long Compose composables: `// ── Top: attempt counter ────`

**A caution:** several comments in the tree still describe the pre-`90f7e30`
architecture — see the "Dead code and stale comments" section of `CONCERNS.md`. When a
comment and the code disagree here, the code has usually moved.

## Module Design

**Packages follow feature + layer separation:**
- `data/model` — pure data classes (Room entities and plain data)
- `data/db` — Room database and DAO interfaces
- `data/repository` — repository classes over the DAOs
- `di` — Hilt DI module objects
- `service` — the accessibility service and its collaborators
- `widget` — the home-screen app widget
- `ui/<feature>` — one package per screen containing Screen + ViewModel
- `ui/components` — shared composables
- `ui/nav` — navigation graph only
- `ui/theme` — colour and theme definitions

There is no `data/remote` package; the app has no network layer.

**No barrel files** — each class imported by its full path.

**Single-responsibility files:** every file contains exactly one primary
class/interface/object.

---

*Convention analysis: 2026-06-15. Revised 2026-08-14 against `96eb5df`.*
