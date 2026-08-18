# Quiet Ocean Reliability and Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a dependable Accessibility-to-pause flow and a cohesive, accessible Quiet Ocean Android experience from setup through reflection.

**Architecture:** Pure foreground normalization/classification feeds a singleton intervention coordinator. The coordinator owns visit state, presentation requests, monitoring health, and delegates committed decisions to a repository contract. Android service/overlay/activity code remains thin adapters. Compose routes collect immutable state into previewable content roots backed by semantic theme, motion, and responsive primitives.

**Tech Stack:** Kotlin 2.2, Android API 26–36, Compose Material 3, Hilt, Room 2.8, coroutines/Flow, JUnit4, MockK, AndroidX/Hilt/Compose instrumentation tests, Gradle 9.6/AGP 9.2, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-18-quiet-ocean-reliability-and-polish-design.md`

## Global Constraints

- Work only in `/Users/dgraciano/Documents/Breathe/.worktrees/quiet-ocean` on `codex/quiet-ocean`.
- Keep one `com.dgraciano.breathe.service.BreatheAccessibilityService` and the existing overlay architecture; do not merge the stale Scope 1 service.
- Preserve minSdk 26, targetSdk 36, Java 17, offline behavior, `canRetrieveWindowContent="false"`, and absence of `INTERNET`, `QUERY_ALL_PACKAGES`, and foreground-service permissions.
- Accessibility and overlay permissions are authoritative. Usage Access is optional enrichment.
- Every behavioral task records a targeted RED command before production edits, then a GREEN command after implementation.
- Use the exact tokens, copy, health mapping, semantics, responsive configurations, motion table, and release thresholds in the spec.
- No backend, account, new destination, XP/evolution expansion, onboarding-complete flag, boot receiver, or polling service.
- A task ends with a compiling, independently reviewable commit. Do not combine tasks.
- Automated completion does not imply device/release completion; missing hardware remains `BLOCKED: DEVICE EVIDENCE`.

---

### Task 1: Android and Compose Test Harness

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/HiltTestRunner.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/TestHarnessSmokeTest.kt`

**Interfaces:**
- Produces AndroidX runner/rules/JUnit, Hilt Android testing/compiler, Compose UI test JUnit4/manifest, and Room testing aliases for all later instrumentation tasks.

- [ ] **Step 1: Add a failing smoke test**

```kotlin
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TestHarnessSmokeTest {
    @get:Rule val hiltRule = HiltAndroidRule(this)
    @Test fun applicationStartsUnderHiltTestRunner() { hiltRule.inject() }
}
```

- [ ] **Step 2: Verify RED**

Run `./gradlew :app:compileDebugAndroidTestKotlin`.

Expected: missing AndroidX/Hilt test classes or runner.

- [ ] **Step 3: Configure the harness**

Add aliases for `androidx.test:runner:1.6.2`, `androidx.test.ext:junit:1.2.1`, `androidx.test:rules:1.6.1`, `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest`, `com.google.dagger:hilt-android-testing`, and `androidx.room:room-testing`. Add `androidTestImplementation`, `kspAndroidTest`, and debug manifest dependencies. Set `testInstrumentationRunner` to `com.dgraciano.breathe.HiltTestRunner`; the runner returns `HiltTestApplication`.

- [ ] **Step 4: Verify GREEN**

Run `./gradlew :app:compileDebugAndroidTestKotlin`. If a device is connected, also run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.TestHarnessSmokeTest`; otherwise record only compile success.

- [ ] **Step 5: Commit**

Commit as `test: establish Android instrumentation harness`.

---

### Task 2: Foreground Observation Normalizer and Classifier

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/service/ForegroundObservation.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/service/ForegroundObservationNormalizer.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/service/ForegroundTransitionClassifier.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/service/ForegroundObservationNormalizerTest.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/service/ForegroundTransitionClassifierTest.kt`

