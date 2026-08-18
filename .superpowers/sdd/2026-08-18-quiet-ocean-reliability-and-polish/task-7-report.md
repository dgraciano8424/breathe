# Task 7 Report: Usage-Access-Safe Launcher Selection

## Result

Launchable-app enumeration now completes independently of optional Usage Access. The picker lists
and selects launcher apps even when usage lookup is denied; those apps simply receive zero usage.
Home reads the same optional `UsageAccessReader` boundary, so its estimates remain available when
permission is granted and safely become zero on denial.

## Changes

- Added an injected `LauncherApps` boundary that enumerates and de-duplicates launchable activities
  before any optional usage lookup.
- Changed `AppSelectViewModel` to inject `UsageAccessReader` and the configured IO dispatcher.
  `SecurityException` from enrichment maps to an empty usage map rather than a picker error.
- Made picker order deterministic: foreground usage descending, then case-insensitive app name,
  then package name.
- Kept block/unblock persistence independent of usage enrichment and moved it onto the injected
  dispatcher.
- Changed `HomeViewModel` to use `UsageAccessReader`, preserving its seven-day minute estimates
  and its zero-minute fallback on `SecurityException`.
- Updated Home and aggregate-consumer test fixtures for the new interface; added focused picker
  tests for denied access and deterministic ordering.

## TDD evidence

### RED

`AppSelectViewModelTest` was added before implementation. The required focused command failed at
Kotlin test compilation because `LauncherApps`, `LaunchableApp`, and the injected picker
constructor boundary did not yet exist. This is the expected missing-state-boundary failure.

### GREEN

After introducing launcher enumeration and optional enrichment, the focused suite passed. It
exercises a real ViewModel with only Android/usage boundaries mocked: a thrown
`SecurityException` still yields `Alpha`, `Beta`, no picker error, and an equal-usage fixture is
ordered by name then package.

## Verification

With:

```sh
JAVA_HOME=/usr/local/Cellar/openjdk@17/17.0.20/libexec/openjdk.jdk/Contents/Home
ANDROID_HOME=/usr/local/share/android-commandlinetools
```

the focused suite passed:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.ui.appselect.AppSelectViewModelTest' \
  --tests 'com.dgraciano.breathe.ui.home.HomeViewModelTest' \
  --tests 'com.dgraciano.breathe.service.SessionTimeHelperTest'
```

The requested full JVM and production Kotlin compile also passed:

```sh
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```

`git diff --check` passed. Build output includes pre-existing Gradle/Android deprecation warnings,
but both verification commands completed `BUILD SUCCESSFUL`.

## Scope

Only Task 7 launcher/usage behavior, the directly affected Home and aggregate-consumer fixtures,
and this report were changed. No Task 13 picker visual/content redesign was included.

## Fix round 1: stale picker loads

### Corrections

- `AppSelectViewModel` now owns one picker-load `Job`. Starting a new reload cancels the prior
  request and advances a generation token.
- Every async state write (rows, ordinary error, and loading completion) is generation-gated, so a
  non-cooperative obsolete request cannot overwrite a later result or hide its loading state.
- `CancellationException` is rethrown before ordinary error handling, preventing cancellation from
  showing the picker retry message.

### Fix TDD evidence

Before changing production code, three delayed-fake ViewModel tests failed against the initial Task
7 implementation: an old request completing after B replaced B's rows, its `finally` cleared B's
loading state while B was pending, and cancellation published the ordinary picker error. The fakes
model package-manager work that may finish after cancellation, which is why cancellation alone is
not sufficient without the generation guard.

After the owned-job and guarded-write implementation, the picker suite passed. The tests would fail
again if any success, error, or `finally` guard were removed, or if cancellation were caught as an
ordinary exception.

### Fix verification

Both commands completed `BUILD SUCCESSFUL` with the Java/Android paths documented above:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.ui.appselect.AppSelectViewModelTest' \
  --tests 'com.dgraciano.breathe.ui.home.HomeViewModelTest' \
  --tests 'com.dgraciano.breathe.service.SessionTimeHelperTest'

./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```
