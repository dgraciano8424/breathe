# Codebase Concerns

**Analysis Date:** 2026-06-15
**Revised:** 2026-08-14 — re-verified against the tree at `96eb5df`. Most of the
2026-06-15 list has been fixed or deleted along with the code it described. Each item
below was checked against source rather than carried forward; items that no longer apply
are listed at the bottom with what closed them, so the history stays readable.

`APP_AUDIT.md` is the authoritative record of blockers and their status. This document
covers the smaller things that never rose to blocker level.

---

## Open — correctness

**Onboarding keys its start destination on the one permission that is now optional:**
- Issue: `BreatheNavGraph` computes `startDest = if (hasUsage) Routes.HOME else Routes.ONBOARDING` (`ui/nav/NavGraph.kt:34`). Since `90f7e30`, usage access is optional — the app is fully functional without it. A user who granted accessibility and overlay but declined usage access starts every cold launch on the onboarding screen.
- Mitigating: `OnboardingScreen` has a `LaunchedEffect(hasAccessibility, hasOverlay)` that navigates to `HOME` once both real permissions are present, so the user is not trapped. But `startDestination` is only read at initial composition, so the result is an onboarding flash on every launch for anyone who skipped the optional permission.
- Files: `ui/nav/NavGraph.kt:34`, `ui/onboarding/OnboardingScreen.kt:53-59`
- Impact: Cosmetic but constant, and it signals "setup incomplete" to a user whose setup is complete.
- Fix approach: Key the start destination on `BreatheAccessibilityService.isEnabled(context) && Settings.canDrawOverlays(context)` — the same pair `HomeViewModel.isMonitoringActive` already uses. Better still, persist an `onboardingComplete` flag; there is still no DataStore or SharedPreferences anywhere in the codebase, so permission state remains a proxy for "has been onboarded".

**`outcome` stored as a raw String, not an enum:**
- Issue: `InterventionEvent.outcome` is a `String` (`data/model/InterventionEvent.kt:12`), with `OUTCOME_DECLINED` / `OUTCOME_OPENED` as companion constants that nothing enforces. The DAO hard-codes `'DECLINED'` in SQL.
- Files: `data/model/InterventionEvent.kt`, `data/db/InterventionEventDao.kt`
- Impact: A typo at a call site or in the SQL literal silently produces events that are never counted — wrong statistics rather than a crash, which is the harder failure to notice.
- Fix approach: `enum class Outcome` with a Room `@TypeConverter`.

**`AppStat` maps a projection by column-name matching with no explicit contract:**
- Issue: `AppStat` is a plain data class (`packageName`, `appName`, `count`) populated from a `@Query` projection. No `@ColumnInfo`, no `@DatabaseView`.
- Files: `data/model/AppStat.kt`, `data/db/InterventionEventDao.kt`
- Impact: Renaming a column in `intervention_events` without updating `AppStat` breaks the query at runtime, not compile time.
- Fix approach: Annotate the fields with `@ColumnInfo` matching the projection aliases.

**Room schema export disabled:**
- Issue: `exportSchema = false` (`data/db/BreatheDatabase.kt:13`).
- Impact: There is no checked-in schema baseline, so Room's migration test utilities cannot verify migrations. This matters more now than in June: the schema has moved 1→5, `MIGRATION_4_5` **drops a table**, and there are still no instrumented tests. The migration is verified by nothing at all.
- Fix approach: Set `exportSchema = true`, add `room.schemaLocation`, commit the schema JSON, and write the migration test. See `TESTING.md`.

**`StatsScreen` loads twice on every open:**
- Issue: `StatsViewModel.init` calls `loadStats()` (`ui/stats/StatsViewModel.kt:35`) and `StatsScreen` calls it again from `LaunchedEffect(Unit)` (`ui/stats/StatsScreen.kt:40-41`).
- Impact: Two concurrent reads per navigation and a brief `isLoading` flicker. Minor, and the double-read is deliberate insurance against stale data on re-entry — but the flicker is a side effect nobody chose.
- Fix approach: Drop the `init` call and keep the `LaunchedEffect`, or make `loadStats()` not reset `isLoading` when data is already present.

---

## Open — performance

