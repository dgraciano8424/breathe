# Testing Patterns

**Analysis Date:** 2026-06-15
**Revised:** 2026-08-14 — brought in line with the tree at `96eb5df`. The 2026-06-15
version stated that no tests existed and offered speculative examples built on
`QuoteRepository`, `ZenQuotesApi` and `ForegroundAppDetector`. Tests now exist, and all
three of those classes have been deleted. The examples below are taken from the real
suite.

## Test Framework

**Runner:**
- JUnit 4 (`junit:junit:4.13.2`) — configured in `app/build.gradle.kts`
- Android instrumentation runner: `androidx.test.runner.AndroidJUnitRunner` (declared in `defaultConfig`, currently unused — there are no instrumented tests)
- `testOptions { unitTests { isReturnDefaultValues = true } }` — Android framework stubs return defaults rather than throwing, which is what lets these tests touch framework types without Robolectric

**Assertion Library:**
- JUnit 4 assertions (bundled with JUnit 4)
- MockK (`io.mockk:mockk:1.13.11`) for mocking

**Additional testing utilities:**
- `kotlinx-coroutines-test:1.8.1` — for testing suspend functions and coroutine flows

**Run Commands:**
```bash
./gradlew testDebugUnitTest     # Run the unit suite
./gradlew lintDebug testDebugUnitTest assembleDebug   # What the audit records as the standard pass
./gradlew connectedAndroidTest  # Instrumented tests — nothing to run yet
```

`JAVA_HOME` must point at a JDK 17. The Android Studio JBR works; a bare shell usually
has no `java` on `PATH`.

## Current Test Coverage State

**43 tests across 4 classes**, all passing as of 2026-08-14:

| Class | Tests | Covers |
|---|---|---|
| `data/repository/AppRepositoryTest.kt` | 8 | Blocked-app CRUD and pause-length reads |
| `data/repository/StatsRepositoryTest.kt` | 7 | Aggregations behind the stats screens |
| `service/SessionTimeHelperTest.kt` | 10 | Session-average estimation from usage events, cache TTL, bounded scan |
| `ui/pause/PauseViewModelTest.kt` | 18 | Pause state, reason capture, outcome recording, approval and widget refresh |

**This is down from 67 tests across 6 classes.** `QuoteRepositoryTest` (160 lines) and
`ForegroundAppDetectorTest` (250 lines) were deleted in `90f7e30` with the code they
covered. Deleting tests for deleted code is correct; the problem is that detection was
*reimplemented*, not removed, and its replacement arrived with no tests at all.

### The gap that matters

`BreatheAccessibilityService` is the least-tested and highest-risk class in the project.
It holds launch detection, the in-memory blocked-set mirror, and the session-approval
lifecycle, and nothing exercises any of it.

Most of it does not need a device. Given a sequence of fake `AccessibilityEvent`s, a test
could pin down:

- a blocked package triggers a pause; an unblocked one does not
- an already-approved package does not re-trigger
- the app's own package is ignored
- events arriving while the overlay is up are ignored
- **approval is revoked when the user leaves — and only then.** This is the open question
  in blocker 2 of `APP_AUDIT.md`: every window-state event with a new package currently
  counts as "left the app", including the notification shade and the IME. A test here
  would settle it permanently instead of re-checking by hand each release

## Test Patterns In Use

### ViewModel tests: constructor injection, MockK, injected scope

`PauseViewModel` takes seven collaborators and an `appScope`. Tests pass a
`CoroutineScope` built on the test dispatcher, which is what makes the detached
application-scope writes observable — the writes deliberately outlive the pause screen in
production, so a test needs to hold the scope to assert on them.

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class PauseViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        appScope = CoroutineScope(testDispatcher)
        tipsRepo = mockk {
            every { getRandomTip() } returns MentalHealthTip("Ground Yourself", "…", "ground")
            every { getRandomActivity() } returns "Step outside for 2 minutes"
        }
        sessionApprovalStore = mockk(relaxed = true)
        widgetRefresher = mockk(relaxed = true)
        appRepo = mockk {
            coEvery { getPauseSeconds(any()) } returns BlockedApp.DEFAULT_PAUSE_SECONDS
        }
        viewModel = PauseViewModel(statsRepo, appRepo, tipsRepo, sessionTimeHelper,
                                   sessionApprovalStore, widgetRefresher, appScope)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        appScope.cancel()
    }
}
```

Conventions visible in that setup, worth keeping:

- `mockk { }` with the stubs inline, rather than a bare `mockk()` followed by a wall of `every` calls
- `relaxed = true` reserved for collaborators the test does not assert on (`widgetRefresher`, `sessionApprovalStore` when it is incidental)
- `slot()` + `coVerify` to capture the `InterventionEvent` actually written, rather than asserting on a repository call count

### Usage-event fixtures

`SessionTimeHelperTest` builds `UsageEvents` fixtures. One trap is recorded in
`APP_AUDIT.md` and worth repeating, because it makes a test pass for the wrong reason:

> Events carry real epoch timestamps, and the production code reads a zero timestamp as
> "no session in progress". Fixtures that start at zero are silently dropped.

### What to mock

- DAOs in repository tests
- Repositories in ViewModel tests
- `UsageStatsManager` in `SessionTimeHelper` tests
- System services generally, via the `SystemServiceModule` seam

### What not to mock

- Kotlin data classes and model objects — construct them directly
- Room itself. DAO behaviour belongs in an instrumented test with an in-memory database, not a mock

## Test Types

**Unit Tests (JVM — `src/test/`):**
- Scope: repositories, ViewModels, and framework-adjacent logic that can be driven through an injected seam
- Target: business logic, state transitions, error handling branches

**Integration/Instrumented Tests (`src/androidTest/`):**
- **None exist.** The directory is empty
- The most valuable candidates, in order: a **migration test** covering 1→5 (the app has real installs and `MIGRATION_4_5` drops a table), then DAO tests against an in-memory Room database

**E2E / UI Tests:**
- No Compose UI test dependency declared (`androidx.compose.ui:ui-test-junit4` is absent from `libs.versions.toml`)
- Compose UI testing is not currently configured

## What tests cannot answer here

This project's central risk is not covered by any unit test and cannot be. The pause
appearing at all depends on `WindowManager` accepting an overlay and on the system
delivering accessibility events — neither of which has a meaningful JVM test surface, and
neither of which has ever been observed working on hardware.

A green suite here means the logic around the mechanism is sound. It says nothing about
the mechanism. See the device checklist in `RELEASE.md`; that checklist, not this suite,
is what currently gates release.

## Coverage

**Requirements:** None enforced — no JaCoCo or coverage threshold configuration present.

## Key Gaps

- **No tests for `BreatheAccessibilityService`** — the highest-risk class in the project, and most of it is testable without a device. See above
- **No instrumented tests at all** — in particular no migration test, against a schema that has moved 1→5 and dropped a table
- **No Compose UI test dependency** — `ui-test-junit4` and `ui-test-manifest` are absent from `libs.versions.toml`
- **No Hilt testing dependency** — `hilt-android-testing` is not declared; Hilt injection in instrumented tests would need it or manual setup
- **No CI** — every run is manual, so "the suite passes" means "someone ran it recently"
- **No JaCoCo coverage enforcement** — coverage is not measured or gated

---

*Testing analysis: 2026-06-15. Revised 2026-08-14 against `96eb5df`.*