**Interfaces:**
- Produces `ForegroundObservationNormalizer.classify(facts): ForegroundSurface`, `ForegroundTransitionClassifier.reduce(state, observation)`, and `reconcileAfterMonitoredPackagesReady(state, packages)`; no Android framework types.

- [ ] **Step 1: Write failing normalization and reducer tests**

```kotlin
@Test fun `active IME is classified as system surface`() {
    val facts = facts(packageName = "com.keyboard", activeImePackage = "com.keyboard")
    assertEquals(ForegroundSurface.ACTIVE_IME, normalizer.classify(facts))
}

@Test fun `deferred selected app reconciles exactly once after packages load`() {
    val waiting = classifier.reduce(emptyState(), observation("com.social", ELIGIBLE_APP)).state
    val ready = classifier.reconcileAfterMonitoredPackagesReady(waiting, setOf("com.social"))
    assertEquals(listOf(ForegroundAction.RequestPause("com.social")), ready.actions)
}
```

Cover Breathe, System UI, IME, Settings/permission, non-launchable packages, same-app churn, system-surface interruption, meaningful leave/return, approval revocation, and process restart.

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.service.ForegroundObservationNormalizerTest' \
  --tests 'com.dgraciano.breathe.service.ForegroundTransitionClassifierTest'
```

Expected: compile failure because the pure types do not exist.

- [ ] **Step 3: Implement minimal pure boundaries**

```kotlin
data class ForegroundFacts(
    val packageName: String,
    val ownPackage: String,
    val activeImePackage: String?,
    val systemUiPackages: Set<String>,
    val settingsPackages: Set<String>,
    val launchablePackages: Set<String>,
)

enum class ForegroundSurface { ELIGIBLE_APP, BREATHE, SYSTEM_UI, ACTIVE_IME, SETTINGS_OR_PERMISSION, NON_LAUNCHABLE }
data class ForegroundObservation(val packageName: String, val componentName: String?, val observedAtElapsedMs: Long, val surface: ForegroundSurface)
data class ForegroundTransition(val state: ForegroundClassifierState, val actions: List<ForegroundAction>)
```

- [ ] **Step 4: Verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.service.ForegroundObservationNormalizerTest' \
  --tests 'com.dgraciano.breathe.service.ForegroundTransitionClassifierTest'
./gradlew :app:testDebugUnitTest
```

Expected: all pass.

- [ ] **Step 5: Commit**

Commit as `feat(service): classify meaningful foreground transitions`.

---

### Task 3: Decision Repository and Usage Estimate Boundary

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/data/repository/PauseDecisionSink.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/data/repository/InterventionDecisionRepository.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/service/UsageAccessReader.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/service/SessionTimeHelper.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/data/repository/InterventionDecisionRepositoryTest.kt`

**Interfaces:**
- Produces a concrete singleton `PauseDecisionSink` for Task 4 and a reusable optional `UsageAccessReader` for Task 7.

- [ ] **Step 1: Write failing insert-order tests**

```kotlin
@Test fun `decline commits zero before estimate then updates generated id`() = runTest {
    coEvery { dao.insert(any()) } returns 42L
    coEvery { usage.averageSessionMinutes("com.social") } returns 8
    sink.record(decision("com.social", DecisionOutcome.DECLINED, null))
    coVerifyOrder {
        dao.insert(match { it.minutesSaved == 0 })
        widget.refresh()
        usage.averageSessionMinutes("com.social")
        dao.updateMinutesSaved(42L, 8)
        widget.refresh()
    }
}
```

Also cover insert failure, enrichment failure/cancellation, opened outcome without scan, and second refresh only after successful update.

- [ ] **Step 2: Verify RED**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.data.repository.InterventionDecisionRepositoryTest'`.

Expected: missing insert-return/update/sink APIs.

- [ ] **Step 3: Implement concrete contracts**

