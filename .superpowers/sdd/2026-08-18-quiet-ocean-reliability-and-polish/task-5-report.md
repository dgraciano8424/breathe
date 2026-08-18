# Task 5 Report: Android Service, Presentation Adapters, and Hilt Wiring

## Result

Wired the existing root-package accessibility service, overlay host, fallback Activity, and pause
ViewModel through the Task 4 singleton coordinator. Android permission/service truth and framework
foreground facts now enter the coordinator without service-owned visit or presentation state.

## Changes

- Added an exact enabled-service parser that compares the application package and fully expanded
  service class, rejecting package prefixes, similarly named services, and wrong namespaces.
- Reduced `BreatheAccessibilityService` to an Android adapter that:
  - reports exact Accessibility and overlay permission truth;
  - reports service connection/disconnection;
  - normalizes package, component, IME, System UI, Settings, and launcher facts through Task 2;
  - sends normalized observations to the Task 4 coordinator; and
  - reports initial Room package readiness/failure and restarts collection for a unique coordinator
    recovery request.
- Bound `PauseOverlayHost` to `PausePresentation`, `InterventionDecisionRepository` to
  `PauseDecisionSink`, and the landed `SessionTimeHelper` implementation to Task 3's
  `UsageAccessReader` boundary in the singleton Hilt component.
- Made the overlay implement the request-ID protocol: successful window add acknowledges the
  request, window/add/launch loss reports the same ID, fallback carries that ID, and coordinator
  dismissal targets only that presentation.
- Made `PauseActivity` acknowledge exactly once per request only after both `onResume` and Compose
  content readiness, report unexpected destruction as presentation loss, and route Back through
  coordinator dismissal.
- Routed production pause decisions from both Activity and overlay ViewModels to the same singleton
  coordinator while retaining the existing constructor used by the pre-existing isolated JVM tests.
- Updated onboarding Accessibility truth to use the exact-component query.

## TDD evidence

The two Task 5 test files were added before production adapters.

### RED

The focused JVM command failed at test compilation with three unresolved
`AccessibilityServiceState` references. Android-test compilation failed in Hilt processing because
the requested `PausePresentationModule` and Activity acknowledgement contract did not exist.

### GREEN

The focused parser suite passed after the exact-component adapter was implemented. The first full
JVM run exposed five existing Home regressions because the Android `ComponentName` was constructed
before an absent setting was rejected; the helper was reordered and the full suite then passed.

## Verification

With:

```sh
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
ANDROID_HOME=/usr/local/share/android-commandlinetools
```

the required combined command passed:

```sh
./gradlew :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:compileDebugKotlin
```

Result: `BUILD SUCCESSFUL`; 106 JVM tests, 0 failures, 0 errors. The focused
`AccessibilityServiceStateTest` also passed independently. `git diff --check` passed.

The merged debug manifest contains exactly one `BreatheAccessibilityService` declaration at the
fully qualified root-package service class. The configured SDK's `adb devices` returned no attached
devices, so `PauseActivityAcknowledgementTest` compiled but connected execution was unavailable.

## Scope

Only Task 5's declared Android adapter, presentation, ViewModel, Hilt, and test files plus this
required report were changed. No later UI/data task was implemented.
