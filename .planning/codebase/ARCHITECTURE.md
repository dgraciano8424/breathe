<!-- refreshed: 2026-06-15 -->
# Architecture

**Analysis Date:** 2026-06-15

## System Overview

```text
┌─────────────────────────────────────────────────────────────────────┐
│                          UI Layer (Jetpack Compose)                  │
├──────────────┬──────────────┬──────────────┬────────────────────────┤
│  MainActivity│ PauseActivity│  HomeScreen  │  Stats / AppSelect /   │
│  (nav host)  │ (interrupt)  │  (main hub)  │  Onboarding Screens    │
│`MainActivity`│`PauseActivity`│`home/`      │  `stats/` `appselect/` │
└──────┬───────┴──────┬───────┴──────┬───────┴──────────┬─────────────┘
       │              │              │                   │
       ▼              ▼              ▼                   ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    ViewModel Layer (Hilt + StateFlow)                │
│  `ui/home/HomeViewModel`  `ui/pause/PauseViewModel`                 │
│  `ui/stats/StatsViewModel` `ui/appselect/AppSelectViewModel`        │
│  `ui/onboarding/OnboardingViewModel`                                │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      Repository Layer                                │
│  `data/repository/AppRepository`  (blocked app list)                │
│  `data/repository/QuoteRepository` (quote fetch + local cache)      │
│  `data/repository/StatsRepository` (intervention event queries)     │
└────────────────┬───────────────────────────────┬────────────────────┘
                 │                               │
                 ▼                               ▼
┌───────────────────────────┐    ┌───────────────────────────────────┐
│  Local Database (Room)    │    │  Remote API (Retrofit)             │
│  `data/db/BreatheDatabase`│    │  `data/remote/ZenQuotesApi`        │
│  BlockedAppDao            │    │  https://zenquotes.io/api/quotes   │
│  QuoteDao                 │    └───────────────────────────────────┘
│  InterventionEventDao     │
└───────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                    Background Service Layer                          │
│  `service/AppMonitorService`  (foreground service, polls every 500ms)│
│  `service/ForegroundAppDetector` (UsageStatsManager wrapper)        │
│  `service/BootReceiver`       (auto-starts service on boot)         │
└─────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| BreatheApp | Hilt application entry point | `app/src/main/java/com/dgraciano/breathe/BreatheApp.kt` |
| MainActivity | Hosts the Compose nav graph; single activity for main UI | `app/src/main/java/com/dgraciano/breathe/MainActivity.kt` |
| PauseActivity | Separate activity launched by service to interrupt the user; can appear over lock screen | `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt` |
| BreatheNavGraph | Compose nav host; defines all main-flow routes | `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt` |
| AppMonitorService | Foreground service; polls foreground app every 500ms and fires PauseActivity on blocked app | `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt` |
| ForegroundAppDetector | Wraps UsageStatsManager to detect current foreground package | `app/src/main/java/com/dgraciano/breathe/service/ForegroundAppDetector.kt` |
| BootReceiver | BroadcastReceiver; restarts AppMonitorService after device reboot | `app/src/main/java/com/dgraciano/breathe/service/BootReceiver.kt` |
| AppRepository | CRUD for the blocked apps list; exposes Flow | `app/src/main/java/com/dgraciano/breathe/data/repository/AppRepository.kt` |
| QuoteRepository | Fetches quotes from ZenQuotes API, caches in Room, returns random entry | `app/src/main/java/com/dgraciano/breathe/data/repository/QuoteRepository.kt` |
| StatsRepository | Records intervention events; provides today/week aggregate queries | `app/src/main/java/com/dgraciano/breathe/data/repository/StatsRepository.kt` |
| BreatheDatabase | Room database; version 2; three entities | `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt` |
| DatabaseModule | Hilt module; provides Room DB and DAOs as singletons | `app/src/main/java/com/dgraciano/breathe/di/DatabaseModule.kt` |
| NetworkModule | Hilt module; provides OkHttpClient, Retrofit, ZenQuotesApi singletons | `app/src/main/java/com/dgraciano/breathe/di/NetworkModule.kt` |
| SystemServiceModule | Hilt module; provides UsageStatsManager singleton | `app/src/main/java/com/dgraciano/breathe/di/SystemServiceModule.kt` |

## Pattern Overview

**Overall:** MVVM (Model-View-ViewModel) with Repository pattern and Dependency Injection via Hilt.

**Key Characteristics:**
- Single-activity architecture for main UI (`MainActivity`), with a second isolated activity (`PauseActivity`) used exclusively as an interrupt overlay launched from a background service.
- ViewModels expose state exclusively via `StateFlow` / `MutableStateFlow`; Compose screens collect with `collectAsState()`.
- Repository layer mediates between ViewModels and data sources; ViewModels never directly reference DAOs or Retrofit APIs.
- All dependency wiring is handled by Hilt; no manual service locator or factory classes.
- Background monitoring runs in a coroutine loop inside a foreground service rather than WorkManager or AlarmManager.

## Layers

**UI Layer:**
- Purpose: Render state, emit user events, navigate between screens
- Location: `app/src/main/java/com/dgraciano/breathe/ui/`
- Contains: `@Composable` screen functions, private composable helpers, theme files
- Depends on: ViewModel layer only
- Used by: Nothing (top layer)

**ViewModel Layer:**
- Purpose: Hold and transform UI state, coordinate business operations, survive configuration changes
- Location: `app/src/main/java/com/dgraciano/breathe/ui/<feature>/` (co-located with screens)
- Contains: `@HiltViewModel` classes annotated with `@Inject constructor`
- Depends on: Repository layer
- Used by: UI layer via `hiltViewModel()`

**Repository Layer:**
- Purpose: Single source of truth for each data domain; abstract storage and network details
- Location: `app/src/main/java/com/dgraciano/breathe/data/repository/`
- Contains: Three repository classes (`AppRepository`, `QuoteRepository`, `StatsRepository`)
- Depends on: DAO interfaces, Retrofit API interface
- Used by: ViewModels, `AppMonitorService`

**Data Layer (DB + Remote):**
- Purpose: Persistence and network access
- Location: `app/src/main/java/com/dgraciano/breathe/data/db/` and `data/remote/`
- Contains: Room database, DAOs, Retrofit interface, DTOs, Room entity models
- Depends on: Android Room, Retrofit, OkHttp
- Used by: Repositories only

**Service Layer:**
- Purpose: Background app monitoring independent of the UI lifecycle
- Location: `app/src/main/java/com/dgraciano/breathe/service/`
- Contains: `AppMonitorService`, `ForegroundAppDetector`, `BootReceiver`
- Depends on: `AppRepository` (to check blocked list), `PauseActivity` (to launch interrupt)
- Used by: Android OS (via manifest), `HomeViewModel` (to start/stop service)

**DI Layer:**
- Purpose: Wire dependencies via Hilt modules
- Location: `app/src/main/java/com/dgraciano/breathe/di/`
- Contains: Three Hilt `@Module` objects installed in `SingletonComponent`
- Depends on: All other layers (provides their dependencies)
- Used by: Hilt code generation at compile time

## Data Flow

### Main Monitoring Loop (Background)

1. Device boots → `BootReceiver.onReceive()` (`service/BootReceiver.kt:8`) fires `AppMonitorService.start()`
2. OR user opens app → `HomeScreen` `LaunchedEffect` calls `viewModel.startService()` (`ui/home/HomeViewModel.kt:37`)
3. `AppMonitorService.startMonitoring()` (`service/AppMonitorService.kt:34`) enters coroutine loop on `Dispatchers.IO`
4. Every 500ms: `ForegroundAppDetector.getCurrentApp()` (`service/ForegroundAppDetector.kt:9`) queries `UsageStatsManager`
5. If app changed and is in blocked list: `appRepository.isBlocked(current)` (`service/AppMonitorService.kt:43`)
6. Match found → `launchPause(packageName)` → `PauseActivity.newIntent()` fired with `FLAG_ACTIVITY_NEW_TASK`

### Pause Interrupt Flow

1. `PauseActivity.onCreate()` (`ui/pause/PauseActivity.kt:21`) extracts package name from intent extras
2. `viewModel.init(packageName, appName)` triggers parallel coroutines:
   - Fetches random quote from `QuoteRepository.getRandomQuote()` (DB-first, API fallback)
   - Loads today's attempt count + 1 from `StatsRepository.getTodayAttemptCount()`
3. User interacts with `PauseScreen` — selects optional reason chip
4. On "No, go back": `viewModel.recordDeclined()` writes `InterventionEvent(outcome=DECLINED)` then sends user to launcher
5. On "Yes, open": `viewModel.recordOpened()` writes `InterventionEvent(outcome=OPENED)` then launches blocked app

### Quote Refresh Flow

1. `QuoteRepository.getRandomQuote()` checks `QuoteDao.count()` (`data/repository/QuoteRepository.kt:15`)
2. If count is 0: calls `refreshQuotes()` → `ZenQuotesApi.getQuotes()` via Retrofit
3. API response mapped from `QuoteDto` → `Quote` entities → `QuoteDao.insertAll()`
4. Returns `QuoteDao.getRandom()` (SQL `ORDER BY RANDOM() LIMIT 1`)

### User Configures Blocked Apps

1. `HomeScreen` FAB → nav to `AppSelectScreen`
2. `AppSelectViewModel.loadInstalledApps()` queries `PackageManager` for non-system apps, excludes already-blocked apps
3. User taps app → `AppSelectViewModel.blockApp()` → `AppRepository.blockApp()` → `BlockedAppDao.insert()`
4. `HomeViewModel.blockedApps` Flow (from `dao.getAll()`) emits update automatically

**State Management:**
- All UI state is `StateFlow` held in ViewModels; screens observe via `collectAsState()`
- Database-backed state uses Room `Flow` queries, surfaced through `AppRepository` and converted with `.stateIn()`
- No global mutable state outside ViewModels; `AppMonitorService` holds a local `approvedSessions: MutableSet<String>` for per-session de-duplication

## Key Abstractions

**BlockedApp (Room Entity):**
- Purpose: Represents a user-designated app that should trigger a pause intervention
- Examples: `data/model/BlockedApp.kt`
- Pattern: Room `@Entity` data class; `packageName` is the primary key

**InterventionEvent (Room Entity):**
- Purpose: Immutable audit record of each pause encounter — outcome (DECLINED or OPENED) and optional reason
- Examples: `data/model/InterventionEvent.kt`
- Pattern: Room `@Entity` with auto-generated integer PK; outcome constants defined in companion object

**Quote (Room Entity + cache):**
- Purpose: Motivational quote displayed on the PauseScreen; fetched from remote API and cached locally
- Examples: `data/model/Quote.kt`, `data/remote/QuoteDto.kt`
- Pattern: Separate DTO (`QuoteDto`) for Retrofit response; domain model (`Quote`) for Room storage

**Repository (data access boundary):**
- Purpose: Single public interface between ViewModels and data sources; handles source selection logic
- Examples: `AppRepository`, `QuoteRepository`, `StatsRepository`
- Pattern: `@Singleton` with `@Inject constructor`; no interface abstraction (concrete classes only)

**UiState (sealed data holder):**
- Purpose: Bundle multiple UI fields into a single StateFlow to simplify screen observation
- Examples: `StatsUiState` in `ui/stats/StatsViewModel.kt:13`, implied by individual StateFlows in `PauseViewModel`
- Pattern: Flat `data class` with defaults; `isLoading` flag for async operations

## Entry Points

**Application Start:**
- Location: `app/src/main/java/com/dgraciano/breathe/BreatheApp.kt`
- Triggers: Android OS on process creation
- Responsibilities: Initializes Hilt DI graph via `@HiltAndroidApp`

**Main UI Entry:**
- Location: `app/src/main/java/com/dgraciano/breathe/MainActivity.kt`
- Triggers: Launcher intent; `android.intent.action.MAIN` / `LAUNCHER` category
- Responsibilities: Sets Compose content with `BreatheTheme` wrapping `BreatheNavGraph`; starts on onboarding by default

**Pause Interrupt Entry:**
- Location: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt`
- Triggers: `AppMonitorService` via explicit intent with `FLAG_ACTIVITY_NEW_TASK`
- Responsibilities: Renders full-screen breathing UI over the current app; records outcome; launches target app or home on dismiss