```kotlin
enum class DecisionOutcome { DECLINED, OPENED }
data class PauseDecision(val requestId: String, val packageName: String, val appName: String, val outcome: DecisionOutcome, val reason: String?)
interface PauseDecisionSink { suspend fun record(decision: PauseDecision): Result<Long> }
interface UsageAccessReader {
    suspend fun usageByPackage(startMs: Long, endMs: Long): Map<String, Long>
    suspend fun averageSessionMinutes(packageName: String): Int
}
```

Change DAO insert to `Long`; add update-by-ID. Insert zero, refresh, then enrich declined outcomes. Catch `SecurityException` at the usage boundary and return empty/zero.

- [ ] **Step 4: Verify GREEN**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.data.repository.InterventionDecisionRepositoryTest' && ./gradlew :app:testDebugUnitTest`.

- [ ] **Step 5: Commit**

Commit as `feat(data): commit pause decisions before enrichment`.

---

### Task 4: Monitoring Health, Presentation Protocol, and Coordinator

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/service/MonitoringHealth.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/service/PausePresentation.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/service/InterventionCoordinator.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/service/SessionApprovalStore.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/service/InterventionCoordinatorTest.kt`

**Interfaces:**
- Consumes Task 2 classifier and Task 3 `PauseDecisionSink`.
- Produces singleton coordinator API and closed `MonitoringHealth` for adapters/UI.

- [ ] **Step 1: Write failing coordinator tests**

```kotlin
@Test fun `missing acknowledgement degrades and late acknowledgement is ignored`() = runTest {
    coordinator.connectReadyState()
    coordinator.onForegroundObservation(selectedObservation())
    val requestId = presentation.lastRequest.id
    advanceTimeBy(2_500)
    assertEquals(DegradedReason.PresentationNotConfirmed, (coordinator.health.value as MonitoringHealth.Degraded).reason)
    coordinator.acknowledgeActivityContentReady(requestId)
    assertTrue(coordinator.health.value is MonitoringHealth.Degraded)
}

@Test fun `Back approves visit and records no decision`() = runTest {
    coordinator.connectReadyState()
    coordinator.onForegroundObservation(selectedObservation())
    coordinator.dismissWithBack(presentation.lastRequest.id)
    coordinator.onForegroundObservation(selectedObservation())
    assertEquals(1, presentation.showCount)
    coVerify(exactly = 0) { decisionSink.record(any()) }
}
```

Test overlay acknowledgement, fallback acknowledgement, timeout, late acknowledgement, presentation loss, permission change, package readiness, retry-once recovery, exact `Active` prerequisites, and opened/declined delegation.

- [ ] **Step 2: Verify RED**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.service.InterventionCoordinatorTest'`.

Expected: missing coordinator/health/presentation types.

- [ ] **Step 3: Implement the exact protocol**

```kotlin
interface PausePresentationCallbacks {
    fun onOverlayAdded(requestId: String)
    fun onOverlayFailed(requestId: String, cause: Throwable)
    fun onActivityContentReady(requestId: String)
    fun onPresentationLost(requestId: String, cause: Throwable?)
}
interface PausePresentation {
    fun show(request: PauseRequest, callbacks: PausePresentationCallbacks)
    fun dismiss(requestId: String)
}
```

`@Singleton class InterventionCoordinator @Inject constructor(...)` owns one pending request, injected application scope/test dispatcher, 2.5s timeout, approval/retry suppression, and `StateFlow<MonitoringHealth>`.

- [ ] **Step 4: Verify GREEN**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.service.InterventionCoordinatorTest' --tests 'com.dgraciano.breathe.service.ForegroundTransitionClassifierTest' --tests 'com.dgraciano.breathe.data.repository.InterventionDecisionRepositoryTest' && ./gradlew :app:testDebugUnitTest`.

- [ ] **Step 5: Commit**

Commit as `feat(service): coordinate monitoring health and pause requests`.

---

