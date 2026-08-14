# Breathe Application Audit

**Audit date:** 2026-07-21
**Last revised:** 2026-08-14 — detection rebuilt on an AccessibilityService (`90f7e30`), replacing the UsageStats poll loop and its foreground service. The quote feature and the `INTERNET` permission were removed. Blocker 1 is unchanged in status and wider in scope: both halves of the interception path are now untested and unverified. One new blocker (2) is opened by the rewrite and is suspected rather than confirmed.
**Previously revised:** 2026-08-06 — every original blocker and correction had a fix in the tree; per-app pause duration and the home-screen widget implemented.
**Scope:** all production Kotlin/Compose source, unit tests, Android manifest, Gradle configuration, resources, `README.md`, and `.planning/codebase/` documents.

## Verdict

The app has a coherent MVVM/Repository/Hilt/Room structure. Since the original audit, every data-integrity, detection, and performance blocker has been fixed. The interception mechanism has been rebuilt twice: first onto an overlay window rather than a background Activity start, and then — in `90f7e30` — onto an AccessibilityService rather than a UsageStats poll loop.

The rewrite is a clear improvement in design. It removes a timer, a foreground service, a boot receiver, three permissions, the `specialUse` Play justification and its demo-video requirement, and it detects launches earlier than polling could. Removing the quote stack removed the `INTERNET` permission with it, so the app is now structurally incapable of transmitting anything — the strongest form of this app's central privacy claim, and one a reviewer can verify from the manifest.

It is still **not demonstrably working**, and the gap has widened rather than narrowed. Every known defect has a fix in the tree, but the central question — does the pause screen actually appear when a blocked app opens? — has never been observed on real hardware, and the rewrite replaced the one half of that path that *did* have test coverage with a new one that has none. The test suite went from 67 tests across 6 classes to 43 across 4; the 250-line `ForegroundAppDetectorTest` was deleted along with the detector it covered, and `BreatheAccessibilityService` arrived with no unit tests. Both halves of the interception path — detection and overlay — are now unverified by tests *and* unverified on hardware.

The rewrite also opens one new question that review alone cannot close: whether treating every window-state event as an app switch causes the notification shade or the keyboard to revoke a session approval and re-trigger the pause mid-session (blocker 2). It is reasoned from the code rather than observed, and it is cheap to check.

That single question — does the pause appear — still gates everything else, and no further code should be written against the interception path until it is answered. The rewrite does not change that conclusion; it enlarges the surface the answer has to cover.

## Blockers

1. **The interception path has never been observed working.** **Addressed in code twice, still unverified on hardware.**

   Two separate rewrites now sit under this one blocker, and neither has run on a device.

   **Display — the overlay.** The direction chosen was the `SYSTEM_ALERT_WINDOW` overlay. It was the obvious one: the app already declared the permission, onboarding already requested it, and the monitor already gated on `canDrawOverlays` — so it was paying the full cost of an overlay while still relying on an Activity start that the platform may refuse. `PauseOverlayHost` now draws `PauseScreen` in a `TYPE_APPLICATION_OVERLAY` window, supplying the lifecycle, ViewModelStore, and SavedStateRegistry owners a `ComposeView` would normally inherit from an Activity. `PauseActivity` remains as a fallback when overlay permission is missing.
   - `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseOverlayHost.kt`
   - `WindowManager` and `ComposeView` code has little unit-test surface, and the behaviour this blocker is about only appears on a real device. Watch specifically for: the overlay appearing at all, back-key behaviour, and survival across rotation.
   - Trade-off accepted, not eliminated: drawing over other apps carries its own Play review scrutiny. That is a policy risk taken in exchange for a technical one.

   **Detection — the accessibility service** (`90f7e30`). `BreatheAccessibilityService` receives `typeWindowStateChanged` events instead of polling `UsageStatsManager` every 500ms. The event arrives as the app comes to the front, so the pause can appear before the blocked app draws rather than up to half a poll interval later. The blocked set is mirrored into memory on `onServiceConnected`, so the event path never touches the database.
   - `app/src/main/java/com/dgraciano/breathe/service/BreatheAccessibilityService.kt`
   - **This replaced the best-tested component in the app with an untested one.** `ForegroundAppDetector` had 250 lines of tests covering restart, unlock, delayed-poll and permission-grant scenarios; it and they are gone, and nothing covers the new service. That is a defensible trade — the old tests largely encoded workarounds for polling's blind spots, which the new design does not have — but it means the detection path's correctness currently rests on nothing but review.
   - The service is scoped as narrowly as the feature allows: window-state events only, `canRetrieveWindowContent="false"`, and deliberately not `isAccessibilityTool` (Play policy excludes monitoring apps from that flag, and claiming it falsely risks account termination). A prominent disclosure with affirmative consent gates the trip to accessibility settings, as Play requires.
   - New risk introduced with it: accessibility services are user-disabled from a settings screen the app does not control, and some OEM battery managers switch them off silently. The app detects the enabled state (`isEnabled`) and surfaces it on the home screen, which is the right mitigation, but the failure mode "monitoring quietly stopped" is now more likely than it was with a foreground service holding a visible notification. **That notification was also the user's only ambient signal that the app was running, and it is gone.**

