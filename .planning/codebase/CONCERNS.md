# Codebase Concerns

**Analysis Date:** 2026-06-15

## Tech Debt

**Onboarding shown on every cold launch:**
- Issue: `NavGraph.kt` hardcodes `startDestination = Routes.ONBOARDING` with no persistence of "has been onboarded" state. The `OnboardingViewModel` auto-advances when `hasUsagePermission` is true, so returning users with the permission granted are immediately redirected. However, if the permission is ever revoked, or on a fresh install when the auto-advance fires before the composable settles, there is no stored flag to skip onboarding. There is no `SharedPreferences` or `DataStore` usage anywhere in the codebase.
- Files: `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`, `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingViewModel.kt`
- Impact: Fragile UX — relies entirely on permission state as a proxy for "completed onboarding." Any timing or permission change causes onboarding re-display.
- Fix approach: Persist an `onboardingComplete` flag with DataStore Preferences; read it in `NavGraph` to choose start destination before composing the graph.

**WorkManager dependency declared but never used:**
- Issue: `libs.versions.toml` declares `work-runtime = "2.9.0"` and `app/build.gradle.kts` includes `implementation(libs.work.runtime)`. No `Worker`, `WorkRequest`, or `WorkManager` call exists anywhere in source.
- Files: `gradle/libs.versions.toml`, `app/build.gradle.kts`
- Impact: Unnecessary APK bloat; unused dependency surface. Likely left from an earlier plan to schedule periodic quote refreshes.
- Fix approach: Remove `work-runtime` from both `libs.versions.toml` and `build.gradle.kts` unless a background refresh worker is added.

**Quote refresh has no scheduling — quotes go stale:**
- Issue: `QuoteRepository.refreshQuotes()` is only called when the local quote table is empty (`if (dao.count() == 0)`). After the first fetch, quotes are never refreshed. There is no TTL, timestamp, or periodic job.
- Files: `app/src/main/java/com/dgraciano/breathe/data/repository/QuoteRepository.kt`
- Impact: Users see the same batch of quotes indefinitely. The unused WorkManager dependency was presumably intended to solve this.
- Fix approach: Add a `fetchedAt` timestamp to the quotes table and refresh when stale (e.g., older than 24 hours), or use WorkManager's `PeriodicWorkRequest`.

**Hardcoded magic numbers for "time saved" stat:**
- Issue: The "Saved" metric displayed in `HomeScreen` and `StatsScreen` is computed as `declined * 20` — a fixed 20-minute assumption per resistance. This magic number is duplicated in two places.
- Files: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeScreen.kt:148`, `app/src/main/java/com/dgraciano/breathe/ui/stats/StatsScreen.kt:84,205`
- Impact: Changing the assumption requires editing three call sites; inconsistency risk if one is missed.
- Fix approach: Extract to a named constant (e.g., `MINUTES_SAVED_PER_RESISTANCE = 20`) in a shared location, or expose a computed property from `StatsRepository`.

**`AppStat` is a plain data class, not a Room entity — raw query result mapping:**
- Issue: `AppStat` (`data/model/AppStat.kt`) is returned by a `@Query` in `InterventionEventDao` that projects `packageName`, `appName`, and `COUNT(*) as count`. Room maps this via column-name matching. There is no `@DatabaseView` or explicit mapping annotation, which means schema column renames will silently break the query at runtime.
- Files: `app/src/main/java/com/dgraciano/breathe/data/model/AppStat.kt`, `app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt:23-31`
- Impact: Fragile — a column rename in `intervention_events` without updating `AppStat` fields causes a silent null/crash at runtime, not a compile error.
- Fix approach: Annotate `AppStat` fields with `@ColumnInfo` matching the exact query output column names to make the contract explicit.

**Room schema export disabled:**
- Issue: `BreatheDatabase` sets `exportSchema = false`, meaning migration correctness cannot be verified against a checked-in schema baseline.
- Files: `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt:14`
- Impact: Future migrations cannot be validated by Room's migration test utilities. Schema history is lost.
- Fix approach: Set `exportSchema = true`, add a `room.schemaLocation` to `build.gradle.kts`, and commit schema JSON files to version control.

**`outcome` field stored as raw String, not enum:**
- Issue: `InterventionEvent.outcome` is a plain `String` column. The DAO query for declined count hard-codes the string `'DECLINED'` directly in SQL. The constants `OUTCOME_DECLINED` and `OUTCOME_OPENED` exist in the companion object but are not enforced at the type level.
- Files: `app/src/main/java/com/dgraciano/breathe/data/model/InterventionEvent.kt`, `app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt:19`
- Impact: Typos in the SQL literal or in call sites produce silent data loss (events never counted as declined). No compile-time enforcement.
- Fix approach: Use a Kotlin `enum class Outcome` with a Room `@TypeConverter`, replacing the raw string column and SQL literal.

---

## Known Bugs

**`approvedSessions` logic allows re-triggering on the same session:**
- Symptoms: If `ForegroundAppDetector` briefly returns a different package (e.g., launcher flicker between app switches) and then returns to the original blocked package, `approvedSessions.remove(lastForeground)` clears the approval and the pause screen fires again within the same intentional open session.
- Files: `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt:38-44`
- Trigger: Rapidly switching between two apps; or any app that briefly yields focus to the launcher during loading.
- Workaround: None. Users see a second pause screen unexpectedly.

**`ForegroundAppDetector` queries only 10-second window — unreliable on slow devices:**
- Symptoms: `queryUsageStats` uses `now - 10_000L` as the window. On devices where `UsageStatsManager` updates lazily (some OEM variants update every 30–60 seconds), the returned stats may be stale, causing the detector to return `null` or the wrong package.
- Files: `app/src/main/java/com/dgraciano/breathe/service/ForegroundAppDetector.kt:12`
- Trigger: Low-end or OEM-modified devices. Also occurs immediately after reboot before the usage stats daemon warms up.
- Workaround: None currently.

**`PauseViewModel.init()` can be called multiple times (no guard):**
- Symptoms: `PauseActivity` calls `viewModel.init(blockedPackage, appName)` in `onCreate`. On activity recreation (e.g., config change), `init()` fires again, incrementing the in-memory `_attemptCount` by re-querying `getTodayAttemptCount` which now reflects the previously recorded event from the same session.
- Files: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseViewModel.kt:33-40`, `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt:39`
- Trigger: Rotate device or system-initiated activity recreation while pause screen is showing.
- Workaround: None; attempt count display jumps.

