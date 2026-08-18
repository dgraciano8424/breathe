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
