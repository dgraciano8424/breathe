# Task 6 Report: Reactive Aggregate Projections and Consumers

## Result

Implemented Room-observed committed aggregate projections for today's intervention totals and
lifetime decline progress. Home, Insights, Journey, and widget reads now consume those projections,
so both the initial decision insert and later minutes-saved enrichment are reflected from committed
database state.

## Changes

- Added `TodayAggregate` and `LifetimeAggregate` projection models.
- Added Room-observed DAO queries over `intervention_events`:
  - today's attempts, declines, and declined-event minutes saved; and
  - lifetime declines and declined-event minutes saved.
- Exposed both projections as `Flow` APIs from `StatsRepository`.
- Changed `AchievementRepository` to map `LifetimeAggregate` emissions into `UserProgress` instead
  of issuing two independent snapshot DAO reads.
- Changed Home and Insights ViewModels to collect the same today projection. Insights refreshes its
  weekly/streak/top-app companion values on every committed today emission and prevents duplicate
  long-lived collectors when its existing screen load hook runs.
- Changed Journey to keep collecting mapped lifetime progress after its existing `load()` entry
  point.
- Changed the widget redraw path to read one `TodayAggregate` emission while preserving the
  existing insert/enrichment refresh broadcasts from `InterventionDecisionRepository`.
- Updated the existing Home test fixture to provide the new flows.

## TDD evidence

### RED

The repository and aggregate-consumer tests were written before production changes. The required
focused command failed during Kotlin test compilation with unresolved `TodayAggregate`,
`LifetimeAggregate`, `observeTodayAggregate`, `observeLifetimeAggregate`, and
`observeUserProgress` symbols, plus the old `AchievementRepository` constructor contract.

After the initial implementation, a separate duplicate-collector regression test failed with two
active Insights subscriptions after the screen requested its existing second load.

### GREEN

The focused repository/consumer suite passed after adding the Room projections, mapping consumers,
and guarding the long-lived Insights collector.

## Verification

With:

```sh
JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
ANDROID_HOME=/usr/local/share/android-commandlinetools
```

the required focused command passed:

```sh
./gradlew :app:testDebugUnitTest \
  --tests 'com.dgraciano.breathe.data.repository.StatsRepositoryTest' \
  --tests 'com.dgraciano.breathe.data.repository.AggregateConsumersTest'
```

The required full JVM and compile command also passed:

```sh
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```

Result: `BUILD SUCCESSFUL`; 115 JVM tests, 0 failures, 0 errors, 0 skipped. Room/KSP accepted both
aggregate SQL projections, and `git diff --check` passed.

## Scope

Only Task 6 aggregate DAO/repository/consumer files, its declared tests, the existing Home test
fixture required by the repository API migration, and this report were changed. No later visual
redesign work was included.

## Fix round 1

Addressed all aggregate reliability review findings in a separate TDD round.

### Corrections

- `StatsRepository` now derives local-day and Monday boundaries from injectable `Clock` and
  `ZoneId` inputs. Its today flow starts with the current boundary, receives injectable/testable
  local-midnight ticks, and uses `flatMapLatest` to replace the old Room query with a fresh one at
  rollover. Home and Insights therefore reset at midnight without requiring another database
  write; a new widget read starts from that same current boundary.
- Every timestamp-window DAO query now uses inclusive `>=`, so an event committed exactly at local
  midnight or Monday midnight is counted.
- Repeated Journey `load()` calls now share one active lifetime-progress collector rather than
  accumulating permanent Room subscriptions.
- Added two schema indexes selected for the actual query shapes:
  `timestamp` supports today/weekly windows and recency ordering, while `(outcome, timestamp)`
  supports declined-window and lifetime-declined filtering.
- Advanced `BreatheDatabase` from version 5 to version 6 and registered additive
  `MIGRATION_5_6`, which creates only those two indexes. **Task 8 must extend from schema version 6
  and must not recreate or duplicate this migration.**
- Added an Android Room integration suite that continuously observes both today and lifetime
  projections across an exact-midnight insert and a later `minutesSaved` update. It also verifies
  every inclusive window query, the current entity schema indexes, and the real 5→6 migration SQL.
- Widget update execution now explicitly rethrows `CancellationException`, while logging ordinary
  read/render exceptions and still finishing the broadcast's pending result.
- Added an `IoDispatcher` Hilt binding and injected it into `HomeViewModel`; JVM tests supply their
  controlled dispatcher, eliminating the former real-`Dispatchers.IO` race.

### Fix RED evidence

The focused unit command initially failed at test compilation because the injectable rollover
constructor, `MIGRATION_5_6`, controlled Home usage dispatcher, and widget update boundary did not
exist. After those were implemented, the repeated-Journey-load test would have observed two active
subscriptions until the ViewModel gained its guarded collector.

The real Room test was authored before the inclusive SQL and index schema changes. Connected RED
execution was unavailable because the configured SDK's `adb devices` reported no attached device;
its mandatory Android-test Kotlin compilation is green and the test is ready for device/CI
execution.

### Fix verification

The focused JVM suite passed for `StatsRepositoryTest`, `AggregateConsumersTest`, and
`PauseCountWidgetTest`. The full JVM suite was then forced to rerun three times; every run completed
`BUILD SUCCESSFUL`. The latest parsed result contains 120 tests, 0 failures, 0 errors, and 0
skipped.

The combined production and Android-test compilation passed:

```sh
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

After strengthening the inclusive-boundary integration assertions, a fresh
`:app:compileDebugAndroidTestKotlin` also completed `BUILD SUCCESSFUL`. `git diff --check` passed.