### Task 5: Android Service, Overlay, Activity, and Hilt Wiring

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/service/BreatheAccessibilityService.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/service/AccessibilityServiceState.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseOverlayHost.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingViewModel.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/di/PausePresentationModule.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/service/AccessibilityServiceStateTest.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/pause/PauseActivityAcknowledgementTest.kt`

**Interfaces:**
- Consumes Tasks 2–4.
- Produces Android adapters bound to the same singleton coordinator instance.

- [ ] **Step 1: Write exact-component and Activity-ready tests**

Use a namespace-correct service parser test and an Hilt instrumented Activity test that passes `EXTRA_REQUEST_ID`, resumes content, verifies one acknowledgement, then presses Back and verifies dismissal.

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.service.AccessibilityServiceStateTest'
./gradlew :app:compileDebugAndroidTestKotlin
```

Expected: parser/acknowledgement hooks absent.

- [ ] **Step 3: Implement adapters and DI**

In `PausePresentationModule`, bind `PauseOverlayHost` to `PausePresentation` and `InterventionDecisionRepository` to `PauseDecisionSink` with separate `@Binds @Singleton` methods. `InterventionCoordinator` injects `PauseDecisionSink`; service, Activity, and overlay callback paths inject the same `@Singleton` coordinator. Normalize framework facts through Task 2. Remove service-owned visit state/direct overlay calls. Overlay add acknowledges; fallback carries the same request ID; Activity acknowledges once after `onResume` and content readiness; Back delegates to coordinator.

- [ ] **Step 4: Verify GREEN**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.service.AccessibilityServiceStateTest' && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:compileDebugKotlin`. If connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.ui.pause.PauseActivityAcknowledgementTest`. Then run `rg -n 'BreatheAccessibilityService' app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` and require exactly one service declaration.

- [ ] **Step 5: Commit**

Commit as `feat(service): wire Android pause presentation adapters`.

---

### Task 6: Reactive Aggregate Projections and Consumers

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/data/db/CommittedAggregates.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/data/repository/StatsRepository.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/data/repository/AchievementRepository.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/stats/StatsViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/achievements/AchievementsViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/widget/PauseCountWidget.kt`
- Modify: `app/src/test/java/com/dgraciano/breathe/data/repository/StatsRepositoryTest.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/data/repository/AggregateConsumersTest.kt`

**Interfaces:**
- Produces `Flow<TodayAggregate>` and `Flow<LifetimeAggregate>` shared by Home, Insights, Journey, and widget reads.

- [ ] **Step 1: Write failing aggregate-flow tests**

Assert Home and Stats receive the same post-insert and post-enrichment totals, and Journey updates from `LifetimeAggregate` rather than snapshot DAO calls.

- [ ] **Step 2: Verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.data.repository.StatsRepositoryTest' \
  --tests 'com.dgraciano.breathe.data.repository.AggregateConsumersTest'
```

Expected: projection APIs absent or consumers remain snapshot-based.

- [ ] **Step 3: Implement projections and migrate consumers**

```kotlin
data class TodayAggregate(val attempts: Int, val declined: Int, val minutesSaved: Int)
data class LifetimeAggregate(val declined: Long, val minutesSaved: Long)
```

Use Room-observed queries. `AchievementRepository` maps `LifetimeAggregate`; ViewModels collect mapped flows. Widget keeps event refresh but reads the same committed projection.

- [ ] **Step 4: Verify GREEN and commit**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.data.repository.StatsRepositoryTest' --tests 'com.dgraciano.breathe.data.repository.AggregateConsumersTest' && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`. Commit as `feat(data): observe committed reflection aggregates`.

---

### Task 7: Usage-Access-Safe Launcher Selection

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeViewModel.kt`
- Modify: `app/src/test/java/com/dgraciano/breathe/service/SessionTimeHelperTest.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/ui/appselect/AppSelectViewModelTest.kt`
- Modify: `app/src/test/java/com/dgraciano/breathe/ui/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes Task 3 `UsageAccessReader`.
- Produces launchable apps even when usage data is absent/denied.