**`StatsScreen` calls `loadStats()` twice on composition:**
- Symptoms: `StatsViewModel.init` calls `loadStats()`, then `StatsScreen` has a `LaunchedEffect(Unit)` that also calls `viewModel.loadStats()`. This results in two concurrent database reads on every screen open, causing a brief flicker as `isLoading` resets to `true` then `false` in rapid succession (the second `loadStats()` call reinitializes state with `isLoading = true`).
- Files: `app/src/main/java/com/dgraciano/breathe/ui/stats/StatsViewModel.kt:30-31`, `app/src/main/java/com/dgraciano/breathe/ui/stats/StatsScreen.kt:31`
- Trigger: Every navigation to the Stats screen.
- Workaround: None; minor visual artifact.

**`AppSelectScreen` dismisses immediately on first tap, blocking multi-select:**
- Symptoms: `AppSelectScreen` calls `onDone()` (pops back stack) immediately after `viewModel.blockApp(app)`, allowing only one app to be selected per visit.
- Files: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectScreen.kt:35-36`
- Trigger: User wants to block multiple apps in one session.
- Workaround: User must navigate back repeatedly and select one app at a time.

---

## Security Considerations

**HTTP logging interceptor active in release builds:**
- Risk: `NetworkModule` attaches `HttpLoggingInterceptor` with `Level.BASIC` unconditionally. In release APKs this logs HTTP request/response lines to Logcat, potentially exposing quote API URLs and response data to apps with `READ_LOGS` permission.
- Files: `app/src/main/java/com/dgraciano/breathe/di/NetworkModule.kt:21-23`
- Current mitigation: None.
- Recommendations: Guard with `if (BuildConfig.DEBUG)` or set `Level.NONE` in release builds.

**`allowBackup="true"` — database backed up to Google cloud:**
- Risk: `AndroidManifest.xml` sets `android:allowBackup="true"`. The Room database `breathe.db` — containing the full list of apps the user is trying to resist, all intervention events, and behavioral patterns — is included in Android Auto Backup and device-to-device transfer by default.
- Files: `app/src/main/AndroidManifest.xml:18`
- Current mitigation: None.
- Recommendations: Add a `backup_rules.xml` (API 31+) or `full_backup_content.xml` to explicitly exclude `breathe.db` from backups, or set `allowBackup="false"` if backup is not a feature requirement.

**`BootReceiver` exported with no permission restriction:**
- Risk: `BootReceiver` is declared `android:exported="true"` with only an implicit intent filter. While `BOOT_COMPLETED` is a protected broadcast that only the system can send, the `exported=true` declaration is broader than needed and increases the attack surface if the intent filter is ever changed.
- Files: `app/src/main/AndroidManifest.xml:48-53`
- Current mitigation: Protected broadcast effectively limits real-world risk.
- Recommendations: Add `android:permission="android.permission.RECEIVE_BOOT_COMPLETED"` attribute, or set `exported="false"` if targeting API 33+ where system broadcasts can still be received by non-exported receivers.

---

## Performance Bottlenecks

**Polling loop at 500ms with database call each cycle:**
- Problem: `AppMonitorService.startMonitoring()` calls `appRepository.isBlocked(current)` inside a `while(isActive)` loop that polls every 500ms. Each poll executes a `SELECT EXISTS` SQL query.
- Files: `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt:34-50`
- Cause: No in-memory cache of the blocked apps set; every poll hits Room on the IO dispatcher.
- Improvement path: Load `getAllBlockedPackageNames()` into an in-memory `Set<String>` on service start and refresh via a `Flow` collector on `AppRepository.getBlockedApps()`. The polling loop then only reads memory.

**`AppSelectViewModel` calls `getAllPackageNames()` once and does not react to changes:**
- Problem: When `AppSelectScreen` is opened, all installed apps are loaded minus already-blocked ones. If another blocked app is added concurrently (unlikely but possible), the list is stale until the screen is recreated.
- Files: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectViewModel.kt:34`
- Cause: One-shot `suspend` call instead of collecting from `BlockedAppDao.getAll()` flow.
- Improvement path: Combine the installed apps list with the `Flow<List<BlockedApp>>` using `combine` to reactively filter.

