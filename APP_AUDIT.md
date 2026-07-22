# Breathe Application Audit

**Audit date:** 2026-07-21
**Scope:** all production Kotlin/Compose source, unit tests, Android manifest, Gradle configuration, resources, `README.md`, and `.planning/codebase/` documents.

## Verdict

The app has a coherent MVVM/Repository/Hilt/Room structure, but it is **not yet production-ready**. The documented core flow is implemented, along with stats, achievements, icons, and app selection, but several lifecycle and Android platform issues can make interception or event recording unreliable. The checked-in plans also describe older code and dependency versions.

## Blockers

1. **Background activity launch is unreliable on Android 10+.** `AppMonitorService` directly calls `startActivity()` from a foreground service. A foreground service is not a general exemption from background-activity-start restrictions, so the core pause screen may be blocked on modern devices. This needs a product/platform decision: a policy-compliant accessibility service, a supported device-management/app-control API, or a notification-driven interaction.
   - `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt:64`

2. **Stats writes can be cancelled.** `PauseActivity` calls `recordOpened()` or `recordDeclined()` and immediately finishes. Those methods launch work in `viewModelScope`; activity teardown clears the ViewModel and can cancel the Room insert.
   - `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt:57`
   - `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseViewModel.kt:57`

3. **Foreground detection loses the current app after five seconds.** The detector only scans recent `MOVE_TO_FOREGROUND` events. After service restart, delayed polling, screen unlock, or late permission grant, it can return `null` indefinitely until another app transition.
   - `app/src/main/java/com/dgraciano/breathe/service/ForegroundAppDetector.kt:10`

4. **A 28-day device event scan runs from the UI action path.** `SessionTimeHelper` scans usage history synchronously when the user declines, which can stall the main thread; the enclosing coroutine is then vulnerable to the teardown race above.
   - `app/src/main/java/com/dgraciano/breathe/service/SessionTimeHelper.kt:18`
   - `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseViewModel.kt:57`

5. **The unit-test suite is out of sync with production APIs.** `PauseViewModelTest` constructs the ViewModel with the old dependency list and asserts obsolete reason behavior. `ForegroundAppDetectorTest` mocks `queryUsageStats`, while production now calls `queryEvents`.
   - `app/src/test/java/com/dgraciano/breathe/ui/pause/PauseViewModelTest.kt:34`
   - `app/src/test/java/com/dgraciano/breathe/service/ForegroundAppDetectorTest.kt:23`

## High-priority corrections

- Make event recording a suspend operation and await successful persistence before navigation/finish; show a brief in-progress state and handle failure.
- Move usage-history analysis to `Dispatchers.IO`, bound its cost, and apply time-based cache invalidation.
- Redesign foreground detection around a durable cursor/last-known state and test restart, unlock, delayed-poll, and permission-grant scenarios.
- Add an idempotent `onStartCommand()` with an explicitly chosen restart policy. The current service only starts monitoring from `onCreate()`.
- Cache the blocked package set in the service instead of performing a Room `EXISTS` query every 500 ms; back off when the screen is off, permission is absent, or the blocked list is empty.
- Remove `QUERY_ALL_PACKAGES` if launcher-intent visibility is sufficient; declare a narrow `<queries>` launcher intent instead. The broad permission creates Play policy risk.
- Disable backup or exclude the Room database. It stores targeted apps, reasons, timestamps, and intervention outcomes.
- Gate OkHttp logging to debug builds.
- Make quote replacement transactional and reject empty/invalid API results so a failed refresh cannot erase the cache.
- Use persisted `minutesSaved` totals consistently. `StatsViewModel` still estimates `declines * 20`, contradicting recorded session estimates.
- Make the pause content scroll/adapt on small screens, landscape, and large font scales; respect reduced-motion settings for infinite animations.
- Add explicit loading, empty, and error states to the app picker and other ViewModels.
- Remove the unused WorkManager dependency unless periodic quote refresh is implemented.

## Plan traceability

| Documented item | Implementation status | Notes |
|---|---|---|
| Usage-access onboarding | Implemented | Returning-user behavior exists but plans describe older routing details. |
| Foreground monitoring | Implemented with blockers | Polling exists; modern background-launch restrictions and stale detection undermine reliability. |
| Blocked-app picker | Implemented | Broad package visibility and eager Drawable loading should be tightened. |
| Pause/breathing flow | Implemented with blockers | UI exists; persistence race and small-screen accessibility remain. |
| Quote API + Room cache | Implemented | No refresh TTL; replacement is non-transactional. |
| Stats screen | Implemented | README still marks it unfinished; calculations are internally inconsistent. |
| Achievements | Implemented | Missing from older architecture maps. |
| App icons/launch visuals | Implemented | README still marks icons unfinished. |
| Per-app custom pause duration | Not implemented | Roadmap item remains open. |
| Widget | Not implemented | Roadmap item remains open. |
| Play Store release readiness | Not complete | Background-launch design, package visibility, backup/privacy, tests, and release verification block it. |

## Documentation drift

- `README.md` roadmap incorrectly marks stats and app icons as unfinished.
- `.planning/codebase/TESTING.md` says no tests exist, but five test files are present.
- `.planning/codebase/STACK.md` documents older AGP/Kotlin/Hilt/KSP versions and a different Gradle heap size.
- `.planning/codebase/ARCHITECTURE.md`, `STRUCTURE.md`, and `CONCERNS.md` omit newer achievements/session-time features or refer to superseded detector behavior.

## Verification status

- Git working tree was clean before this audit.
- A temporary JDK 17 successfully started Gradle.
- `./gradlew test` could not reach compilation because no Android SDK is installed/configured in this environment (`SDK location not found`).
- Installing the SDK requires acceptance of Google's SDK license, so it was not performed automatically.
- Static review already proves the two test API mismatches above; a full `test lint assembleDebug` run is still required after an Android SDK 34 installation is configured.