- [ ] **Step 1: Write failing SecurityException test**

```kotlin
@Test fun `denied usage access still shows launchable apps alphabetically`() = runTest {
    coEvery { usage.usageByPackage(any(), any()) } throws SecurityException()
    every { launcherApps.load() } returns listOf(app("Beta"), app("Alpha"))
    viewModel.loadApps()
    assertEquals(listOf("Alpha", "Beta"), viewModel.uiState.value.apps.map { it.name })
    assertNull(viewModel.uiState.value.errorMessage)
}
```

- [ ] **Step 2: Verify RED**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.appselect.AppSelectViewModelTest' --tests 'com.dgraciano.breathe.ui.home.HomeViewModelTest' --tests 'com.dgraciano.breathe.service.SessionTimeHelperTest'`. Expected: picker error or missing state boundary.

- [ ] **Step 3: Implement enrichment-after-enumeration**

Load launcher apps first. Treat missing usage as empty/zero. Sort usage descending, then case-insensitive name, then package. Keep selection persistence independent.

- [ ] **Step 4: Verify GREEN and commit**

Run `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.appselect.AppSelectViewModelTest' --tests 'com.dgraciano.breathe.ui.home.HomeViewModelTest' --tests 'com.dgraciano.breathe.service.SessionTimeHelperTest' && ./gradlew :app:testDebugUnitTest`. Commit as `fix(appselect): work without usage access`.

---

### Task 8: Room Schema Export and Migration Paths

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt`
- Create: `app/schemas/com.dgraciano.breathe.data.db.BreatheDatabase/{1,2,3,4,5}.json`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/data/db/BreatheDatabaseMigrationTest.kt`
- Create if required for faithful history: `app/src/androidTest/assets/migrations/*.db`

**Interfaces:**
- Consumes historical DDL at `292988f`, `3070d80`, `cc1e6a8`, `f52b9e8`, `90f7e30`.
- Produces reproducible adjacent and 1/2/3/4→5 migration verification.

- [ ] **Step 1: Write migration tests and verify RED**

Seed each supported version with real rows. Run `./gradlew :app:compileDebugAndroidTestKotlin`; if connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.data.db.BreatheDatabaseMigrationTest`.

Expected: missing schemas/plugin validation.

- [ ] **Step 2: Enable export and reconstruct verified history**

Apply Room Gradle plugin, set `room { schemaDirectory("$projectDir/schemas") }`, and `exportSchema = true`. Generate v5. Reconstruct v1–v4 only from historical DDL and validate; use versioned SQLite fixtures when identity/schema JSON cannot be faithful.

- [ ] **Step 3: Verify GREEN**

Run `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin`; if connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.data.db.BreatheDatabaseMigrationTest`; then run `./gradlew :app:kspDebugKotlin && git diff --exit-code -- app/schemas`.

- [ ] **Step 4: Commit**

Commit as `test(data): verify supported Room migrations`.

---

### Task 9: Quiet Ocean Theme Tokens

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/theme/Theme.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/theme/Type.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/theme/Shape.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/theme/Spacing.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/test/java/com/dgraciano/breathe/ui/theme/QuietOceanContrastTest.kt`

**Interfaces:** Produces exact semantic tokens for Tasks 10–15.

- [ ] Write exact-value and ≥4.5:1 contrast tests.
- [ ] Verify RED with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.theme.QuietOceanContrastTest'`; expect missing semantic roles.
- [ ] Implement the exact colors, four type sizes, spacing, radii, elevation, Material mappings, and dark system chrome from the spec.
- [ ] Verify GREEN with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.theme.QuietOceanContrastTest' && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug`.
- [ ] Commit as `feat(ui): establish Quiet Ocean theme tokens`.

---

### Task 10: Observable Motion Policy