**`loadInstalledApps()` runs on `Dispatchers.IO` but calls `getApplicationLabel()` per app:**
- Problem: `pm.getApplicationLabel(it)` is called in a `.map` inside a coroutine on `Dispatchers.IO`. For devices with 200+ user apps, this blocks the IO thread pool for potentially 200–500ms on first open.
- Files: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectViewModel.kt:35-39`
- Cause: No loading state shown in `AppSelectScreen` while the list is populating; screen appears empty momentarily.
- Improvement path: Add a loading indicator to `AppSelectScreen`, or use `getInstalledApplications(0)` without `GET_META_DATA` which is faster.

---

## Fragile Areas

**`ForegroundAppDetector` — platform API reliability:**
- Files: `app/src/main/java/com/dgraciano/breathe/service/ForegroundAppDetector.kt`
- Why fragile: `UsageStatsManager.queryUsageStats` behavior varies significantly by Android OEM. On some devices (MIUI, ColorOS, HyperOS), usage stats require additional manufacturer-specific permissions or are throttled. The 10-second query window is too narrow for lazy-update implementations.
- Safe modification: Expand the window to 30–60 seconds and take the most recent entry. Wrap in a try/catch for `SecurityException`. Provide a permissions guidance screen for known problematic OEMs.
- Test coverage: None. Core feature has zero tests.

**`AppMonitorService` — OS may kill it:**
- Files: `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt`
- Why fragile: Even as a foreground service, aggressive battery optimization on MIUI, Huawei EMUI, and similar OEM Android variants kills foreground services. `BootReceiver` restarts it on reboot but not after mid-session kills.
- Safe modification: Implement `onStartCommand` returning `START_STICKY` to request restart after kill. Consider adding an `AlarmManager` or `JobScheduler` watchdog.
- Test coverage: None.

**`PauseViewModel.init()` — not idempotent:**
- Files: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseViewModel.kt`
- Why fragile: Called directly from `PauseActivity.onCreate`, so any activity lifecycle event (rotation, multi-window resize) re-runs initialization logic. The ViewModel survives recreation but `init()` is not guarded.
- Safe modification: Gate with a `private var initialized = false` flag, or move initialization into the ViewModel's own `init {}` block using saved state handle for the package name.
- Test coverage: None.

**Navigation start destination is always ONBOARDING:**
- Files: `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`
- Why fragile: Relies on `OnboardingViewModel.refreshPermissionState()` being called and the `LaunchedEffect(hasUsage)` auto-navigating fast enough to be invisible to the user. On slow devices the onboarding screen flashes on every launch for returning users who have the permission.
- Safe modification: Determine start destination before composing the NavHost (e.g., read a DataStore flag synchronously in `MainActivity` or using `runBlocking` at startup), then pass it as the initial route.
- Test coverage: None.

---

## Scaling Limits

**`InterventionEventDao.getRecent()` hardcoded at 100 rows:**
- Current capacity: Returns `LIMIT 100` events.
- Limit: If a user interacts with 100+ blocked apps in a session, older events are invisible to any future feature that uses `getRecent()`.
- Scaling path: Paginate with `PagingSource` when a history/log UI feature is added.

