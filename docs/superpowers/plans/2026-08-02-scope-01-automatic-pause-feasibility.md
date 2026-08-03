# Scope 1 Automatic-Pause Feasibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove whether a narrowly configured Android Accessibility Service can reliably present one Breathe moment when a user-selected app enters the foreground and can follow a credible Google Play policy path.

**Architecture:** Add a pure Kotlin foreground-session gate and coordinator, then adapt Android `TYPE_WINDOW_STATE_CHANGED` events through a minimal `AccessibilityService` that observes package identity only. Replace the spike build’s legacy polling-service entry path with explicit Accessibility access onboarding, validate the behavior on real devices, and finish with a written go/no-go decision. No production UI expansion begins until this gate passes.

**Tech Stack:** Kotlin 2.2.10, Android SDK 34, Jetpack Compose, Hilt, Room, JUnit 4, MockK, kotlinx-coroutines-test, Android Accessibility Service APIs.

## Global Constraints

- The public promise remains: “A calm space between impulse and action. Take one breath, then choose intentionally.”
- The service observes package/window transitions only; `canRetrieveWindowContent` and `canPerformGestures` remain `false`.
- Breathe is not declared as an accessibility tool; `android:isAccessibilityTool="false"` is explicit.
- Disclosure appears in-app immediately before the system Accessibility settings screen and affirmative consent is required.
- The pause always fails open. Android Back and the existing Continue path remain available.
- Selected packages and spike evidence remain local; no telemetry or account is introduced.
- The legacy foreground polling service must not run at the same time as the Accessibility spike.
- Scope 1 does not redesign the breathing UI, persistence model, Home, app picker, stats, or achievements.
- Scope 1 passes only when both device reliability and the Play policy/declaration path pass.
- If either gate fails, implementation stops before Scope 2 and the product promise is reconsidered.

---

## File Structure

### New production files

- `app/src/main/java/com/dgraciano/breathe/service/accessibility/ForegroundSessionGate.kt` — pure state machine that emits at most one request per external foreground session.
- `app/src/main/java/com/dgraciano/breathe/service/accessibility/AccessibilityPauseCoordinator.kt` — pure coordinator joining selected packages, session gating, and the launch callback.
- `app/src/main/java/com/dgraciano/breathe/service/accessibility/BreatheAccessibilityService.kt` — Android adapter for accessibility events and `PauseActivity` launch attempts.
- `app/src/main/java/com/dgraciano/breathe/service/accessibility/AccessibilityServiceState.kt` — checks whether Breathe’s service is enabled without embedding Settings parsing in UI code.
- `app/src/main/res/xml/breathe_accessibility_service.xml` — narrow, policy-minimized service metadata.

### New tests

- `app/src/test/java/com/dgraciano/breathe/service/accessibility/ForegroundSessionGateTest.kt`
- `app/src/test/java/com/dgraciano/breathe/service/accessibility/AccessibilityPauseCoordinatorTest.kt`
- `app/src/test/java/com/dgraciano/breathe/service/accessibility/AccessibilityServiceStateTest.kt`

### Modified files

- `app/src/main/AndroidManifest.xml` — register the Accessibility Service and unregister the legacy foreground monitor/boot path for this spike.
- `app/src/main/res/values/strings.xml` — add service label, description, and exact prominent disclosure copy.
- `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingViewModel.kt` — expose Accessibility Service enabled state.
- `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingScreen.kt` — present disclosure, affirmative consent, system settings navigation, and return-state refresh.
- `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt` — route by Accessibility Service readiness instead of Usage Access readiness.
- `app/src/main/java/com/dgraciano/breathe/ui/home/HomeViewModel.kt` — stop starting the legacy polling service.

### New decision artifact

- `docs/play/accessibility-service-declaration-draft.md` — exact policy declaration and review-video script.
- `docs/feasibility/accessibility-service-evaluation.md` — actual device observations and final go/no-go decision, written only after the matrix is run.