**`HomeViewModel` recomputes a 7-day usage aggregate inside a Flow collector:**
- Problem: `loadAppsWithStats()` runs `queryAndAggregateUsageStats(now - 7 days, now)` **inside** `repo.getBlockedApps().collect { }` (`ui/home/HomeViewModel.kt:63-77`), so the entire aggregate is recomputed on every emission — including every add, removal, and pause-length change.
- Cause: The aggregate does not depend on which apps changed, but it is inside the collector anyway.
- Impact: On a device with many apps this is a repeated multi-hundred-millisecond query on the IO dispatcher for no new information. It is the same shape of problem the audit already fixed once in `SessionTimeHelper`.
- Improvement path: Compute the usage map once per refresh and `combine` it with the Flow, or reuse `SessionTimeHelper`'s TTL cache.

**`HomeViewModel` reaches for `UsageStatsManager` through `Context` directly:**
- Problem: `context.getSystemService(USAGE_STATS_SERVICE)` in the ViewModel, despite `SystemServiceModule` existing to provide exactly that dependency.
- Impact: Bypasses the injection seam, which is a large part of why `HomeViewModel` has no tests.
- Improvement path: Inject `UsageStatsManager`.

**Eager `getApplicationLabel()` / icon loading in the app picker:**
- Problem: The picker resolves a label, and a `Drawable` icon, per installed app.
- Impact: First open is slow on devices with many apps. This is mitigated now — the screen has real `isLoading` and `errorMessage` state with retry — but the underlying cost is unchanged.
- Improvement path: Load icons lazily per visible row.

---

## Open — scaling

**`InterventionEventDao` fixed limits:** `getRecent()` is `LIMIT 100` and the top-apps
query is `LIMIT 5`. Both are fine for the current screens and would need revisiting if a
history UI or a longer breakdown is added.

**No data retention policy:** `intervention_events` grows without bound — one row per
pause, forever. Not urgent at realistic usage rates, but there is now no WorkManager (it
was removed as unused), so a cleanup job would need a new mechanism. Worth deciding
deliberately rather than discovering at scale.

---

## Fragile areas

**`BreatheAccessibilityService` — the whole product, with no tests:**
- Files: `service/BreatheAccessibilityService.kt`
- Why fragile: it holds detection, the blocked-set mirror and the approval lifecycle, and nothing exercises any of it. It also inherits an OEM problem from its predecessor rather than escaping one: accessibility services are disabled by aggressive battery managers on some OEM builds, and the user can switch them off from a Settings screen the app does not control.
- Partial mitigation: `HomeViewModel.isMonitoringActive` reports enabled-state on the home screen, so the failure is visible **if the user opens the app**.
- Safe modification: add unit tests around the event-handling branches first — most of it does not need a device. See `TESTING.md`.
- Test coverage: none.

**Loss of the ambient "still running" signal:**
- Why fragile: the foreground-service notification is gone, which is a genuine win for the user and a genuine loss for diagnosability. It was the only ambient indication that monitoring was alive. A silently disabled service now looks exactly like a working one right up until a blocked app opens without a pause.
- Safe modification: decide deliberately whether silent failure is acceptable. If it is not, the options are a periodic check with a notification, or making the home screen's status more prominent.

**`PauseViewModel.init()` is a reset, not an initializer:**
- Files: `ui/pause/PauseViewModel.kt:52-61`
- Why fragile: it has no idempotency guard, by design — `onNewIntent` can retarget the same ViewModel at a different app, so `init()` deliberately resets `pauseSeconds` to the default before re-reading. That is correct for retargeting, but it means any *unintended* second call silently re-reads `getTodayAttemptCount() + 1`, which will have moved if an event was recorded in between.
- Note: much less exposed than in June. The overlay path builds a fresh ViewModel per pause, so this only applies to the `PauseActivity` fallback.
- Safe modification: leave as-is, but if a guard is added it must not break the retarget case — the reset is load-bearing.

---

## Security considerations

Materially better than at the last review. The three items previously listed here are all
closed (see below), and the app's exposure is now unusually small: no network permission,
no exported components except the widget receiver the launcher requires, no backup, and
an accessibility service that cannot read window content.