**Files:**
- Delete: `app/src/main/java/com/dgraciano/breathe/ui/components/ReducedMotion.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/components/MotionPolicy.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/components/WaveBackground.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/components/NimbusBuddy.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/components/ConfettiOverlay.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/BreatheApp.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/ui/components/MotionPolicyTest.kt`

**Interfaces:** Produces `LocalMotionPolicy` and Full/Reduced animation selection for Tasks 11–15.

- [ ] Write failing tests for animator-scale mapping, Reduced static/omitted decisions, and Full duration caps.
- [ ] Verify RED with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.components.MotionPolicyTest'`; expect missing policy.
- [ ] Implement lifecycle-aware `ContentObserver`, resume recheck, injectable policy, static waves/Nimbus, omitted Reduced confetti, and ≤220ms/snap navigation.
- [ ] Verify GREEN with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.components.MotionPolicyTest' && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:lintDebug`.
- [ ] Commit as `feat(ui): honor one observable motion policy`.

---

### Task 11: Shared Responsive and Semantic Compose Primitives

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/ui/components/BreatheScreen.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/components/QuietCard.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/components/SectionHeader.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/components/MetricGroup.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/components/SelectableControls.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/components/MonitoringStatusCard.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/ui/components/MonitoringHealthCopyTest.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/components/MonitoringStatusCardTest.kt`

**Interfaces:** Consumes Tasks 4, 9, 10; produces shared primitives for screen tasks.

- [ ] Write failing health-copy and status-card semantics tests.
- [ ] Verify RED with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.components.MonitoringHealthCopyTest' && ./gradlew :app:compileDebugAndroidTestKotlin`; expect absent primitives.
- [ ] Implement responsive metrics, selection roles/state descriptions, ≥48dp targets, insets/scroll conventions, and exact health mappings.
- [ ] Verify GREEN with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.components.MonitoringHealthCopyTest' && ./gradlew :app:compileDebugAndroidTestKotlin :app:compileDebugKotlin :app:lintDebug`; if connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.ui.components.MonitoringStatusCardTest`.
- [ ] Commit as `feat(ui): add Quiet Ocean Compose primitives`.

---

### Task 12: Onboarding and Home Content Roots

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingUiState.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingContent.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeScreen.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeUiState.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeContent.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/onboarding/OnboardingContentTest.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/home/HomeContentTest.kt`

**Interfaces:** Consumes Tasks 4, 6, 9–11; produces pure previewable setup/home roots.

- [ ] Add failing one-primary-card, exact-state-copy, semantics, compact, and 200%-font tests.
- [ ] Verify RED with `./gradlew :app:compileDebugAndroidTestKotlin`; expect content roots absent.
- [ ] Split routes from Android-free content, implement exact hierarchy/copy/scroll/reflow, and add required deterministic previews.
- [ ] Verify GREEN with `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:compileDebugKotlin :app:lintDebug`; if connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.ui.onboarding.OnboardingContentTest,com.dgraciano.breathe.ui.home.HomeContentTest`.
- [ ] Commit as `feat(ui): refine onboarding and home`.

---

### Task 13: App Picker Content Root

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectScreen.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectUiState.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/appselect/AppSelectContent.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/appselect/AppSelectContentTest.kt`

**Interfaces:** Consumes Tasks 7, 9–11; produces semantic previewable picker content.

- [ ] Write failing exact-copy, checkbox semantics, optional-usage, selection count, compact, and large-type tests.
- [ ] Verify RED with `./gradlew :app:compileDebugAndroidTestKotlin`; expect content root absent.
- [ ] Implement pure content with search/loading/error/empty/list states, semantic rows, deterministic fixtures, and required previews.
- [ ] Verify GREEN with `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:compileDebugKotlin :app:lintDebug`; if connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.ui.appselect.AppSelectContentTest`.
- [ ] Commit as `feat(ui): make app selection calm and accessible`.