**`getTopApps` limited to 5 entries:**
- Current capacity: `LIMIT 5` in SQL.
- Limit: If a user blocks more than 5 apps, the stats screen only shows the top 5 even if the user has meaningful data for the rest.
- Scaling path: Make the limit a parameter or increase to 10; add a scrollable list if count grows.

**No data retention policy — `intervention_events` grows indefinitely:**
- Current capacity: Unbounded. Every intervention is inserted and never deleted.
- Limit: On devices with limited storage, after months of use the table could accumulate tens of thousands of rows.
- Scaling path: Add a periodic cleanup job (WorkManager) that deletes events older than 90 days, or implement the already-declared WorkManager dependency.

---

## Dependencies at Risk

**`kotlin = "1.9.24"` and `ksp = "1.9.24-1.0.20"` — outdated:**
- Risk: Kotlin 1.9.x is a maintenance release; Kotlin 2.x is current. The `ksp` version is tightly coupled to the Kotlin version and must be updated together. The Compose BOM `2024.06.00` is over a year old relative to the analysis date.
- Impact: Missing compiler improvements, newer Compose features (strong skipping mode on by default in Kotlin 2.x), and potential incompatibility if any dependency requires a newer Kotlin version.
- Migration plan: Update to Kotlin 2.x, matching KSP version, and a current Compose BOM (2025.x). Test Hilt annotation processing with the new KSP version.

**`compileSdk = 34` / `targetSdk = 34` — below current requirement:**
- Risk: Google Play requires `targetSdk >= 35` for new app submissions as of August 2024, and `>= 36` for updates in 2025. The app currently targets SDK 34.
- Impact: Play Store submission rejection for new or updated releases.
- Migration plan: Update `compileSdk` and `targetSdk` to 36 in `app/build.gradle.kts`, test for behavioral changes (predictive back gesture, photo picker, notification permission changes).

**Gson converter for Retrofit — consider Moshi or kotlinx.serialization:**
- Risk: Gson lacks null-safety awareness for Kotlin and does not enforce non-null types at deserialization. A malformed ZenQuotes API response with missing `q` or `a` fields will create a `QuoteDto` with null fields that are declared non-null in Kotlin, causing a `NullPointerException` at use.
- Impact: App crash if ZenQuotes API returns unexpected JSON shape.
- Migration plan: Replace `converter-gson` with `kotlinx-serialization-json` or `converter-moshi` with Kotlin codegen for null-safe deserialization.

---

## Missing Critical Features

**No error state for quote loading failure:**
- Problem: `QuoteRepository.refreshQuotes()` silently swallows API errors via `runCatching { }.getOrNull()`. If the network call fails and the local table is empty, `getRandomQuote()` returns `null`. `PauseScreen` handles `null` quote gracefully in the UI, but the user sees no quote with no explanation.
- Blocks: Reliable quote display on first offline use.

**No graceful degradation when Usage Stats permission is revoked post-onboarding:**
- Problem: After the user grants permission and moves to `HomeScreen`, if they revoke the Usage Stats permission from Settings, `AppMonitorService` continues running but `queryUsageStats` returns an empty list silently. The service appears functional (notification persists) but no apps are detected.
- Blocks: User awareness that monitoring has stopped.

**No "service is running" status indicator on HomeScreen:**
- Problem: `HomeScreen` calls `viewModel.startService()` in `LaunchedEffect(Unit)` every time it enters composition. There is no feedback to the user about whether the service is active, whether the Usage Stats permission is still granted, or whether monitoring is working.
- Blocks: User trust and troubleshooting.

---

## Test Coverage Gaps

**Zero tests exist:**
- What's not tested: Every class in the codebase — ViewModels, repositories, DAOs, `ForegroundAppDetector`, `AppMonitorService`, all UI screens.
- Files: `app/src/test/` directory exists but contains no test files. `app/src/androidTest/` directory exists but contains no test files. Test dependencies (`junit`, `coroutines-test`, `mockk`) are declared in `build.gradle.kts` but unused.
- Risk: Any refactor or bug fix has no regression safety net. The core detection logic (`ForegroundAppDetector`, `AppMonitorService`) and intervention recording (`PauseViewModel`, `StatsRepository`) are completely unvalidated.
- Priority: High — particularly `ForegroundAppDetector`, `AppMonitorService` polling logic, `PauseViewModel` (init idempotency), and `StatsRepository` date boundary calculations (`startOfToday`, `startOfWeek`).

---

*Concerns audit: 2026-06-15*