---

### Task 1: Foreground Session Gate

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/service/accessibility/ForegroundSessionGate.kt`
- Test: `app/src/test/java/com/dgraciano/breathe/service/accessibility/ForegroundSessionGateTest.kt`

**Interfaces:**
- Consumes: foreground package names and `Set<String>` of selected packages.
- Produces: `PauseRequest(sessionId: String, packageName: String)` from `onWindowStateChanged(packageName, selectedPackages)`.
- Invariant: an event from Breathe’s own package does not reset the external foreground session.

- [ ] **Step 1: Write the failing state-machine tests**

```kotlin
package com.dgraciano.breathe.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundSessionGateTest {
    private val selected = setOf("com.social.app")

    @Test
    fun `first selected package transition emits one request`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }

        assertEquals(
            PauseRequest("com.social.app:1000:1", "com.social.app"),
            gate.onWindowStateChanged("com.social.app", selected)
        )
    }

    @Test
    fun `repeated events in one foreground session do not emit again`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }
        gate.onWindowStateChanged("com.social.app", selected)

        assertNull(gate.onWindowStateChanged("com.social.app", selected))
        assertNull(gate.onWindowStateChanged("com.social.app", selected))
    }

    @Test
    fun `breathe window does not reset selected app session`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }
        gate.onWindowStateChanged("com.social.app", selected)

        assertNull(gate.onWindowStateChanged("com.dgraciano.breathe", selected))
        assertNull(gate.onWindowStateChanged("com.social.app", selected))
    }

    @Test
    fun `leaving for another app permits a later selected app session`() {
        var time = 1_000L
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { time }
        gate.onWindowStateChanged("com.social.app", selected)
        gate.onWindowStateChanged("com.android.launcher", selected)
        time = 2_000L

        assertEquals(
            PauseRequest("com.social.app:2000:2", "com.social.app"),
            gate.onWindowStateChanged("com.social.app", selected)
        )
    }

    @Test
    fun `blank and unselected packages do not emit`() {
        val gate = ForegroundSessionGate("com.dgraciano.breathe") { 1_000L }

        assertNull(gate.onWindowStateChanged(null, selected))
        assertNull(gate.onWindowStateChanged("", selected))
        assertNull(gate.onWindowStateChanged("com.mail.app", selected))
    }
}
```

- [ ] **Step 2: Run the targeted test and verify failure**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ForegroundSessionGateTest'
```

Expected: compilation fails because `ForegroundSessionGate` and `PauseRequest` do not exist.

- [ ] **Step 3: Implement the minimal pure state machine**

```kotlin
package com.dgraciano.breathe.service.accessibility

data class PauseRequest(
    val sessionId: String,
    val packageName: String
)

class ForegroundSessionGate(
    private val breathePackage: String,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private var currentExternalPackage: String? = null
    private var requestSequence: Long = 0

    fun onWindowStateChanged(
        packageName: String?,
        selectedPackages: Set<String>
    ): PauseRequest? {
        if (packageName.isNullOrBlank() || packageName == breathePackage) return null
        if (packageName == currentExternalPackage) return null

        currentExternalPackage = packageName
        if (packageName !in selectedPackages) return null

        requestSequence += 1
        return PauseRequest(
            sessionId = "$packageName:${nowMillis()}:$requestSequence",
            packageName = packageName
        )
    }
}
```

- [ ] **Step 4: Run the targeted test and verify success**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests '*ForegroundSessionGateTest'
```

Expected: all five tests pass.

- [ ] **Step 5: Commit the state machine**

```bash
git add app/src/main/java/com/dgraciano/breathe/service/accessibility/ForegroundSessionGate.kt app/src/test/java/com/dgraciano/breathe/service/accessibility/ForegroundSessionGateTest.kt
git commit -m "test: define accessibility foreground sessions"
```

---

### Task 2: Accessibility Pause Coordinator

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/service/accessibility/AccessibilityPauseCoordinator.kt`
- Test: `app/src/test/java/com/dgraciano/breathe/service/accessibility/AccessibilityPauseCoordinatorTest.kt`