2. **Approval may be revoked by windows that are not app switches.** **Suspected, introduced by `90f7e30`, unverified.**

   `onAccessibilityEvent` treats every `typeWindowStateChanged` event with a new package name as "the user left the previous app" and calls `sessionApprovalStore.revoke(lastForeground)` (`BreatheAccessibilityService.kt:70-76`). That is the only revoke site in the app.

   But window-state events are not only fired by app switches. The notification shade, the input method, and system dialogs all raise them under their own package names — `com.android.systemui` and the active IME being the common cases. If they do so here, then pulling down the shade or opening the keyboard inside an approved app revokes that approval, and returning to the app re-triggers the pause mid-session.

   The old detector was not exposed to this: `UsageStatsManager` reports activity-level foreground transitions, so the shade and the IME never appeared as "the foreground app changed". The rewrite is what opens the question.

   This is reasoned from the code, not observed — whether a given window fires the event, and under which package, varies by Android version and OEM. It is cheap to check and would be an obvious irritant if real, so it belongs at the front of the device pass: approve a blocked app, pull down the notification shade, dismiss it, and see whether the pause returns. Repeat with the keyboard. If confirmed, the fix is to ignore packages that are not launchable apps rather than to treat every window as a switch.

3. ~~**Stats writes can be cancelled.**~~ **Resolved** (`7da8b3e`). Both writes now run on an injected `@ApplicationScope` (`SupervisorJob + Dispatchers.IO`), so they survive `PauseActivity` finishing and the ViewModel being cleared. The target package/app/reason are captured before launching, since `onNewIntent` can retarget the ViewModel mid-write. Three regression tests cover it.
   - A failing write is now caught and logged rather than escaping into the application scope. It still cannot be retried or shown to the user, because the pause screen is gone by then.

4. ~~**Foreground detection loses the current app after five seconds.**~~ **Resolved** (`7364025`), then **obsoleted** by `90f7e30`. `ForegroundAppDetector` remembered the last app it saw resume and reported it until a newer transition replaced it, with a 24-hour cold-start scan, incremental later scans, a cursor that only advanced on a non-empty result, and a 5s throttle on the wide-scan path.

   The class and its tests were deleted with the accessibility rewrite. The defect cannot recur, because the mechanism that produced it is gone: window events are pushed as they happen, so there is no cursor, no scan window, and no poll interval to lose an app inside. Recorded here rather than removed, because it explains why the replacement was worth making.

5. ~~**A 28-day device event scan runs from the UI action path.**~~ **Resolved** (`7da8b3e`, `3383360`). The scan moved off the main thread when the enclosing coroutine moved from `viewModelScope` (which dispatches to `Main.immediate`) to the IO-backed application scope. Its cost is now bounded — a 7-day window, since the platform only retains usage events for about a week, averaged over the 50 newest sessions — and cached averages carry a 6-hour TTL, so a user's session time is no longer frozen at whatever it was the first time they declined that app. The cache is a `ConcurrentHashMap`, because declines now share an application scope. `SessionTimeHelperTest` covers it.

