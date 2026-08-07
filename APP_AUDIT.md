# Breathe Application Audit

**Audit date:** 2026-07-21
**Last revised:** 2026-08-06 — every original blocker and correction now has a fix in the tree; blocker 1 remains unverified on hardware. Per-app pause duration and the home-screen widget implemented.
**Scope:** all production Kotlin/Compose source, unit tests, Android manifest, Gradle configuration, resources, `README.md`, and `.planning/codebase/` documents.

## Verdict

The app has a coherent MVVM/Repository/Hilt/Room structure. Since the original audit, every data-integrity, detection, and performance blocker has been fixed, and the test suite has been brought back in sync with production APIs and extended. The interception mechanism has been rebuilt on an overlay window rather than a background Activity start.

It is **not yet demonstrably working**, which is a different claim from the earlier "not production-ready". Every known defect has a fix in the tree, but the central one — does the pause screen actually appear when a blocked app opens? — has never been observed on real hardware. That single question now gates everything else, and no further code should be written against the interception path until it is answered.

## Blockers

1. **Background activity launch is unreliable on Android 10+.** **Addressed in code, unverified on hardware.**

   The direction chosen was the `SYSTEM_ALERT_WINDOW` overlay. It was the obvious one: the app already declared the permission, onboarding already requested it, and the monitor already gated on `canDrawOverlays` — so it was paying the full cost of an overlay while still relying on an Activity start that the platform may refuse. `PauseOverlayHost` now draws `PauseScreen` in a `TYPE_APPLICATION_OVERLAY` window, supplying the lifecycle, ViewModelStore, and SavedStateRegistry owners a `ComposeView` would normally inherit from an Activity. `PauseActivity` remains as a fallback when overlay permission is missing.
   - `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseOverlayHost.kt`
   - **This is the one change in the recent series with no test coverage and no device run.** `WindowManager` and `ComposeView` code has little unit-test surface, and the behavior this blocker is about only appears on a real device. Until someone installs the debug APK and opens a blocked app, treat this as unproven. Watch specifically for: the overlay appearing at all, back-key behavior, and survival across rotation.
   - Trade-off accepted, not eliminated: drawing over other apps carries its own Play review scrutiny. That is a policy risk taken in exchange for a technical one.

2. ~~**Stats writes can be cancelled.**~~ **Resolved** (`7da8b3e`). Both writes now run on an injected `@ApplicationScope` (`SupervisorJob + Dispatchers.IO`), so they survive `PauseActivity` finishing and the ViewModel being cleared. The target package/app/reason are captured before launching, since `onNewIntent` can retarget the ViewModel mid-write. Three regression tests cover it.
   - A failing write is now caught and logged rather than escaping into the application scope. It still cannot be retried or shown to the user, because the pause screen is gone by then.

3. ~~**Foreground detection loses the current app after five seconds.**~~ **Resolved** (`7364025`). `ForegroundAppDetector` remembers the last app it saw resume and reports it until a newer transition replaces it. Cold start scans back 24 hours to recover an already-open app; later scans are incremental with a 1s overlap. The cursor only advances on a non-empty result, so usage access granted after the first poll still recovers, and a 5s throttle bounds the wide-scan path. Now `@Singleton`, since the cached state depends on a single instance.

4. ~~**A 28-day device event scan runs from the UI action path.**~~ **Resolved** (`7da8b3e`, `3383360`). The scan moved off the main thread when the enclosing coroutine moved from `viewModelScope` (which dispatches to `Main.immediate`) to the IO-backed application scope. Its cost is now bounded — a 7-day window, since the platform only retains usage events for about a week, averaged over the 50 newest sessions — and cached averages carry a 6-hour TTL, so a user's session time is no longer frozen at whatever it was the first time they declined that app. The cache is a `ConcurrentHashMap`, because declines now share an application scope. `SessionTimeHelperTest` covers it.

5. ~~**The unit-test suite is out of sync with production APIs.**~~ **Resolved.** `ForegroundAppDetectorTest` exercises the current `queryEvents` implementation, and `PauseViewModelTest` matches the current constructor and reason semantics. The suite is 67 tests across 6 classes and passes.

## High-priority corrections

Open:

- Nothing outstanding from the original audit. What remains is device verification (blocker 1) and the roadmap items below.

Done:

- ~~Add an idempotent `onStartCommand()` with an explicitly chosen restart policy.~~ Done. Returns `START_STICKY` and no-ops when the monitor loop is already running, so a redelivered start command cannot stack a second loop.
- ~~Cache the blocked package set in the service; back off when idle.~~ Done. The service collects the blocked-apps Flow into an in-memory set instead of running a Room `EXISTS` query twice a second, and drops to a 3s poll when the screen is off, the overlay is up, or nothing is monitored.
- ~~Remove `QUERY_ALL_PACKAGES`.~~ Done. Replaced with a `<queries>` launcher-intent filter, which is all the picker ever needed. This removes the restricted-permission declaration that would have required a Play justification.
- ~~Disable backup or exclude the Room database.~~ Done. `allowBackup="false"`, plus explicit `backup_rules.xml` and `data_extraction_rules.xml` excluding the database from cloud backup and device transfer.
- ~~Gate OkHttp logging to debug builds.~~ Done, behind `BuildConfig.DEBUG` (which required enabling the `buildConfig` build feature).
- ~~Make quote replacement transactional and reject empty/invalid API results.~~ Done via `QuoteDao.replaceAll()` under `@Transaction`, with null/blank entries filtered and empty results rejected before the swap. The DTO fields are now nullable, which is what Gson actually produces.
- ~~Make the pause content scroll and respect reduced motion.~~ Done. The pause column scrolls, and a shared `rememberReducedMotion()` holds the breathing rings and Nimbus still when the system animation scale is zero.
- ~~Add explicit loading, empty, and error states to the app picker.~~ Done. `isLoading` and `errorMessage` are real state rather than inferred from an empty list, with a retry action.
- ~~Remove the unused WorkManager dependency.~~ Done, from both the build file and the version catalog.
- ~~Surface write failures from the intervention-event path.~~ Done, as logging. The screen is gone by the time these run, so there is no user to notify and nothing to retry into; the goal was making a missing statistic diagnosable rather than invisible.
- ~~Make event recording survive navigation/teardown.~~ Done via the application scope (`7da8b3e`). Note this diverges from the original recommendation: rather than awaiting persistence behind an in-progress state, the write is detached so the user's Yes/No stays instant. Blocking the exit on a Room write plus a usage scan was the wrong trade for this interaction.
- ~~Move usage-history analysis to `Dispatchers.IO`, bound its cost, and apply time-based cache invalidation.~~ Done (`7da8b3e`, `3383360`).
- ~~Redesign foreground detection around a durable cursor/last-known state and test restart, unlock, delayed-poll, and permission-grant scenarios.~~ Done (`7364025`), with tests for each scenario.
- ~~Use persisted `minutesSaved` totals consistently.~~ Done (`c148554`). `StatsViewModel` and `HomeScreen` both read recorded per-event minutes; the `declines * 20` estimate is gone.

## Plan traceability

| Documented item | Implementation status | Notes |
|---|---|---|
| Usage-access onboarding | Implemented | Returning-user behavior exists but plans describe older routing details. |
| Foreground monitoring | Implemented, unverified | Detection is durable; interception moved to an overlay window that no one has yet seen run. |
| Blocked-app picker | Implemented | Broad package visibility and eager Drawable loading should be tightened. |
| Pause/breathing flow | Implemented | Persistence race fixed; small-screen and reduced-motion accessibility remain as corrections, not blockers. |
| Quote API + Room cache | Implemented | No refresh TTL; replacement is non-transactional. |
| Stats screen | Implemented | Calculations now consistent with recorded data. |
| Achievements | Implemented | Missing from older architecture maps. |
| App icons/launch visuals | Implemented | — |
| Per-app custom pause duration | Implemented | Stored per blocked app (schema v4), set from the home list, enforced by a countdown that gates the "continue" action. |
| Widget | Implemented | RemoteViews home-screen widget showing today's pause count and time won back; refreshed when an intervention is recorded. |
| Play Store release readiness | Build and paperwork ready; unverified | Signing, R8 config, privacy policy, data-safety answers, and permission justifications are all in place (`RELEASE.md`, `PRIVACY.md`). Device verification and a hosted policy URL still block submission. |

## Documentation drift

- `.planning/codebase/STACK.md` documents AGP 8.4.0, Kotlin 1.9.24, Hilt 2.51.1, and KSP 1.9.24-1.0.20. The project is on AGP 9.2.1, Kotlin 2.2.10, Hilt 2.60.1, and KSP 2.2.10-2.0.2.
- `.planning/codebase/ARCHITECTURE.md`, `STRUCTURE.md`, and `CONCERNS.md` omit newer achievements/session-time features and predate the application-scope and detector changes.
- `.planning/codebase/TESTING.md` is broadly accurate; its noted gap (no Compose UI test dependency) still holds.
- `README.md` roadmap is now accurate — the earlier claim that it mismarked stats and app icons no longer applies.

## Verification status

- `./gradlew lintDebug testDebugUnitTest assembleDebug` passes: 67 tests across 6 classes, debug APK builds, lint reports no errors (51 pre-existing warnings remain).
- Requires `JAVA_HOME` pointing at a JDK 17 — the Android Studio JBR at `C:\Program Files\Android\Android Studio\jbr` works; the shell has no `java` on `PATH` by default.
- The original audit could not compile at all (no Android SDK configured). The SDK is now present at `local.properties: sdk.dir`, so the earlier "static review only" caveat no longer applies.
- Lint is now error-free. It had been hiding a real crash: `OnboardingViewModel` called `AppOpsManager.unsafeCheckOpNoThrow`, which only exists from API 29, against a `minSdk` of 26 — onboarding would have thrown `NoSuchMethodError` on API 26-28. Now version-gated with the pre-29 `checkOpNoThrow`.
- `./gradlew assembleRelease` succeeds: R8 shrinking passes with the reflective keep rules, producing a 1.3 MB APK plus a `mapping.txt` for crash deobfuscation. The release build itself has not been run.
- `targetSdk` raised from 34 to 36 for Play compliance. Lint stays error-free, but the Android 15/16 behaviour changes that come with it are unverified.
- Still unverified on hardware: no instrumented or on-device run has been performed. This now matters more than it did, because the overlay rewrite of the interception path rests entirely on it. The unit suite says nothing about whether the pause screen appears.
- Note when writing usage-event fixtures: events carry real epoch timestamps, and the production code reads a zero timestamp as "no session in progress". Fixtures that start at zero are silently dropped and can make a test pass for the wrong reason.