**Interfaces:**
- Consumes: `ForegroundSessionGate`, `() -> Set<String>`, and `(PauseRequest) -> Unit`.
- Produces: `fun onWindowStateChanged(packageName: String?)`.
- Depends on: `PauseRequest` and `ForegroundSessionGate` from Task 1.

- [ ] **Step 1: Write the failing coordinator tests**

```kotlin
package com.dgraciano.breathe.service.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityPauseCoordinatorTest {
    @Test
    fun `selected transition invokes launch callback`() {
        val launches = mutableListOf<PauseRequest>()
        val coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate("com.dgraciano.breathe") { 42L },
            selectedPackages = { setOf("com.social.app") },
            launchPause = launches::add
        )

        coordinator.onWindowStateChanged("com.social.app")

        assertEquals(
            listOf(PauseRequest("com.social.app:42:1", "com.social.app")),
            launches
        )
    }

    @Test
    fun `unselected and duplicate transitions never launch`() {
        val launches = mutableListOf<PauseRequest>()
        val coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate("com.dgraciano.breathe") { 42L },
            selectedPackages = { setOf("com.social.app") },
            launchPause = launches::add
        )

        coordinator.onWindowStateChanged("com.mail.app")
        coordinator.onWindowStateChanged("com.social.app")
        coordinator.onWindowStateChanged("com.social.app")

        assertEquals(1, launches.size)
    }

    @Test
    fun `empty selected package snapshot never launches`() {
        val launches = mutableListOf<PauseRequest>()
        val coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate("com.dgraciano.breathe") { 42L },
            selectedPackages = { emptySet() },
            launchPause = launches::add
        )

        coordinator.onWindowStateChanged("com.social.app")

        assertTrue(launches.isEmpty())
    }
}
```

- [ ] **Step 2: Run the targeted test and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*AccessibilityPauseCoordinatorTest'
```

Expected: compilation fails because `AccessibilityPauseCoordinator` does not exist.

- [ ] **Step 3: Implement the coordinator**

```kotlin
package com.dgraciano.breathe.service.accessibility

class AccessibilityPauseCoordinator(
    private val gate: ForegroundSessionGate,
    private val selectedPackages: () -> Set<String>,
    private val launchPause: (PauseRequest) -> Unit
) {
    fun onWindowStateChanged(packageName: String?) {
        gate.onWindowStateChanged(packageName, selectedPackages())
            ?.let(launchPause)
    }
}
```

- [ ] **Step 4: Run both accessibility unit-test classes**

```bash
./gradlew :app:testDebugUnitTest --tests '*ForegroundSessionGateTest' --tests '*AccessibilityPauseCoordinatorTest'
```

Expected: all tests pass.

- [ ] **Step 5: Commit the coordinator**

```bash
git add app/src/main/java/com/dgraciano/breathe/service/accessibility/AccessibilityPauseCoordinator.kt app/src/test/java/com/dgraciano/breathe/service/accessibility/AccessibilityPauseCoordinatorTest.kt
git commit -m "feat: coordinate selected app pause requests"
```

---

### Task 3: Narrow Accessibility Service Adapter

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/service/accessibility/BreatheAccessibilityService.kt`
- Create: `app/src/main/res/xml/breathe_accessibility_service.xml`
- Modify: `app/src/main/AndroidManifest.xml:44-59`
- Modify: `app/src/main/res/values/strings.xml:1-26`

**Interfaces:**
- Consumes: `AppRepository.getBlockedApps(): Flow<List<BlockedApp>>` and `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED`.
- Produces: `PauseActivity` launch attempt for each emitted `PauseRequest`.
- Depends on: Tasks 1–2.

- [ ] **Step 1: Add the exact service strings**

Add to `strings.xml`:

```xml
<string name="accessibility_service_label">Breathe mindful app pauses</string>
<string name="accessibility_service_description">Notices when apps you selected enter the foreground so Breathe can offer one mindful pause. Breathe does not read screen content or control other apps.</string>
<string name="accessibility_disclosure_title">Allow Breathe to notice selected apps</string>
<string name="accessibility_disclosure_body">Breathe uses Android Accessibility access to notice the name of the app in the foreground when an app you selected opens, then show your Breathe moment. Breathe does not read screen content, tap buttons, or collect what you do inside apps. Your selected apps and pause activity stay on this device. You can turn access off at any time in Settings.</string>
<string name="accessibility_consent">I understand and want to enable this feature.</string>
<string name="open_accessibility_settings">Open Accessibility settings</string>
```

- [ ] **Step 2: Create the policy-minimized service configuration**

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="false"
    android:canRetrieveWindowContent="false"
    android:description="@string/accessibility_service_description"
    android:isAccessibilityTool="false"
    android:notificationTimeout="50" />
```

- [ ] **Step 3: Implement the Android adapter**

```kotlin
package com.dgraciano.breathe.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.dgraciano.breathe.data.repository.AppRepository
import com.dgraciano.breathe.ui.pause.PauseActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BreatheAccessibilityService : AccessibilityService() {
    @Inject lateinit var appRepository: AppRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val selectedPackages = AtomicReference<Set<String>>(emptySet())
    private lateinit var coordinator: AccessibilityPauseCoordinator

    override fun onServiceConnected() {
        super.onServiceConnected()
        coordinator = AccessibilityPauseCoordinator(
            gate = ForegroundSessionGate(packageName),
            selectedPackages = selectedPackages::get,
            launchPause = ::launchPause
        )
        scope.launch {
            appRepository.getBlockedApps()
                .map { apps -> apps.mapTo(mutableSetOf()) { it.packageName } }
                .distinctUntilChanged()
                .collect(selectedPackages::set)
        }
        Log.i(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        coordinator.onWindowStateChanged(event.packageName?.toString())
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        Log.i(TAG, "destroyed")
        super.onDestroy()
    }

    private fun launchPause(request: PauseRequest) {
        val intent = PauseActivity.newIntent(this, request.packageName).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        runCatching { startActivity(intent) }
            .onSuccess { Log.i(TAG, "launched session=${request.sessionId}") }
            .onFailure { error -> Log.e(TAG, "launch failed session=${request.sessionId}", error) }
    }

    private companion object {
        const val TAG = "BreatheA11y"
    }
}
```

- [ ] **Step 4: Register the service in the manifest**

Add inside `<application>`:

```xml
<service
    android:name=".service.accessibility.BreatheAccessibilityService"
    android:exported="true"
    android:label="@string/accessibility_service_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/breathe_accessibility_service" />
</service>
```

- [ ] **Step 5: Verify the manifest and resource compilation**

Run:

```bash
./gradlew :app:processDebugMainManifest :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`; the merged manifest contains `BreatheAccessibilityService`, `BIND_ACCESSIBILITY_SERVICE`, and the XML metadata reference.

- [ ] **Step 6: Commit the Android adapter**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml app/src/main/res/xml/breathe_accessibility_service.xml app/src/main/java/com/dgraciano/breathe/service/accessibility/BreatheAccessibilityService.kt
git commit -m "feat: add narrow accessibility pause service"
```

---

### Task 4: Accessibility Readiness and Prominent Consent

**Files:**
- Create: `app/src/main/java/com/dgraciano/breathe/service/accessibility/AccessibilityServiceState.kt`
- Test: `app/src/test/java/com/dgraciano/breathe/service/accessibility/AccessibilityServiceStateTest.kt`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingViewModel.kt:1-40`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingScreen.kt:27-190`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt:27-59`

**Interfaces:**
- Produces: `AccessibilityServiceState.isEnabled(context): Boolean` and `hasAccessibilityPermission: StateFlow<Boolean>`.
- Consumes: `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
- UI contract: settings navigation is disabled until the disclosure checkbox is selected.

- [ ] **Step 1: Write the failing enabled-services parser tests**

```kotlin
package com.dgraciano.breathe.service.accessibility

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityServiceStateTest {
    private val expected =
        "com.dgraciano.breathe/com.dgraciano.breathe.service.accessibility.BreatheAccessibilityService"

    @Test
    fun `finds exact service in colon separated setting`() {
        val enabled = "com.reader/.ReaderService:$expected:com.voice/.VoiceService"
        assertTrue(AccessibilityServiceState.containsService(enabled, expected))
    }

    @Test
    fun `matching is case insensitive but not partial`() {
        assertTrue(AccessibilityServiceState.containsService(expected.uppercase(), expected))
        assertFalse(AccessibilityServiceState.containsService("prefix$expected", expected))
    }

    @Test
    fun `null and blank settings are disabled`() {
        assertFalse(AccessibilityServiceState.containsService(null, expected))
        assertFalse(AccessibilityServiceState.containsService("", expected))
    }
}
```

- [ ] **Step 2: Run the test and verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests '*AccessibilityServiceStateTest'
```

Expected: compilation fails because `AccessibilityServiceState` does not exist.

- [ ] **Step 3: Implement the state checker**

```kotlin
package com.dgraciano.breathe.service.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object AccessibilityServiceState {
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context,
            BreatheAccessibilityService::class.java
        ).flattenToString()
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return containsService(enabled, expected)
    }

    internal fun containsService(enabled: String?, expected: String): Boolean =
        enabled
            ?.split(':')
            ?.any { component -> component.equals(expected, ignoreCase = true) }
            ?: false
}
```

- [ ] **Step 4: Replace the onboarding ViewModel permission state**

Use this complete ViewModel body:

```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _hasAccessibilityPermission = MutableStateFlow(false)
    val hasAccessibilityPermission: StateFlow<Boolean> = _hasAccessibilityPermission

    init {
        refreshPermissionState()
    }

    fun refreshPermissionState() {
        _hasAccessibilityPermission.value = AccessibilityServiceState.isEnabled(context)
    }
}
```

Remove the `AppOpsManager`, `Process`, and Usage Access checks from this class.

- [ ] **Step 5: Replace the onboarding permission card with prominent disclosure and consent**

At the top of `OnboardingScreen`, collect `hasAccessibilityPermission`, retain the lifecycle refresh, and add:

```kotlin
var disclosureAccepted by rememberSaveable { mutableStateOf(false) }

