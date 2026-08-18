# Task 4 Report: Monitoring Health, Presentation Protocol, and Coordinator

## Result

Implemented the process-wide intervention coordinator, closed monitoring-health model, and pause
presentation acknowledgement protocol. The coordinator consumes the landed Task 2 classifier
state/actions and delegates exact opened/declined decisions to the landed Task 3 sink.

## Changes

- Added closed `MonitoringHealth`, setup reasons, the six specified degraded reasons, and explicit
  recovery actions for deterministic UI mapping.
- Added coordinator-owned `PauseRequest` IDs plus the exact `PausePresentation` and
  `PausePresentationCallbacks` protocol.
- Added singleton `InterventionCoordinator`, which:
  - requires Accessibility, overlay permission, a connected service, and monitored-package
    readiness before publishing `Active`;
  - reconciles deferred observations through `ForegroundTransitionClassifier` and executes its
    actual `RequestPause` and `RevokeApproval` actions;
  - serializes one pending request and requires overlay or Activity content-ready acknowledgement
    within 2.5 seconds;
  - ignores late/stale acknowledgements after closing a request;
  - publishes closed degraded health for presentation loss, permission changes, service loss,
    monitored-package failure, and classifier failure;
  - offers exactly one retry with a fresh request ID, then routes recovery to setup review;
  - treats Back as in-memory visit approval without recording an outcome; and
  - creates exact Task 3 `PauseDecision` values for opened and declined outcomes.
- Changed `SessionApprovalStore` to hold one atomic current-visit approval, preventing an obsolete
  revoke from clearing a newer visit approval.
- Added 13 coordinator tests covering the required acknowledgement, timeout, late callback,
  presentation loss, permission/readiness, retry, Back, classifier reconciliation/revocation, and
  decision-delegation behavior.

## TDD evidence

The coordinator test was created before any Task 4 production type.

### Initial RED

```sh
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/usr/local/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.service.InterventionCoordinatorTest'
```

The unit-test Kotlin compilation failed as expected with unresolved `InterventionCoordinator`,
`MonitoringHealth`, `DegradedReason`, `PausePresentation`, `PausePresentationCallbacks`, and
`PauseRequest` symbols.

### Retry regression RED

After the core suite was green, added
`monitored package initialization retries once then routes to setup review` before changing the
initialization-recovery implementation. Its focused run failed at the recovery-action assertion,
showing a second failure incorrectly still offered `TryAgain`.

### GREEN

The focused coordinator suite then passed with all 13 tests.

## Final verification

Required focused Task 2/3/4 integration command:

```sh
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/usr/local/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.service.InterventionCoordinatorTest' \
  --tests 'com.dgraciano.breathe.service.ForegroundTransitionClassifierTest' \
  --tests 'com.dgraciano.breathe.data.repository.InterventionDecisionRepositoryTest'
```

Result: `BUILD SUCCESSFUL`, exit 0.

Full JVM suite:

```sh
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/usr/local/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`, exit 0. Parsed JUnit XML totals: 83 tests, 0 failures, 0 errors,
0 skipped. `git diff --check` also passed.

## Scope

Only the Task 4 production/test files and this required report were changed. Task 5 Android
service, overlay, Activity, ViewModel, and Hilt adapter wiring were not modified.