---

### Task 14: Shared Pause Content and Retarget Safety

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseScreen.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseUiState.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseContent.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseOverlayHost.kt`
- Create: `app/src/test/java/com/dgraciano/breathe/ui/pause/PauseViewModelRetargetingTest.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/pause/PauseContentTest.kt`

**Interfaces:** Consumes Tasks 4–5 and 9–11; produces one pause content path for overlay/Activity. Task 5 owns adapter protocol; this task changes only state-to-content wiring and leaves request acknowledgement/Back contracts intact.

- [ ] Write delayed A→B retarget JVM test and failing FlowRow/announcement/copy/responsive/Reduced Compose tests.
- [ ] Verify RED with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.pause.PauseViewModelRetargetingTest' && ./gradlew :app:compileDebugAndroidTestKotlin`; expect stale result or absent types.
- [ ] Implement cancel-and-replace state, shared pure content, exact semantics/copy/scroll/actions, and required previews while preserving Task 5 adapter protocol.
- [ ] Verify GREEN with `./gradlew :app:testDebugUnitTest --tests 'com.dgraciano.breathe.ui.pause.PauseViewModelRetargetingTest' && ./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:compileDebugKotlin :app:lintDebug`; if connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.ui.pause.PauseContentTest`.
- [ ] Commit as `feat(ui): unify the mindful pause experience`.

---

### Task 15: Insights, Journey, Widget, and Preview Matrix

**Files:**
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/stats/StatsViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/stats/StatsScreen.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/stats/StatsContent.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/achievements/AchievementsViewModel.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/achievements/AchievementsScreen.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/achievements/AchievementsUiState.kt`
- Create: `app/src/main/java/com/dgraciano/breathe/ui/achievements/AchievementsContent.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/widget/PauseCountWidget.kt`
- Modify: `app/src/main/res/layout/widget_pause_count.xml`
- Modify: `app/src/main/res/drawable/widget_background.xml`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/QuietOceanResponsiveTest.kt`
- Create: `app/src/androidTest/java/com/dgraciano/breathe/ui/MotionPolicyTest.kt`

**Interfaces:** Consumes Tasks 6, 9–11; completes content/previews and reflection/widget surfaces.

- [ ] Write failing responsive, merged-metric, empty-state, preview-root, and Reduced tests.
- [ ] Verify RED with `./gradlew :app:compileDebugAndroidTestKotlin`; expect content roots/layout assertions absent.
- [ ] Implement pure reflection roots/previews, responsive metrics, unchanged achievement model, and token-aligned committed-data widget.
- [ ] Verify GREEN with `./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:compileDebugKotlin :app:lintDebug :app:assembleDebug`; if connected, run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.dgraciano.breathe.ui.QuietOceanResponsiveTest,com.dgraciano.breathe.ui.MotionPolicyTest`.
- [ ] Commit as `feat(ui): align reflection and widget with Quiet Ocean`.

---