LaunchedEffect(hasAccessibilityPermission) {
    if (hasAccessibilityPermission) onPermissionsGranted()
}
```

The permission card content must be exactly:

```kotlin
Text(
    text = stringResource(R.string.accessibility_disclosure_title),
    style = MaterialTheme.typography.titleMedium,
    color = BreatheTextPrimary
)
Spacer(Modifier.height(8.dp))
Text(
    text = stringResource(R.string.accessibility_disclosure_body),
    style = MaterialTheme.typography.bodyMedium,
    color = BreatheTextSecondary
)
Row(
    modifier = Modifier
        .fillMaxWidth()
        .toggleable(
            value = disclosureAccepted,
            onValueChange = { disclosureAccepted = it }
        )
        .padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Checkbox(
        checked = disclosureAccepted,
        onCheckedChange = null
    )
    Spacer(Modifier.width(8.dp))
    Text(stringResource(R.string.accessibility_consent))
}
Button(
    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
    enabled = disclosureAccepted && !hasAccessibilityPermission,
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)
) {
    Text(
        if (hasAccessibilityPermission) stringResource(R.string.granted)
        else stringResource(R.string.open_accessibility_settings)
    )
}
```

Import `rememberSaveable`, `stringResource`, `toggleable`, and `heightIn`. The checkbox plus button click is the affirmative consent; the app must not navigate to Settings before it.

- [ ] **Step 6: Route by Accessibility readiness**

In `BreatheNavGraph`, replace `hasUsage` collection and start-destination selection with:

```kotlin
val hasAccessibility by onboardingViewModel.hasAccessibilityPermission.collectAsState()
val startDest = if (hasAccessibility) Routes.HOME else Routes.ONBOARDING
```

- [ ] **Step 7: Run permission-state tests and compile the UI**

```bash
./gradlew :app:testDebugUnitTest --tests '*AccessibilityServiceStateTest' :app:compileDebugKotlin
```

Expected: parser tests pass and Compose compilation succeeds.

- [ ] **Step 8: Commit readiness and consent**

```bash
git add app/src/main/java/com/dgraciano/breathe/service/accessibility/AccessibilityServiceState.kt app/src/test/java/com/dgraciano/breathe/service/accessibility/AccessibilityServiceStateTest.kt app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingViewModel.kt app/src/main/java/com/dgraciano/breathe/ui/onboarding/OnboardingScreen.kt app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt
git commit -m "feat: disclose and request accessibility access"
```

---

### Task 5: Isolate the Spike From Legacy Polling

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:4-17,44-59`
- Modify: `app/src/main/java/com/dgraciano/breathe/ui/home/HomeViewModel.kt:1-70`
- Preserve but do not execute: `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt`
- Preserve but do not execute: `app/src/main/java/com/dgraciano/breathe/service/BootReceiver.kt`

