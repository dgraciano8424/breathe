<!-- refreshed: 2026-08-14 -->
# Architecture

**Analysis Date:** 2026-06-15
**Revised:** 2026-08-14 — brought in line with the tree at `96eb5df`. The 2026-06-15
version described the original design: a polling foreground service, a boot receiver, a
`PauseActivity` launched by intent, and a Retrofit quote layer. All four have been
replaced or deleted.

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────┐
│                          UI Layer (Jetpack Compose)                  │
├──────────────┬───────────────┬──────────────┬───────────────────────┤
│  MainActivity│ PauseScreen   │  HomeScreen  │ Stats / AppSelect /   │
│  (nav host)  │ (two hosts)   │  (main hub)  │ Onboarding / Achieve. │
└──────┬───────┴───────┬───────┴──────┬───────┴──────────┬────────────┘
       │               │              │                  │
       ▼               ▼              ▼                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ViewModel Layer (Hilt + StateFlow)                │
│  HomeViewModel  PauseViewModel  StatsViewModel                       │
│  AppSelectViewModel  OnboardingViewModel  AchievementsViewModel      │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Repository Layer                                │
│  AppRepository           (blocked apps + per-app pause length)       │
│  StatsRepository         (intervention event queries)                │
│  AchievementRepository   (progress levels)                           │
│  MentalHealthTipsRepository (local tip/activity text — no network)   │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 ▼
┌───────────────────────────────────────────┐
│  Local Database (Room, schema v5)         │   ← the only data source
│  BreatheDatabase                          │
│  BlockedAppDao · InterventionEventDao     │
└───────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Detection Layer (system-driven)                   │
│  BreatheAccessibilityService  ← bound by the OS, not started by us   │
│    typeWindowStateChanged events, canRetrieveWindowContent=false     │
│  SessionApprovalStore   (per-visit "continue" approvals)             │
│  PauseOverlayHost       (TYPE_APPLICATION_OVERLAY window)            │
└─────────────────────────────────────────────────────────────────────┘

There is no network layer, no foreground service, and no boot receiver.
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| BreatheApp | Hilt application entry point | `BreatheApp.kt` |
| MainActivity | Hosts the Compose nav graph; single activity for main UI | `MainActivity.kt` |
| BreatheAccessibilityService | Receives window-state events, mirrors the blocked set, decides whether to intervene | `service/BreatheAccessibilityService.kt` |
| SessionApprovalStore | Holds per-visit approvals so "continue" is not re-prompted until the user leaves | `service/SessionApprovalStore.kt` |
| SessionTimeHelper | Estimates average session length per app from usage events; 6-hour TTL cache | `service/SessionTimeHelper.kt` |
| PauseOverlayHost | Draws `PauseScreen` in a `TYPE_APPLICATION_OVERLAY` window; supplies the ViewTree owners | `ui/pause/PauseOverlayHost.kt` |
| PauseActivity | Fallback pause host when overlay permission is missing | `ui/pause/PauseActivity.kt` |
| PauseViewModel | Pause state; records the outcome on an application scope | `ui/pause/PauseViewModel.kt` |
| BreatheNavGraph | Compose nav host; routes `onboarding`, `home`, `app_select`, `stats`, `achievements` | `ui/nav/NavGraph.kt` |
| AppRepository | CRUD for blocked apps and per-app pause length; exposes Flow | `data/repository/AppRepository.kt` |
| StatsRepository | Records intervention events; today/week aggregates | `data/repository/StatsRepository.kt` |
| AchievementRepository | Derives progress level from recorded events | `data/repository/AchievementRepository.kt` |
| MentalHealthTipsRepository | Supplies the grounding tip and alternative activity; local data | `data/repository/MentalHealthTipsRepository.kt` |
| PauseCountWidget | Home-screen widget: today's pause count and time won back | `widget/PauseCountWidget.kt` |
| WidgetRefresher | Pushes a widget update when an intervention is recorded | `widget/WidgetRefresher.kt` |
| BreatheDatabase | Room database; version 5; two entities; migrations 1→5 | `data/db/BreatheDatabase.kt` |
| DatabaseModule | Provides Room DB and DAOs as singletons | `di/DatabaseModule.kt` |
| SystemServiceModule | Provides `UsageStatsManager` | `di/SystemServiceModule.kt` |
| CoroutinesModule | Provides the `@ApplicationScope` that outlives the pause screen | `di/CoroutinesModule.kt` |

## Pattern Overview

**Overall:** MVVM with Repository pattern and Hilt DI.

**Key Characteristics:**
- Single-activity architecture for the main UI. The pause is **not** a nav destination and normally **not** an Activity — it is a Compose tree in an overlay window, so it can appear over a third-party app.
- Detection is **event-driven and system-owned**. The OS binds the accessibility service, delivers window events to it, and rebinds it after reboot. The app does not start, stop, or schedule it.
- ViewModels expose state via `StateFlow`; Compose screens collect with `collectAsState()`.
- Repositories mediate between ViewModels and Room; ViewModels never touch DAOs.
- Work that must survive the pause screen disappearing runs on an injected `@ApplicationScope`, not `viewModelScope`.