**Boot Entry:**
- Location: `app/src/main/java/com/dgraciano/breathe/service/BootReceiver.kt`
- Triggers: `android.intent.action.BOOT_COMPLETED` broadcast
- Responsibilities: Restarts `AppMonitorService` so monitoring persists across reboots

## Architectural Constraints

- **Threading:** Main thread for Compose UI; `Dispatchers.IO` for all database and network calls; `AppMonitorService` monitoring loop runs on `Dispatchers.IO` via `SupervisorJob`
- **Global state:** `AppMonitorService.approvedSessions` is a module-level `MutableSet` (service scope, not singleton); `AppMonitorService.lastForeground` is a private `var` in service scope
- **Circular imports:** None detected; service layer depends on repository, UI depends on ViewModels, repositories depend on DAOs — all one-directional
- **Polling interval:** Foreground app detection polls every 500ms; there is no event-driven alternative due to Android API constraints
- **Two-activity design:** `PauseActivity` must be a separate `Activity` (not a composable destination) so the OS allows it to appear over any foreground app via `FLAG_ACTIVITY_NEW_TASK`

## Anti-Patterns

### Direct context injection into ViewModel

**What happens:** `HomeViewModel`, `AppSelectViewModel`, and `OnboardingViewModel` each receive `@ApplicationContext val context: Context` via Hilt injection.
**Why it's wrong here:** Accessing `PackageManager`, `AppOpsManager`, or starting services directly from a ViewModel couples business logic to Android framework classes, making unit testing harder.
**Do this instead:** Extract platform operations into injectable wrapper classes (similar to how `ForegroundAppDetector` wraps `UsageStatsManager`) and inject those wrappers instead.