**Interfaces:**
- Removes runtime entry to `AppMonitorService.start(context)`.
- Leaves the legacy implementation available for comparison without allowing both mechanisms to race.

- [ ] **Step 1: Remove legacy runtime startup from HomeViewModel**

Delete:

```kotlin
import com.dgraciano.breathe.service.AppMonitorService
```

Delete `startService()` from the `init` block and delete:

```kotlin
fun startService() = AppMonitorService.start(context)
```

Keep existing stats and blocked-app loading behavior unchanged in this scope.

- [ ] **Step 2: Unregister the legacy service and boot receiver**

Delete the `AppMonitorService` `<service>` and `BootReceiver` `<receiver>` blocks from the manifest. Remove these permissions because nothing in the spike build uses them:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

Keep `PACKAGE_USAGE_STATS` for the unchanged Home usage summaries during Scope 1; its removal belongs to the later privacy/release scope.

- [ ] **Step 3: Assert that only the accessibility mechanism is registered**

Run:

```bash
./gradlew :app:processDebugMainManifest
rg -n "BreatheAccessibilityService|AppMonitorService|BootReceiver" app/build/intermediates/merged_manifests/debug/processDebugMainManifest/AndroidManifest.xml
```

Expected: one `BreatheAccessibilityService` match; no `AppMonitorService` or `BootReceiver` matches.

- [ ] **Step 4: Run targeted tests and build the debug APK**

```bash
./gradlew :app:testDebugUnitTest --tests '*service.accessibility*' :app:assembleDebug
```

Expected: targeted tests pass and `app/build/outputs/apk/debug/app-debug.apk` is created.

- [ ] **Step 5: Commit legacy isolation**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/java/com/dgraciano/breathe/ui/home/HomeViewModel.kt
git commit -m "refactor: isolate accessibility feasibility spike"
```

---

### Task 6: Google Play Declaration Draft

**Files:**
- Create: `docs/play/accessibility-service-declaration-draft.md`

**Interfaces:**
- Consumes: the exact service metadata and disclosure implemented in Tasks 3–4.
- Produces: submission-ready declaration text and a deterministic review-video script.

- [ ] **Step 1: Write the declaration with this exact substance**

```markdown
# Breathe Accessibility Service Declaration Draft

## Classification

Breathe is not an accessibility tool. Its service metadata sets `isAccessibilityTool=false`.

## Core feature requiring the API