6. ~~**The unit-test suite is out of sync with production APIs.**~~ **Resolved**, then **partly undone** by `90f7e30`. The suite no longer tests anything that does not exist — `ForegroundAppDetectorTest` and `QuoteRepositoryTest` went with the code they covered, and `PauseViewModelTest` matches the current constructor and reason semantics. It is in sync.

   It is also smaller: 43 tests across 4 classes, down from 67 across 6. The drop is not itself a defect — deleting tests for deleted code is correct — but 250 of those lines covered detection, and detection still exists. It was reimplemented, not removed. See "Test coverage" under corrections.

## High-priority corrections

Open:

- **Test coverage for detection.** `BreatheAccessibilityService` holds the launch-detection logic, the blocked-set mirror, and the approval lifecycle, and has no tests. The parts worth covering do not need a device: given a sequence of events, does the right package trigger a pause, is an approved app skipped, is approval revoked at the right moment, and is our own package ignored. `AccessibilityEvent` is awkward but not impossible to fake, and the revoke question in blocker 2 is exactly the kind of thing a test would pin down permanently rather than re-checking by hand each release.
- **No ambient signal that monitoring is alive.** The foreground-service notification is gone, which is a real gain for the user and a real loss for diagnosability. The home screen reports the service's enabled state, but a user whose OEM battery manager silently disabled accessibility will not be looking at the home screen — that is the one place the app is not, by definition, when monitoring should be happening. Worth deciding deliberately whether a silent stop is acceptable, rather than discovering the answer from a review.

Nothing else is outstanding from the original audit. What remains beyond the two items above is device verification (blockers 1 and 2) and the roadmap items below.

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
| Usage-access onboarding | Implemented, reshaped | Usage access is now optional and only enriches stats. Onboarding gates on accessibility plus overlay, behind a prominent disclosure with affirmative consent. Plans describe older routing details. |
| Foreground monitoring | Replaced, unverified | No longer a foreground service. Detection is an AccessibilityService reacting to window-state events; interception is an overlay window. Neither half has been seen running. |
| Blocked-app picker | Implemented | Broad package visibility and eager Drawable loading should be tightened. |
| Pause/breathing flow | Implemented | Persistence race fixed; small-screen and reduced-motion accessibility remain as corrections, not blockers. |
| Quote API + Room cache | **Removed** (`90f7e30`) | Never rendered — `quote` was an unused parameter on `PauseScreen`. The network stack, the table, and the `INTERNET` permission existed to populate a discarded value. Dropped in `MIGRATION_4_5`. Both copies of the privacy policy had disclosed ZenQuotes as a third party for a feature the user never saw. |
| Stats screen | Implemented | Calculations now consistent with recorded data. |
| Achievements | Implemented | Missing from older architecture maps. |
| App icons/launch visuals | Implemented | — |
| Per-app custom pause duration | Implemented | Stored per blocked app (schema v4), set from the home list, enforced by a countdown that gates the "continue" action. |
| Widget | Implemented | RemoteViews home-screen widget showing today's pause count and time won back; refreshed when an intervention is recorded. |
| Play Store release readiness | Build and paperwork ready; unverified | Signing, R8 config, privacy policy, data-safety answers, permission justifications and listing copy are in place (`RELEASE.md`, `PRIVACY.md`, `STORE_LISTING.md`). The permission story got simpler — no `specialUse` justification, no demo video, no `INTERNET` — and harder in one place: an AccessibilityService declaration draws its own review scrutiny. Device verification, the Pages toggle, and the visual assets still block submission. |

## Documentation drift