## Layers

**UI Layer:**
- Purpose: Render state, emit user events, navigate
- Location: `ui/`
- Depends on: ViewModel layer only

**ViewModel Layer:**
- Purpose: Hold and transform UI state, coordinate operations
- Location: `ui/<feature>/`, co-located with screens
- Depends on: Repository layer (plus `@ApplicationContext` in three cases — see Anti-Patterns)

**Repository Layer:**
- Purpose: Single source of truth per data domain
- Location: `data/repository/`
- Depends on: DAO interfaces
- Used by: ViewModels, and `BreatheAccessibilityService` (for the blocked list)

**Data Layer:**
- Purpose: Persistence
- Location: `data/db/`, `data/model/`
- Depends on: Room. Nothing else — there is no remote source

**Detection Layer:**
- Purpose: Notice a blocked app opening and put the pause on screen
- Location: `service/`, plus `ui/pause/PauseOverlayHost.kt`
- Depends on: `AppRepository`, `SessionApprovalStore`, `PauseOverlayHost`
- Used by: The Android OS, via the manifest. Nothing in the app calls into it

**DI Layer:**
- Purpose: Wire dependencies via Hilt modules in `SingletonComponent`
- Location: `di/`

## Data Flow

### Detection and interception

1. User opens an app. The system raises `TYPE_WINDOW_STATE_CHANGED` and delivers it to `BreatheAccessibilityService.onAccessibilityEvent()` (`service/BreatheAccessibilityService.kt:60`)
2. Non-`typeWindowStateChanged` events, and events for Breathe's own package, are ignored
3. If the overlay is already showing, the event is ignored — the blocked app stays in the foreground behind the overlay and would otherwise re-trigger continuously
4. If the package differs from `lastForeground`, the previous app's session approval is revoked and `lastForeground` advances (`:70-76`). **See blocker 2 in `APP_AUDIT.md`: this treats any window, including system ones, as an app switch**
5. If the package is not in the in-memory blocked set, or is already approved, or overlay permission is missing, nothing happens
6. Otherwise `PauseOverlayHost.show(packageName, appName)` (`:82`)

The blocked set is mirrored into memory by a Flow collected in `onServiceConnected()`, so
the event path never touches Room.

### Rendering the pause

1. `PauseOverlayHost.show()` sets `isShowing` and posts to the main looper
2. `showInternal()` builds `OverlayOwners` — a lifecycle, ViewModelStore and SavedStateRegistry owner — because a `ComposeView` outside an Activity has none of them
3. A `PauseViewModel` is constructed through a local `ViewModelProvider` against those owners, then `init(packageName, appName)` loads the tip, the alternative activity, today's attempt count, and the per-app pause length
4. The `ComposeView` is added to the `WindowManager` as `TYPE_APPLICATION_OVERLAY`
5. `PauseActivity` renders the same `PauseScreen` when overlay permission is missing

### Recording an outcome

1. "I'll do something else" → `recordDeclined()`; "Continue to X" (enabled once the countdown ends) → `recordOpened()`, which also calls `sessionApprovalStore.approve()` (`ui/pause/PauseViewModel.kt:96`)
2. The `InterventionEvent` write runs on `@ApplicationScope`, not `viewModelScope`, so it survives the overlay being torn down immediately afterwards
3. Failures are caught and logged (`:128`). They cannot be retried or surfaced — the pause is gone by then
4. `WidgetRefresher` pushes an update so the home-screen widget reflects the new count

### Configuring blocked apps

1. `HomeScreen` → `AppSelectScreen`
2. `AppSelectViewModel` queries `PackageManager` through a `<queries>` launcher-intent filter, surfacing "most used this week" first
3. Selection → `AppRepository.blockApp()` → `BlockedAppDao.insert()`
4. The blocked-apps Flow re-emits, updating both the home list and the service's in-memory mirror

**State Management:**
- UI state is `StateFlow` in ViewModels; screens use `collectAsState()`
- Database-backed state uses Room `Flow` queries surfaced through repositories
- Cross-component mutable state is deliberate and singleton-scoped: `SessionApprovalStore` (a `ConcurrentHashMap`-backed set) and `PauseOverlayHost.isShowing` (`@Volatile`), both read from the accessibility event thread and written from elsewhere

## Key Abstractions

**BlockedApp (Room Entity):** a user-designated app that triggers a pause, with its own
`pauseSeconds`. `packageName` is the primary key. `data/model/BlockedApp.kt`

**InterventionEvent (Room Entity):** immutable record of each pause — outcome, optional
reason, and `minutesSaved`. Auto-generated PK; outcome constants in the companion object.
`data/model/InterventionEvent.kt`

**MentalHealthTip:** the grounding tip rendered during the pause
(`PauseScreen.kt:221`). Local data from `MentalHealthTipsRepository` — this is what the
deleted ZenQuotes feature was mistakenly believed to be providing.