Breathe lets a user select distracting apps. When one of those selected apps enters the foreground, Breathe observes the package-level window-change event and presents one mindful breathing pause before the user chooses whether to return or continue. Android does not provide a narrower event API that can deliver this user-requested, app-specific foreground transition with equivalent timing.

## Data accessed

- Package identity from `TYPE_WINDOW_STATE_CHANGED` events.
- Only package identity is read; window content is not retrieved.
- The service cannot perform gestures and does not tap, scroll, type, or change settings.
- Selected package identifiers and Breathe outcomes are stored locally on the device.
- Breathe does not sell or share this information.

## User control

- The app presents a prominent disclosure immediately before opening Android Accessibility settings.
- The user must select “I understand and want to enable this feature” before the settings button is enabled.
- The system settings screen requires the user to enable the service.
- The user can disable the service at any time in Android Settings.
- Android Back, Skip, and Continue remain available in every Breathe moment.

## Review video sequence

1. Launch Breathe from a fresh install.
2. Show the complete prominent disclosure without cutting away.
3. Select the affirmative-consent checkbox.
4. Tap “Open Accessibility settings.”
5. Enable “Breathe mindful app pauses” in the Android system UI.
6. Return to Breathe and select one test social app.
7. Open the selected app and show the automatic Breathe moment.
8. Tap Continue and show that the selected app opens.
9. Leave, reopen the selected app, and tap Not right now.
10. Return to Android Accessibility settings and disable Breathe.
11. Reopen the selected app and show that no Breathe moment appears.
```

- [ ] **Step 2: Cross-check the document against implementation**

Run:

```bash
rg -n "isAccessibilityTool|canRetrieveWindowContent|canPerformGestures|typeWindowStateChanged" app/src/main/res/xml/breathe_accessibility_service.xml
rg -n "accessibility_disclosure_body|accessibility_consent" app/src/main/res/values/strings.xml
```

Expected: the XML values and disclosure strings match every declaration claim.

- [ ] **Step 3: Commit the declaration draft**

```bash
git add docs/play/accessibility-service-declaration-draft.md
git commit -m "docs: draft accessibility service declaration"
```

---

### Task 7: Device Matrix and Go/No-Go Decision

**Files:**
- Create after testing: `docs/feasibility/accessibility-service-evaluation.md`

**Interfaces:**
- Consumes: debug APK, `BreatheA11y` log output, Pixel/Samsung observations, and the policy draft.
- Produces: exactly one final decision: `GO`, `NO-GO: RELIABILITY`, or `NO-GO: POLICY`.

- [ ] **Step 1: Run the automated preflight**

```bash
./gradlew :app:testDebugUnitTest --tests '*service.accessibility*' :app:lintDebug :app:assembleDebug
```

Expected: all new targeted tests, lint, and debug assembly pass. Existing unrelated stale unit tests are documented but are not silently altered in this feasibility scope.

- [ ] **Step 2: Install and verify service discovery on each test device**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings get secure enabled_accessibility_services
adb logcat -c
adb logcat -s BreatheA11y ActivityTaskManager AndroidRuntime
```

The user must enable the service through the Android Settings UI; do not enable it programmatically through ADB or hidden APIs.

- [ ] **Step 3: Execute the required reliability scenarios**

Run every scenario on a Pixel and Samsung device across Android 10, Android 12 or 13, Android 14, and the current Android release available to the test team:

1. First enable after onboarding.
2. Selected app opened from launcher.
3. Selected app reopened after returning Home.
4. Continue to selected app.
5. Not right now to Home.
6. Ten rapid window-change events in the selected app.
7. Rapid switching between two selected apps.
8. Breathe process killed, then selected app opened.
9. Device reboot, then selected app opened.
10. Accessibility access revoked while Breathe is backgrounded.
11. Screen off/on and unlock into a selected app.
12. Battery saver and Doze.
13. TalkBack enabled alongside Breathe.
14. Selected app removed from Breathe.