- `.planning/codebase/STACK.md` documents AGP 8.4.0, Kotlin 1.9.24, Hilt 2.51.1, and KSP 1.9.24-1.0.20. The project is on AGP 9.2.1, Kotlin 2.2.10, Hilt 2.60.1, and KSP 2.2.10-2.0.2.
- `.planning/codebase/ARCHITECTURE.md`, `STRUCTURE.md`, and `CONCERNS.md` omit newer achievements/session-time features and predate the application-scope and detector changes. They are now a full architecture behind: they describe a polling foreground service, a boot receiver, and a quote/network layer, none of which exist. Of everything in this section, these are the documents most likely to mislead someone picking the project up.
- `.planning/codebase/TESTING.md` describes a suite that no longer exists — it predates the deletion of `ForegroundAppDetectorTest` and `QuoteRepositoryTest`. Its noted gap (no Compose UI test dependency) still holds.
- `.planning/codebase/INTEGRATIONS.md` documents the ZenQuotes integration in detail — endpoint, caching strategy, DTO and interface paths — all of which point at deleted files. The app now has no external integrations at all, and no network layer to hold one.
- ~~`README.md`~~ **Fixed 2026-08-14.** It was staler than a first pass suggested: the rewrite updated the intro and roadmap but left the mechanism described as a 500ms `UsageStatsManager` poll inside a foreground service, the structure tree pointing at three deleted files and a deleted `remote/` package, the build listed as Gradle 8.7 against an actual 9.6.1, and — worst — a Permissions section stating the app "requires one special permission" and naming usage access as it. That was exactly inverted: usage access is now the only optional one, and neither accessibility nor overlay was mentioned. The "Key concepts" section, which exists to teach, explained a foreground service and a polling design that no longer exist; it now explains why the service was replaced, what the accessibility trade costs, and why `isAccessibilityTool` is deliberately not set.

## Verification status

- `./gradlew lintDebug testDebugUnitTest assembleDebug` passes as of 2026-08-14, re-run against `90f7e30`: **43 tests across 4 classes, 0 failures, 0 skipped**; debug APK builds; lint reports **0 errors and 50 warnings**. The test count is down from 67 across 6 classes — see blocker 6 for what was lost and why.
- Requires `JAVA_HOME` pointing at a JDK 17 — the Android Studio JBR at `C:\Program Files\Android\Android Studio\jbr` works; the shell has no `java` on `PATH` by default.
- The original audit could not compile at all (no Android SDK configured). The SDK is now present at `local.properties: sdk.dir`, so the earlier "static review only" caveat no longer applies.
- Lint is now error-free. It had been hiding a real crash: `OnboardingViewModel` called `AppOpsManager.unsafeCheckOpNoThrow`, which only exists from API 29, against a `minSdk` of 26 — onboarding would have thrown `NoSuchMethodError` on API 26-28. Now version-gated with the pre-29 `checkOpNoThrow`.
- `./gradlew assembleRelease` succeeds, re-run 2026-08-14: R8 shrinking passes with the reflective keep rules, producing a **1.03 MB** APK plus a `mapping.txt` for crash deobfuscation. It was 1.3 MB before Retrofit, OkHttp and Gson came out. The keep rules were rewritten with the same commit — the Gson and Retrofit rules are gone and `BreatheAccessibilityService` has one (`proguard-rules.pro:20`), which matters because the system instantiates it by name and a missing rule would break detection in release builds only. That the build succeeds does not prove the rule is sufficient; only a release-build device run does. The release build itself has still not been run.
- `targetSdk` raised from 34 to 36 for Play compliance. Lint stays error-free, but the Android 15/16 behaviour changes that come with it are unverified.
- Still unverified on hardware: no instrumented or on-device run has been performed. This now matters more than it did before, and more than it did at the last revision. Both halves of the interception path have been rewritten since anyone last claimed it worked, and neither half has tests. The unit suite says nothing about whether the pause screen appears, and it no longer says anything about whether a launch is detected either.
- Emulator note: `README.md` records that `UsageStatsManager` is unreliable on emulators. That caveat is narrower now — detection no longer uses it — but an emulator is still a poor venue for this verification, because overlay behaviour, OEM accessibility handling, and the battery managers that silently disable services are exactly what an emulator does not reproduce.
- Note when writing usage-event fixtures: events carry real epoch timestamps, and the production code reads a zero timestamp as "no session in progress". Fixtures that start at zero are silently dropped and can make a test pass for the wrong reason.