**What remains worth watching:**
- The accessibility service is the highest-privilege thing in the app. Its narrow scope (`typeWindowStateChanged`, `canRetrieveWindowContent="false"`) is what makes the privacy claims in `PRIVACY.md` and `STORE_LISTING.md` true. **Widening either setting silently falsifies two public documents**, so treat `app/src/main/res/xml/accessibility_service_config.xml` as a file that cannot be changed without a matching documentation change.
- `PauseCountWidget` is `exported="true"`, which is required for the launcher to place it. It exposes no data beyond the counts already shown on the home screen.

---

## Dead code and stale comments

~~Found while revising these documents.~~ **All cleared 2026-08-14.** Each was verified
unreferenced before removal, and the full pass (`lintDebug testDebugUnitTest assembleDebug
assembleRelease`) was re-run afterwards: 43 tests still pass, lint warnings dropped from
50 to 47, and the release APK is unchanged at 1.03 MB.

- `app/build.gradle.kts` — `buildConfig = true`, commented "Needed so network logging can be gated to debug builds". Removed; nothing referenced `BuildConfig`, and `BuildConfig.java` is confirmed no longer generated
- `app/src/main/res/drawable/ic_notification.xml` — deleted; unreferenced since the foreground-service notification was removed
- `res/values/strings.xml` — `notification_title` and `notification_channel_name` deleted; there is no notification to name
- `ui/pause/PauseOverlayHost.kt` — the KDoc on `isShowing` described it as "read by the monitor loop from its polling thread". Rewritten to describe the accessibility event callback that actually reads it
- `app/build.gradle.kts` — the debug `applicationIdSuffix` comment referred to the schema-v4 migration; the schema is now v5

The reasoning behind that comment is unchanged and still worth honouring: debug keeps the
release `applicationId` so an upgrade-in-place can exercise the migration, which no one
has done yet.

---

## Test coverage gaps

See `TESTING.md` for the full picture. In short: 43 tests across 4 classes, all passing;
`BreatheAccessibilityService` and `HomeViewModel` have none; there are no instrumented
tests, and therefore no migration test against a schema that has moved 1→5 and dropped a
table.

---

## Closed since 2026-06-15

Kept for history. Each was verified as closed against the tree at `96eb5df`.

| Concern | Closed by |
|---|---|
| WorkManager declared but never used | Dependency removed |
| Quote refresh has no scheduling / no error state | Quote feature deleted entirely (`90f7e30`) — it was never rendered |
| Gson null-safety risk on `QuoteDto` | Gson and the DTO deleted with the network stack |
| HTTP logging interceptor active in release builds | `NetworkModule` deleted; no HTTP client remains |
| `allowBackup="true"` exposed the database to cloud backup | `allowBackup="false"` plus explicit `backup_rules.xml` and `data_extraction_rules.xml` |
| `BootReceiver` exported with no permission restriction | `BootReceiver` deleted — the system rebinds the accessibility service itself |
| 500ms poll loop with a Room query per cycle | Poll loop deleted; the service mirrors the blocked set in memory |
| `ForegroundAppDetector` 10-second query window unreliable on OEM devices | `ForegroundAppDetector` deleted; detection is push-based |
| `AppMonitorService` killed by OEM battery managers | Service deleted. **Note the risk did not vanish** — it moved to the accessibility service being disabled instead. See Fragile Areas |
| `approvedSessions` re-triggering on launcher flicker | Rewritten as `SessionApprovalStore`. **The underlying question is open again in a new form** — see blocker 2 in `APP_AUDIT.md` |
| Hardcoded `declined * 20` "time saved" | Replaced with persisted per-event `minutesSaved` (`c148554`) |
| `AppSelectScreen` dismissed on first tap, blocking multi-select | Picker now supports multi-select with an "Add N" action |
| `AppSelectViewModel` list did not react to changes | Loading, empty and error states are real state with retry |
| Kotlin 1.9.24 / KSP 1.9.24 outdated | Kotlin 2.2.10, KSP 2.2.10-2.0.2, AGP 9.2.1, Hilt 2.60.1 |
| `compileSdk` / `targetSdk` 34 below Play requirement | Both raised to 36 |
| No graceful degradation when usage access is revoked | Usage access is now optional; `HomeViewModel.isMonitoringActive` reports the permissions that actually matter |
| No "service is running" indicator | `isMonitoringActive` surfaced on the home screen |
| Zero tests exist | 43 tests across 4 classes |

---

*Concerns audit: 2026-06-15. Revised 2026-08-14 against `96eb5df`.*