For each scenario, record Android version/build, device model, time from selected app becoming foreground to visible Breathe moment, number of Breathe moments shown, whether Continue opens exactly once, and relevant `BreatheA11y`/system log lines.

- [ ] **Step 4: Apply the pass thresholds**

The reliability gate passes only when:

- Every normal selected-app launch produces one visible Breathe moment on every required device/OS combination.
- No foreground session produces more than one Breathe moment.
- Continue opens the selected app exactly once and does not loop.
- Not right now exits safely.
- Process death, reboot, permission revocation, and screen unlock have explicit and repeatable behavior.
- Median presentation delay is at most 1 second and no observed normal launch exceeds 2 seconds.
- Breathe coexists with TalkBack without suppressing or corrupting TalkBack behavior.
- No window content, gestures, hidden API, programmatic permission grant, overlay, or full-screen notification is added to make a failure disappear.

The policy gate passes only when:

- Implemented behavior matches the prominent disclosure word for word.
- `isAccessibilityTool=false` remains accurate.
- The Play declaration answers and review-video sequence can demonstrate the full behavior.
- No policy claim depends on reading screen content or autonomous interaction.
- The product owner accepts that Play approval is required and not guaranteed.

- [ ] **Step 5: Write the evidence-based evaluation**

Create `docs/feasibility/accessibility-service-evaluation.md` with:

```markdown
# Accessibility Service Feasibility Evaluation

## Decision

Use exactly one: `GO`, `NO-GO: RELIABILITY`, or `NO-GO: POLICY`.

## Automated Preflight

Record the commands, exit codes, and failing test names if any.

## Device Matrix Results

Provide one row per device/OS/scenario with measured delay, presentation count, navigation result, and evidence reference.

## Reliability Gate

Mark every threshold PASS or FAIL and explain each failure with logs or reproduction steps.

## Policy Gate

Mark every declaration/disclosure requirement PASS or FAIL and link the exact implementation evidence.

## Decision Rationale

Explain why the evidence requires the selected decision.

## Next Authorized Scope

- For `GO`: Scope 2 Core Reliability.
- For `NO-GO: RELIABILITY`: stop and evaluate notification-driven product reframing.
- For `NO-GO: POLICY`: stop and evaluate distribution/product alternatives.
```

Replace the instructional sentences with actual evidence while writing the report; do not commit an unfilled template.

- [ ] **Step 6: Review the decision before committing**

```bash
rg -n "GO|NO-GO|PASS|FAIL|Pixel|Samsung|Android" docs/feasibility/accessibility-service-evaluation.md
git diff --check
```

Expected: the report contains one decision, explicit threshold results, both device families, the tested Android versions, and no whitespace errors.

- [ ] **Step 7: Commit the evaluation**

```bash
git add docs/feasibility/accessibility-service-evaluation.md
git commit -m "docs: decide automatic pause feasibility"
```

---

## Scope 1 Completion Gate

Scope 1 is complete only when:

- All three new JVM test classes pass.
- Debug manifest, Kotlin compilation, lint, and debug assembly pass.
- The legacy polling service and Accessibility Service never run together.
- The prominent disclosure, consent UI, XML metadata, and declaration draft agree.
- The required Pixel/Samsung/Android device matrix is recorded with evidence.
- `docs/feasibility/accessibility-service-evaluation.md` contains one defensible decision.

Do not create or execute the Scope 2 implementation plan until this gate produces `GO`. The architecture, tests, and product copy for every later scope depend on the result.

## Execution Handoff

After this plan is approved, execute it in an isolated worktree created through `superpowers:using-git-worktrees`.

Two supported execution modes:

1. **Subagent-Driven (recommended):** use `superpowers:subagent-driven-development`, dispatch a fresh worker per task, and review specification compliance and code quality between tasks.
2. **Inline Execution:** use `superpowers:executing-plans`, implement in small batches, and stop at review checkpoints.
