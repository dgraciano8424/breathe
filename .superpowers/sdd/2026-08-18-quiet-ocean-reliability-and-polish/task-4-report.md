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

## Fix round 1

Addressed all coordinator review findings in a separate TDD fix round.

### Behavioral corrections

- Terminal opened/declined callbacks atomically claim a request, then wait for
  `PauseDecisionSink.record` to return success before approval, dismissal, or publishing ready
  health. Cancellation remains cooperative and propagates through the returned coroutine `Job`.
- A failed durable write keeps the presentation open, does not approve the visit, retains the
  diagnostic cause, and rejects duplicate/stale terminal callbacks. It maps to
  `ClassifierFailure` + `ReviewSetup`: the phase's closed health table explicitly assigns unknown
  technical failures to `ClassifierFailure`, and automatic persistence retry would risk a
  duplicate commit when the repository cannot prove whether storage completed.
- `OverlayUnavailable` now maps exactly to `ReviewDisplayPermission`. Presentation loss closes the
  request and suppresses another presentation for that visit; `retry()` never directly invokes
  `PausePresentation.show`.
- Pause presentation is gated on all four prerequisites: Accessibility, overlay permission,
  service connection, and monitored-package readiness. A reducer `RequestPause` is retained as a
  coordinator-level deferred request until every prerequisite is true.
- Degraded health is reason-aware and sticky. Permission/service/package updates clear only their
  corresponding conditions; `PresentationNotConfirmed` and `ClassifierFailure` survive unrelated
  ready notifications.
- Idle permission loss publishes `SetupRequired`; only permission loss while a presentation is
  active/pending publishes `PermissionChanged`.
- `TryAgain` resets classifier state, approvals, timestamps, deferred work, and retry state, then
  emits a uniquely identified pure `ReinitializeMonitoring` request for the Task 5 adapter.
- Reconciliation uses the retained first observation's timestamp, and monitored package input is
  defensively copied before entering classifier state.
- A testable diagnostic state retains package-load, overlay, classifier, and persistence causes
  without external analytics or user-facing exception text.
- Stale acknowledgement and simultaneous opened/declined races are explicitly covered.

### Fix RED evidence

Each production correction followed a focused failing test:

1. Persistence-ordering tests initially failed because the request was dismissed and opened visits
   were approved before the sink completed; failure/cancellation tests also exposed the missing
   diagnostic and returned-job APIs.
2. Readiness/health tests produced six expected failures: missing Accessibility, overlay, and
   service prerequisites still presented; idle permission loss reported `PermissionChanged`;
   timeout degradation was erased; and overlay loss advertised/replayed an immediate retry.
3. Recovery/diagnostic tests initially failed to compile because classifier-failure and recovery
   boundaries did not exist. After those APIs landed, the focused suite retained two expected
   failures proving that deferred requests used the later timestamp and mutable package input
   leaked into classifier state.
4. The distinct-recovery-cycle test then failed because repeated recoveries reused one enum-valued
   state and did not emit a new observable request.

### Fix GREEN and final verification

The coordinator suite passes all 29 tests. The required combined suite passed:

```sh
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/usr/local/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.service.InterventionCoordinatorTest' \
  --tests 'com.dgraciano.breathe.service.ForegroundTransitionClassifierTest' \
  --tests 'com.dgraciano.breathe.data.repository.InterventionDecisionRepositoryTest'
```

Result: `BUILD SUCCESSFUL`, exit 0.

The full JVM suite also passed:

```sh
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/usr/local/share/android-commandlinetools \
./gradlew :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`, exit 0. Parsed JUnit XML totals: 99 tests, 0 failures, 0 errors,
0 skipped. `git diff --check` passed.