**SessionApprovalStore:** the "you already said yes" memory. Approval is per-visit:
granted on continue, revoked when the user leaves. The definition of "leaves" is the open
question in blocker 2.

**Repository (data access boundary):** `@Singleton` with `@Inject constructor`; concrete
classes, no interfaces.

## Entry Points

**Application Start:** `BreatheApp.kt` — Hilt graph init via `@HiltAndroidApp`

**Main UI:** `MainActivity.kt` — launcher intent; sets `BreatheTheme` around `BreatheNavGraph`

**Detection:** `service/BreatheAccessibilityService.kt` — bound by the OS from the manifest
when the user enables it in Settings, and rebound after reboot. **The app never starts
it**, which is why `HomeViewModel` reports its state rather than controlling it

**Pause (fallback):** `ui/pause/PauseActivity.kt` — used only when overlay permission is missing

**Widget:** `widget/PauseCountWidget.kt` — instantiated by the launcher from its manifest name

## Architectural Constraints

- **Threading:** Compose on main; Room and usage-stats work on `Dispatchers.IO`. `onAccessibilityEvent` runs on the service's main thread and must stay cheap — hence the in-memory blocked-set mirror and the `mainHandler.post` inside `PauseOverlayHost`
- **Reflectively instantiated classes:** the accessibility service and the widget are resolved by name from the manifest, so both need ProGuard keep rules. A missing rule breaks them in release builds only
- **No process-lifetime component of our own:** the service is the OS's to bind. There is nothing to restart, and nothing that can be relied upon to run when the service is disabled
- **Overlay, not Activity:** the pause must be a window, not a nav destination, so it can draw over a third-party app. Background Activity starts are unreliable on Android 10+, which is what motivated the change
- **Detection is push, not poll:** there is no interval, no cursor, and no scan window. The trade is that the app now sees *every* window change, including ones that are not app switches

## Anti-Patterns

### Direct context injection into ViewModel

**What happens:** `HomeViewModel`, `AppSelectViewModel` and `OnboardingViewModel` each
take `@ApplicationContext val context: Context`.
**Why it's wrong here:** it couples the ViewModel to framework classes and bypasses the
injection seam, which is usually why a class here has no tests.
**Worked example, now fixed:** `HomeViewModel` used to call
`context.getSystemService(USAGE_STATS_SERVICE)` directly despite `SystemServiceModule`
existing to provide `UsageStatsManager`. Injecting it instead is what made
`HomeViewModelTest` possible — the class went from untestable to five tests without any
other structural change. That is the argument for the pattern in miniature.
**Still applies to:** the remaining `Context` uses — `PackageManager` in
`AppSelectViewModel`, `AppOpsManager` and `Settings` in `OnboardingViewModel`, and the
permission checks in `HomeViewModel.refreshMonitoringState()`. Extract them behind
injectable wrappers the way `SessionTimeHelper` wraps usage stats.

### No repository interface abstractions

**What happens:** repositories are concrete `@Singleton` classes with no interface.
**Why it's wrong here:** ViewModels depend on concrete types, so tests need MockK.
**Do this instead:** interface + impl, if test doubles without a mocking framework become
worth the indirection. Noted rather than recommended — the suite works as it stands.

## Error Handling

**Strategy:** degrade quietly on non-critical paths; there is no global error state.

**Patterns:**
- `resolveAppName()` falls back to the package name via `runCatching { }.getOrDefault()` (`BreatheAccessibilityService.kt:85`)
- `PauseOverlayHost` falls back to `PauseActivity` when the window is rejected, and tolerates a double-detach
- Intervention-event write failures are caught and logged; by then the pause is gone, so there is no user to notify and nothing to retry into
- The app picker has real `isLoading` and `errorMessage` state with a retry action — the one place errors are surfaced
- **Missing permissions are the main "error" path**, and they are handled as state rather than exceptions: `HomeViewModel.isMonitoringActive` reflects accessibility-enabled plus `canDrawOverlays`, so a revoked permission shows as inactive rather than as silent zeros

## Cross-Cutting Concerns

**Logging:** Android `Log` only; `Log.d`/`Log.v` stripped from release builds by ProGuard.
No structured logging, no crash reporting, no analytics — and adding any would be the
app's first network egress.
**Validation:** package-name uniqueness enforced by Room `@PrimaryKey` +
`OnConflictStrategy.REPLACE`.
**Authentication:** none. No accounts, no external services.

## Known stale comments in source

None outstanding. The set found while revising these documents — the `isShowing` KDoc
describing a polling thread, the `buildConfig` flag justified by deleted network logging,
the orphaned notification icon and strings — was cleared on 2026-08-14. See the "Dead code
and stale comments" section of `CONCERNS.md` for the list and what verified each removal.

---

*Architecture analysis: 2026-06-15. Revised 2026-08-14 against `96eb5df`.*