### No repository interface abstractions

**What happens:** `AppRepository`, `QuoteRepository`, and `StatsRepository` are concrete `@Singleton` classes with no interface.
**Why it's wrong here:** ViewModels depend directly on concrete types, so faking repositories in unit tests requires either Mockk or real Room in-memory databases.
**Do this instead:** Define `interface AppRepository` / `impl DefaultAppRepository` pattern to allow test doubles without mocking frameworks.

## Error Handling

**Strategy:** Silent fail with `runCatching` for non-critical paths; no global error state.

**Patterns:**
- Network calls in `QuoteRepository.refreshQuotes()` wrapped in `runCatching { }.getOrNull()` — failure silently skips quote refresh
- `PauseActivity.newIntent()` resolves app label with `runCatching { }.getOrDefault(packageName)` — falls back to package name
- No error state is surfaced to the UI; failed quote loads result in a `null` quote (hidden by `AnimatedVisibility`)
- No retry logic on network failures

## Cross-Cutting Concerns

**Logging:** OkHttp `HttpLoggingInterceptor` at `BASIC` level for network traffic (configured in `di/NetworkModule.kt`); no structured app-level logging framework
**Validation:** None — package name uniqueness is enforced by Room `@PrimaryKey` + `OnConflictStrategy.REPLACE`
**Authentication:** None required; ZenQuotes API is public; no user accounts

---

*Architecture analysis: 2026-06-15*