### Task 16: Lint Ratchet, CI, and Release Manifest Policy

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/lint-baseline.xml`
- Create: `.github/workflows/android.yml`
- Create: `scripts/verify_release_manifest.py`
- Create: `scripts/tests/test_verify_release_manifest.py`
- Create: `scripts/tests/fixtures/valid-manifest.xml`

**Interfaces:**
- Produces no-new-warning lint enforcement, PR gates, and final merged-manifest invariants.

- [ ] **Step 1: Write failing namespace-correct policy tests**

```python
def test_rejects_query_all_packages():
    manifest = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">
      <uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
    </manifest>'''
    with self.assertRaisesRegex(PolicyError, "QUERY_ALL_PACKAGES"):
        verify_manifest_xml(manifest, valid_accessibility_config())
```

Also test allowed permissions, backup false, one non-exported service/binding permission, launcher query, window-state-only config, no content retrieval/gesture/accessibility-tool, and one CLI test against the fixture with a fake apkanalyzer executable.

- [ ] **Step 2: Verify RED then implement checker**

Run `python3 -m unittest scripts/tests/test_verify_release_manifest.py` before and after implementation.

- [ ] **Step 3: Configure lint ratchet**

Fix touched correctness/security/accessibility warnings. Create reviewed `app/lint-baseline.xml` for accepted legacy warnings. Configure baseline, `abortOnError = true`, `warningsAsErrors = true`, `checkReleaseBuilds = true`.

- [ ] **Step 4: Add CI**

Static job uses JDK 17/SDK 36 for JVM/lint/debug/release/bundle/Python/manifest/baseline gates. Instrumentation job boots API 35 Google APIs emulator and runs connected tests. Upload reports on failure.

- [ ] **Step 5: Verify and commit**

Run `python3 -m unittest scripts/tests/test_verify_release_manifest.py && ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease && git diff --exit-code -- app/lint-baseline.xml`. Commit as `build: enforce Android quality and manifest policy`.

---

### Task 17: Release, Privacy, and Device-Evidence Documentation

**Files:**
- Modify: `RELEASE.md`, `APP_AUDIT.md`, `README.md`, `PRIVACY.md`, `STORE_LISTING.md`, `docs/index.html`
- Create: `docs/release-evidence/README.md`

**Interfaces:**
- Produces truthful local/release instructions and the exact physical-device/Play evidence gate.

- [ ] **Step 1: Correct Accessibility and signing guidance**

Require Play Accessibility declaration, in-app disclosure/affirmative consent, and demo video matching the tested build. Separate unsigned PR CI from protected signed candidates; keep signing secrets out of source.

- [ ] **Step 2: Reconcile optional Usage Access/privacy copy**

State selection works without it and aggregate usage remains on-device/bounded for ordering and estimates. Retain the no-network claim only with Task 15 manifest enforcement.

- [ ] **Step 3: Add exact evidence template**

Include commit/version/signing digest, device/OEM/OS/operator/time, 10 normal launches, 10 leave/return visits, 10 system-surface interruptions, median/max latency, three repetitions of recovery cases, TalkBack/200%/landscape/reduced-motion/widget results, evidence links, and exact decision values from the spec.

- [ ] **Step 4: Verify and commit**

Run `rg -n 'device verified|Play approved|demo video|Usage Access|BLOCKED: DEVICE EVIDENCE' README.md RELEASE.md APP_AUDIT.md PRIVACY.md STORE_LISTING.md docs && git diff --check && ./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease`. Confirm no unsupported pass claim. Commit as `docs: define Quiet Ocean release evidence gates`.

---

## Final Verification, PR, and Merge

- [ ] Run the fresh automated gate:

```bash
export JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/usr/local/share/android-commandlinetools
./gradlew --no-daemon --stacktrace \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:bundleRelease
./gradlew :app:compileDebugAndroidTestKotlin
python3 -m unittest scripts/tests/test_verify_release_manifest.py
python3 scripts/verify_release_manifest.py \
  --apkanalyzer "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer" \
  --apk app/build/outputs/apk/release/app-release-unsigned.apk
git diff --exit-code -- app/lint-baseline.xml app/schemas
git diff --check
```

- [ ] If a configured emulator/device exists, run `./gradlew --no-daemon :app:connectedDebugAndroidTest`; otherwise report instrumentation/device evidence as blocked, never passed.
- [ ] Dispatch independent whole-branch architecture/code and UI-contract reviewers against the spec and plan.
- [ ] Fix every critical/important finding, run one scoped re-review, then repeat the fresh automated gate.
- [ ] Push `codex/quiet-ocean`, open a PR to `main`, wait for required checks, merge when green, and confirm the merge commit on `origin/main`.
- [ ] Leave release status `BLOCKED: DEVICE EVIDENCE` until supported Pixel/Samsung hardware and a signed candidate satisfy the physical matrix.
