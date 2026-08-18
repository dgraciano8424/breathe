# Breathe Quiet Ocean Reliability and Polish Design

**Date:** 2026-08-18

**Status:** Implementation specification ready for final user review

**Baseline:** `origin/main` at `6839d73`

**Target branch:** `codex/quiet-ocean`

## Purpose

Breathe should feel like a calm, dependable pause between impulse and action. This phase makes the existing Android app reliable enough to trust, coherent enough to feel intentionally designed, and straightforward to verify in Android Studio.

The work strengthens the current Accessibility Service and overlay architecture rather than merging the obsolete `codex/breathe-scope-01` experiment. It preserves the existing offline-first model, Nimbus identity, insights, journey, per-app pause duration, and home-screen widget while making the core pause journey quieter and more prominent than gamification.

## Product promise

> A calm space between impulse and action. Take one breath, then choose intentionally.

Every change in this phase must reinforce that promise. Reliability, compassionate language, legibility, and predictable system behavior take precedence over decorative motion, streak pressure, or new features.

## Goals

1. Make foreground-app classification and pause presentation deterministic and testable.
2. Prevent silent monitoring failures and show users an honest monitoring state.
3. Ensure optional Usage Access never blocks the essential setup or app-selection flow.
4. Persist pause decisions promptly and keep Home, Insights, Journey, and the widget consistent with committed data.
5. Establish a compact, semantic Compose design system called **Quiet Ocean**.
6. Make every core screen resilient to TalkBack, large text, small screens, landscape, and reduced-motion preferences.
7. Provide previews and automated quality gates that support a fast Android Studio polish loop.
8. Correct release documentation and make physical-device evidence an explicit release gate.

## Non-goals

- No new backend, account, synchronization, social, subscription, or analytics system.
- No new XP economy, plant evolution, Nimbus evolution, streak penalty, or additional destination.
- No light theme or dynamic-color project in this phase.
- No persisted “onboarding complete” flag; revocable system permissions remain authoritative.
- No boot receiver or foreground polling service.
- No merge or reuse of the stale Scope 1 implementation.
- No claim that physical-device behavior or Play approval has passed until evidence exists.
- No broad navigation rewrite and no database encryption project.

## Guiding decisions

### Reliability before decoration

Core monitoring and persistence seams are fixed before visual refinement. UI work may proceed in parallel only where it does not depend on unsettled behavior.

### One monitoring architecture

The app keeps the current root-package `BreatheAccessibilityService` and `PauseOverlayHost`. There will be one classification path, one approval-state owner, and one source of monitoring health. The obsolete Activity-launching accessibility service is not introduced.

### Permission truth over onboarding state

Accessibility and overlay permission states are queried from Android. Usage Access remains optional and enriches estimates only. The UI never infers that monitoring is healthy merely because a Settings entry exists.

### Calm hierarchy over gamification

Home foregrounds current setup/monitoring state and today’s pause practice. Journey, badges, and Insights remain available but become secondary reflection destinations. Copy avoids shame, pressure, and “win/lose” framing.

## Architecture

### 1. Foreground transition classifier

Extract the event-decision logic from `BreatheAccessibilityService` into a pure, JVM-testable component. It consumes normalized foreground observations and produces explicit actions.

Inputs include:

- observed package and optional component;
- timestamp;
- whether the initial monitored-package snapshot is ready;
- current monitored-package set;
- currently approved visit state;
- app/system package classification;
- current overlay presentation state.

Outputs include:

- ignore with a reason;
- update current eligible app;
- revoke a previous visit approval;
- request a pause for a monitored app;
- remain initializing or degraded.

Required behavior:

- Ignore Breathe itself, System UI, the active IME, permission/settings surfaces, and non-launchable packages.
- Do not interpret notification shade, keyboard, or system-dialog windows as a meaningful app departure.
- Do not consume the first foreground event while monitored packages are still loading.
- After initial package readiness, reconcile the latest eligible observation so the first selected-app launch is not silently missed.
- Treat same-app window churn as the same visit.
- Revoke approval only after a meaningful transition to another eligible application.
- Define process-restart behavior explicitly: an in-memory approval may be lost and the app may re-prompt once after service recovery. This is accepted and documented rather than hidden.

`BreatheAccessibilityService` remains a thin Android adapter: normalize events, resolve exact service/component state, feed the classifier, and execute actions.

### 2. Intervention coordinator

Introduce a small application-scoped coordinator as the boundary between monitoring, pause presentation, and persistence. It does not own Compose rendering.

Responsibilities:

- gate event handling until monitored-package state is initialized;
- serialize pause requests so one overlay cannot be retargeted underneath another;
- invoke `PauseOverlayHost` and observe presentation success/failure/dismissal;
- publish monitoring health and the last meaningful monitoring event;
- accept the user’s decision and persist the outcome before publishing success;
- trigger aggregate/widget invalidation only after committed persistence.

The coordinator must not perform long usage scans before inserting the user’s decision. It records the decision promptly with available data, then performs optional saved-time enrichment in a follow-up operation. If enrichment fails, the decision remains valid and diagnostics record the failure.

Every presentation receives a coordinator-owned request ID. A successful overlay add acknowledges that ID immediately. The Activity fallback receives it as an intent extra and acknowledges from `onResume` only after pause content is ready. If no acknowledgement arrives within 2.5 seconds, the coordinator closes the pending request, suppresses automatic retry for that visit, and publishes `Degraded(PresentationNotConfirmed)`. Late acknowledgements are ignored after the request is closed. JVM tests cover request timeout and late acknowledgement; an instrumented test covers Activity acknowledgement.

Back has one consistent meaning in both overlay and Activity presentation: dismiss this pause and allow the current app visit without recording an intervention outcome. The coordinator approves the current visit in memory, removes the pause, and will not prompt again until a meaningful departure to another eligible app and a later return. An overlay removal caused by permission loss or window failure is not treated as a user Back action: it publishes degraded health, suppresses retry for the current visit, and requires recovery or a later meaningful visit before another presentation.

### 3. Monitoring health

Expose a read-only `StateFlow<MonitoringHealth>` from a repository/coordinator boundary. UI consumers do not inspect scattered services directly.

States:

- `SetupRequired(reason)` — Accessibility or overlay permission is missing.
- `Initializing` — permissions exist but monitored packages/service state are not ready.
- `Active(lastEventAt?)` — the service is connected and capable of presenting a pause.
- `Degraded(reason, recoveryAction?)` — event classification or overlay presentation failed, service binding cannot be confirmed, or permission changed mid-presentation.

Health reasons must be user-actionable where possible and diagnostic otherwise. The Home screen displays one concise status surface and a recovery action. It must never claim monitoring is active solely from an enabled-service string prefix; exact component comparison is required.

### 4. Optional usage enrichment

App selection always starts from launcher activities. Usage Access enriches ordering and time estimates only.

- A missing permission or `SecurityException` yields unknown/zero usage, not an error screen.
- Launcher enumeration and selection remain fully functional.
- Privacy and release copy describe exactly when aggregate usage is read.
- Usage reads run off the main thread and remain bounded.

### 5. Pause decision and retargeting

`PauseViewModel` uses cancel-and-replace initialization so an older package lookup cannot overwrite a newer request. Reason/tip/countdown state resets consistently for each new request.

Overlay coordination prevents multiple simultaneous targets. The Activity fallback remains a best-effort recovery only and must report degraded health if Android refuses it. The product and release documents must not present fallback behavior as guaranteed.

Decision persistence is two committed steps without a schema change:

1. Insert the event immediately with `minutesSaved = 0`; change the DAO insert contract to return the generated event ID.
2. For a declined outcome, calculate the bounded optional estimate and atomically update `minutesSaved` by event ID.

The first commit invalidates count/outcome aggregates and the widget. A successful enrichment update emits a second Room invalidation and widget refresh for saved-time totals. Zero means “no saved-time estimate available yet or after enrichment failure”; the UI displays the committed total without a pending spinner or an invented estimate. Opened outcomes remain zero and are never enriched. Tests verify insert-before-scan ordering, second-update behavior, cancellation/failure behavior, and that refreshes follow successful commits only.

### 6. Reactive aggregates

Room remains the source of truth. DAO/repository APIs expose reactive aggregate projections needed by Home and Insights rather than unrelated snapshot reloads.

The initial implementation should cover:

- today’s pause count;
- today’s intentionally declined count;
- today’s committed minutes saved;
- lifetime totals used by Journey;
- recent events/top monitored apps already shown by Insights.

The widget continues event-triggered refresh, but invalidation happens only after a successful database commit. Home and Insights collect the same committed source so foreground navigation does not display mismatched totals.

### 7. Room schema verification

Enable Room schema export and commit schema JSON. Reconstruct faithful v1–v4 schema JSON from the checked-in migration DDL and validate it against versioned SQLite fixture databases before relying on `MigrationTestHelper`; commit the current v5 schema generated by Room. Add instrumented tests for 1→5, 2→5, 3→5, and 4→5, plus each adjacent migration. Tests verify that monitored apps, pause durations, and intervention events survive upgrades and that removed quote data does not break opening the database. If a reconstructed historical schema cannot be validated, use a checked-in versioned database fixture for that starting version instead of fabricating a schema.

This phase does not add a schema version unless implementation genuinely requires one.

## Quiet Ocean design system

### Semantic color roles

Retain the deep-ocean and teal identity, but route UI through semantic roles rather than screen-local colors:

- `canvas` — immersive base background;
- `surface` — standard content surface;
- `surfaceRaised` — primary focus card/dialog;
- `surfaceQuiet` — secondary grouping;
- `actionPrimary` and `onActionPrimary`;
- `textPrimary`, `textSupporting`, and `textMutedAccessible`;
- `statusHealthy`, `statusAttention`, and `statusError`;
- `selection` and `focusRing`.

All normal text must meet WCAG AA contrast against its actual composited surface. Warning and status colors must come from theme roles, not raw literals.

### Typography, shape, and spacing

Use a small named scale:

- display/focal;
- screen title;
- section title;
- body;
- supporting;
- label.

Avoid fixed custom sizes below accessible Material label sizing. Use a compact shape scale for small controls, cards, and focal containers. Use a consistent 4/8-based spacing scale and replace one-off padding/radius values while touching each screen.

### Motion

One reduced-motion source governs all decorative motion:

- waves;
- onboarding pulse;
- navigation transitions;
- Nimbus drift/shimmer;
- picker entrance/bounce;
- stats entrances;
- confetti;
- breathing rings.

When system animations are disabled, decorative motion stops and state remains understandable. With motion enabled, Pause keeps the slow breathing focal movement; navigation and supporting screens use short fades/settles instead of long lateral slides.

### Shared primitives

Add only the primitives needed to eliminate repeated visual decisions:

- `BreatheScreen` for background, insets, and scroll conventions;
- `QuietCard` with standard and raised emphasis;
- `SectionHeader`;
- `MonitoringStatusCard`;
- semantic selectable row/chip wrappers;
- compact metric presentation that can stack or wrap.

Primitives must remain plain Compose components, be previewable, and expose accessibility semantics.

## Screen behavior and visual hierarchy

### Onboarding

- Keep the prominent Accessibility disclosure and affirmative consent.
- Make the entire layout scroll-safe for small screens, landscape, and 200% font size.
- Present setup as a short ordered sequence with clear complete/current/remaining states.
- Keep Usage Access visibly optional.
- Refresh permission state on resume and route to Home only when essential permissions are genuinely ready.
- Align system-bar appearance and launch background with the dark Quiet Ocean canvas.

### Home

Home shows exactly one primary state:

1. setup/recovery needed;
2. no monitored apps selected;
3. active practice summary.

Monitoring health is visible near the top. Nimbus supports the state rather than competing with it. The monitored-app list and its pause-duration control remain the primary management surface.

Journey and Insights remain reachable, but their actions become quieter secondary destinations. Dense three-column metrics reflow or stack for large text and narrow widths.

### App selection

- Rename the destination from “Distractions” to “Choose apps to pause.”
- Use compassionate explanatory copy.
- Enumerate launcher apps regardless of Usage Access.
- Use semantic selectable rows with role, selected state, and state description.
- Keep search, loading, empty, error, and retry states.
- Lazy-load or otherwise bound expensive icon work where practical.
- Make the confirmation action describe the selection count without implying judgment.

### Pause

- Preserve the breathing focal point and deliberate countdown.
- Keep the reason prompt optional and visually secondary.
- Use semantic selectable chips that wrap at large font sizes.
- Replace attempt/streak-pressure copy with neutral, supportive language.
- Preserve a clear choice to continue and a clear choice to do something else.
- Ensure scrollability, overlay insets, Back handling, rotation recovery, TalkBack order, and reduced motion.
- Confetti is optional delight for an intentional decline; it is suppressed for reduced motion and never blocks dismissal.

### Insights and Journey

- Unify card, metric, and section styles with Home.
- Reframe metrics as reflection, not performance pressure.
- Keep existing data and destinations; do not expand the achievement model.
- Make dense metric groups responsive to width and font scale.
- Empty states should explain how data appears without pushing the user toward compulsive use.

### Widget

- Align background, typography, and accent colors with Quiet Ocean.
- Preserve glanceable count/time content and accessibility descriptions.
- Verify day rollover and refresh after committed intervention events.

## UI implementation contract

### Exact visual tokens

All listed colors are opaque ARGB values. Alpha may be applied only for decorative canvas work, disabled state, or scrims—not to normal text.

| Token | Value | Material mapping / use |
|---|---:|---|
| `canvas` | `#012A36` | `background`, app/system-bar base |
| `surface` | `#0B3A48` | `surface`, standard cards |
| `surfaceRaised` | `#124E5E` | `surfaceContainerHigh`, focal card/dialog |
| `surfaceQuiet` | `#08313E` | `surfaceContainerLow`, secondary groups |
| `actionPrimary` | `#80CBC4` | `primary` |
| `onActionPrimary` | `#012A36` | `onPrimary` |
| `textPrimary` | `#F1FAFB` | `onBackground`, `onSurface` |
| `textSupporting` | `#C2E1E4` | supporting body copy |
| `textMutedAccessible` | `#9EC9CE` | labels/metadata; 6.83:1 on `surface` |
| `statusHealthy` | `#70D6C7` | active/complete icon plus text |
| `statusAttention` | `#FFD166` | setup/initializing icon plus text |
| `statusError` | `#FF8A80` | degraded/error icon plus text |
| `selection` | `#4DB6AC` | selected outline/fill |
| `focusRing` | `#B2EBF2` | keyboard/accessibility focus indication |

Typography uses the platform sans-serif family and honors user font scale:

| Token | Size / line height | Weight |
|---|---:|---:|
| `focal` | 28sp / 36sp | 700 |
| `screenTitle` | 28sp / 34sp | 700 |
| `sectionTitle` | 20sp / 26sp | 600 |
| `body` | 16sp / 24sp | 400 |
| `supporting` | 14sp / 20sp | 400 |
| `label` | 14sp / 18sp | 600 |

Permitted corner radii are 12dp for controls, 20dp for cards, 28dp for focal containers, and 50% for circular/pill treatment. Permitted layout spacing is 4, 8, 16, 24, 32, 48, or 64dp. Cards use 0dp tonal separation by default and at most 2dp elevation for a focal/recovery surface. Focus indication is a 2dp `focusRing` outline with 2dp separation from content.

As a composition target, canvas/background occupies roughly 60% of a standard screen, standard/raised surfaces roughly 30%, and primary/status accents no more than roughly 10%. This is a hierarchy guard, not a pixel-count test: teal accent identifies selection, focus, primary action, and focal breathing state rather than decorating every card.

### Rendered state and content order

`HomeUiState` includes `monitoringHealth`, `chosenApps`, `todaySummary`, `journeySummary`, and `isLoading`. It renders in this fixed order:

1. app bar with Breathe title and settings/help action if present;
2. `MonitoringStatusCard`;
3. exactly one primary-state card;
4. chosen-app management section;
5. today’s reflection summary when data exists;
6. secondary Journey and Insights destinations.

Primary-state mapping:

| Input | Primary card | Recovery/action copy |
|---|---|---|
| `SetupRequired(Accessibility)` | “Finish setup to begin” / “Breathe needs Accessibility access to notice when a chosen app opens.” | “Open accessibility settings” |
| `SetupRequired(Overlay)` | “Finish setup to begin” / “Allow Breathe to show your pause above a chosen app.” | “Allow display over apps” |
| `Initializing` for ≤5s | “Getting pauses ready” / “This usually takes a moment.” | no action |
| `Initializing` for >5s | “Setup needs a check” / “Breathe has not connected yet.” | “Review setup” |
| `Degraded(reason)` | “Pauses need attention” plus a reason-specific, nontechnical explanation | `recoveryAction` label; default “Try again” |
| `Active` with no chosen apps | “Choose where you want breathing room” / “Pick the apps where a short pause would help.” | “Choose apps” |
| `Active` with chosen apps | “Pauses are ready” / “Watching {count} chosen app(s).” | no primary recovery action |

`DegradedReason` is closed for this phase and maps exactly as follows:

| Reason | Title | Body | Recovery action |
|---|---|---|---|
| `PresentationNotConfirmed` | “Pause could not appear” | “Android did not confirm that your pause opened.” | “Try again” |
| `OverlayUnavailable` | “Display permission needs attention” | “Breathe cannot show a pause above your chosen apps.” | “Review display permission” |
| `ServiceDisconnected` | “Monitoring paused” | “Breathe is not connected to Accessibility right now.” | “Review accessibility settings” |
| `PermissionChanged` | “Setup changed” | “A permission Breathe needs was turned off.” | “Review setup” |
| `MonitoredAppsUnavailable` | “Chosen apps are unavailable” | “Breathe could not load your chosen apps.” | “Try again” |
| `ClassifierFailure` | “Monitoring needs a restart” | “Breathe could not safely identify the current app.” | “Try again” |

Unknown technical failures are logged and map to `ClassifierFailure`; UI code does not create ad hoc degraded copy. “Try again” reinitializes coordinator state once. If it fails again, the state remains degraded and the action changes to “Review setup,” which routes through the permission/status checklist.

Other screen order:

- **Onboarding:** wordmark → setup progress → current permission explanation/disclosure → current primary action → completed essentials → optional Usage Access → privacy reassurance.
- **Picker:** app bar/title → compassionate supporting copy → search → loading/error/empty/app list → selection-count confirmation action.
- **Pause:** app context → breathing focal/countdown → supportive tip → optional reason group → “Do something else” and “Continue to {app}” decisions.
- **Insights:** app bar → period/reflection headline → responsive metrics → recent/top-app reflection → empty explanation.
- **Journey:** app bar → current level/progress → responsive lifetime metrics → badge path; no streak penalty or urgency copy.

Required copy changes:

- “Distractions” → “Choose apps to pause.”
- Picker support → “Choose the apps where you want a little breathing room.”
- Picker confirmation → “Pause {count} app(s).”
- Pause reason heading → “What brought you here?”
- Pause reason support → “Optional — noticing is enough.”
- Attempt-pressure/ordinal copy → “A familiar moment.”
- Continue action → “Continue to {app}.”
- Decline action → “Do something else.”
- Insights framing → “Your reflection.”

Existing error details may remain specific, but user-facing copy must state what happened, what data was preserved, and the next available action without blame.

### Responsive contract

The required reference configurations are:

| Name | Viewport | Font scale | Expected behavior |
|---|---:|---:|---|
| Compact portrait | 320×568dp | 1.0 | all core actions reachable by vertical scroll |
| Standard portrait | 360×800dp | 1.0 | normal reference layout |
| Large type | 360×800dp | 2.0 | no clipped text or horizontal scrolling |
| Compact landscape | 640×360dp | 1.0 | scroll-safe content; decisions remain reachable |

At widths below 400dp or font scale at/above 1.3, metric groups stack in one column. At wider sizes and font scale below 1.3, they may use two columns only when each cell receives at least 152dp. Core screens never use a fixed three-column metric row. Reason and duration choices use `FlowRow` with a minimum 48dp height and wrap naturally; they never use equal-weight fixed-width labels. Onboarding and Pause always have a vertically scrollable content region. Bottom actions may remain visually anchored only when the scrollable region accounts for their height and no content can be obscured.

### TalkBack and selection semantics

| Element | Semantic contract |
|---|---|
| Picker app row | `Role.Checkbox`, label app name, `selected`, state description “Selected for pausing” / “Not selected,” click label “Add” / “Remove” |
| Monitored-app row | heading app name followed by pause duration; explicit Remove button with app name in label |
| Pause-duration option | `Role.RadioButton`, group label “Pause length,” selected state, label such as “10 seconds” |
| Reason chip | `Role.RadioButton`, group label “Reason, optional,” selected state; proceeding without a selected chip is the skip path |
| Monitoring status | heading with state text; not clickable as a whole; recovery control is `Role.Button` with the displayed action label |
| Pause decisions | `Role.Button`; labels exactly match visible text; “Continue” is focused after optional reason content, never before the focal pause |
| Countdown | description “Pause, {seconds} seconds remaining”; no live-region update each second; announce politely only when the pause begins and when Continue becomes available |
| Metric card | merged description such as “Pauses today, 3”; decorative icons excluded |
| Icon-only action | `Role.Button` and a specific label such as “Open Insights”; no duplicate child announcement |

Focus order follows the content orders above. Canvas waves, Nimbus decorative particles, confetti, and decorative icons use cleared/absent semantics. Status always includes text in addition to color.

### Reduced-motion contract

Provide a single observable `MotionPolicy` through a CompositionLocal. Its Android implementation observes `Settings.Global.ANIMATOR_DURATION_SCALE` with a `ContentObserver`, rechecks on lifecycle resume, and exposes `Full` or `Reduced`; previews/tests inject either value.

| Motion | Full | Reduced |
|---|---|---|
| Wave background | slow drift | fixed wave phase |
| Onboarding pulse | slow scale/alpha pulse | static emphasis |
| Navigation | ≤220ms fade + ≤16dp settle | `snap`, no translation |
| Nimbus | slow drift/shimmer | static pose |
| Picker/stats entrance | ≤180ms fade | immediately visible |
| Picker bounce | omitted in both modes | omitted |
| Confetti | short nonblocking celebration | omitted |
| Breathing rings | slow breathing scale | static rings; countdown/text unchanged |
| Numeric/state changes | short crossfade | immediate replacement |

No component reads animation settings independently after this boundary exists.

### Pure content and previews

The following roots become pure presentational composables while route wrappers collect ViewModels and perform navigation/system actions:

- `OnboardingContent(OnboardingUiState, callbacks, MotionPolicy)`;
- `HomeContent(HomeUiState, callbacks, MotionPolicy)`;
- `AppSelectContent(AppSelectUiState, callbacks, MotionPolicy)`;
- `PauseContent(PauseUiState, callbacks, MotionPolicy)`;
- `StatsContent(StatsUiState, callbacks, MotionPolicy)`;
- `AchievementsContent(AchievementsUiState, callbacks, MotionPolicy)`;
- `MonitoringStatusCard(MonitoringHealth, onRecovery)`.

Each root has a standard 360×800 preview. In addition:

- Onboarding: essential-setup state at Compact portrait and Large type.
- Home: setup-required, active-empty, and active-populated at Standard; populated at Large type.
- Picker: loading, error, empty, and multi-selected at Standard; selected at Large type.
- Pause: initial, countdown-active, reason-selected, and Continue-ready at Standard; Continue-ready at Compact landscape and Large type; one Reduced fixture.
- Insights: empty and populated at Standard; populated at Large type.
- Journey: base/populated at Standard and Large type.
- Monitoring status: every health state in a grouped component preview.

Preview fixtures are deterministic local Kotlin values. They do not call Hilt, Room, Android Settings, PackageManager, clocks, or random-tip APIs.

### UI acceptance matrix

| Area | Automated assertion |
|---|---|
| Theme | screen code contains no raw warning/error colors; token contrast tests cover every text/background pair |
| Onboarding | semantic primary action for each essential state; Compact, Large type, and landscape screenshots/previews render with reachable final action |
| Home | one primary card only; monitoring heading/action semantics; metrics stack at Large type |
| Picker | app rows expose checkbox role/selected state; missing Usage Access still renders launcher apps; search and confirmation reachable at Large type |
| Pause | reason/duration selection roles; no per-second live-region spam; actions reachable in Compact landscape and Large type; Reduced fixture has no running decorative transition |
| Insights/Journey | no fixed three-column group at Large type; metric descriptions merge label/value; empty state is present |
| Motion | every animation in the policy table has a Reduced assertion or deterministic static preview |
| Previews | every named pure content root compiles without ViewModel/system dependencies |
| Widget | committed event refresh and day-rollover data behavior covered; colors match token resources |

## Accessibility requirements

- Every actionable control has a role, label, state, and at least a 48dp touch target.
- Selectable rows/chips use Compose selection semantics rather than generic click semantics.
- Decorative images and canvas work are hidden from accessibility services; meaningful status is represented as text/semantics.
- TalkBack focus order follows the visual decision order.
- Core flows remain usable at 200% font scale, in landscape, and on small API 26-class screens.
- Normal text meets AA contrast; status is never communicated by color alone.
- System “remove animations” disables all nonessential animation.
- Countdown and changing monitoring status avoid noisy repeated announcements.

## Error handling and observability

- Expected permission absence is UI state, not an exception.
- Usage enrichment failures degrade to unknown usage.
- Overlay add/remove failures publish degraded monitoring health and retain diagnostic logs.
- Database decision-write failure is recorded and does not cause widget/aggregate success invalidation.
- Service initialization timeout or lost binding becomes degraded health with a recovery action.
- No personal app-selection or intervention data is sent off-device.
- Release logging removes verbose/debug entries while preserving actionable warnings/errors.

## Android Studio and preview workflow

Add focused previews for:

- onboarding setup stages and large text;
- Home setup-required, empty, and populated states;
- app picker loading/error/selected states;
- Pause initial/countdown/reason-selected states;
- Insights empty/populated states;
- Journey base state;
- monitoring status variants;
- reduced-motion representations where visually different.

Stateful screens should separate presentational content from ViewModel collection only where required to enable previews and UI tests. Do not introduce a separate UI architecture solely for previews.

## Testing strategy

### JVM tests

- foreground classifier/state machine;
- initial monitored-package readiness and first-event reconciliation;
- system package/IME/System UI filtering;
- visit approval and meaningful revocation;
- exact service-state parsing;
- PauseViewModel cancel-and-replace retargeting;
- optional Usage Access fallback;
- decision persistence ordering and aggregate invalidation;
- reactive aggregate mapping.

All behavioral changes follow red-green-refactor: a targeted test must fail for the intended reason before production code changes.

### Instrumented tests

- Room migrations through version 5;
- critical Compose semantics for selectable app rows, reason chips, and duration controls;
- onboarding navigation/permission-state rendering using injectable state boundaries;
- widget data rendering where practical.

### Automated build gate

From a clean checkout with JDK 17 and Android SDK 36:

```bash
./gradlew --no-daemon --stacktrace \
  :app:testDebugUnitTest \
  :app:connectedDebugAndroidTest \
  :app:lintDebug \
  :app:assembleDebug \
  :app:assembleRelease \
  :app:bundleRelease
```

`connectedDebugAndroidTest` runs when a configured emulator/device exists. CI must always run JVM tests, lint, and assemble/bundle tasks. Triage the current lint warnings once, fix correctness/security/accessibility warnings in touched flows, and generate a checked-in `app/lint-baseline.xml` only for explicitly accepted legacy warnings. Configure lint with `baseline = file("lint-baseline.xml")`, `abortOnError = true`, and `warningsAsErrors = true`; therefore existing reviewed entries are suppressed and every new warning fails the build. CI also runs `git diff --exit-code -- app/lint-baseline.xml` after lint so a task cannot silently expand the baseline.

CI additionally inspects the merged release manifest and asserts:

- no `INTERNET`;
- no `QUERY_ALL_PACKAGES`;
- no foreground-service permissions;
- Accessibility Service remains narrow and `canRetrieveWindowContent="false"`;
- only intended special accesses remain.

### Physical-device release gate

No automated test substitutes for this gate. Record evidence for:

- Android 15/16 on a Pixel-class device;
- a current Samsung/OEM device with battery-management behavior;
- an API 26–28 device or representative hardware where available;
- Accessibility and overlay grant, revoke, and re-grant;
- selected app launch, overlay appearance, both outcomes, Back, rotation, lock/unlock, reboot, service recovery, and force-stop behavior;
- notification shade, IME, and system dialog transitions without false re-prompt;
- optional Usage Access declined;
- TalkBack, 200% font, landscape, and remove-animations;
- widget refresh/day rollover;
- signed, minified release APK behavior.

Reliability measurement and pass rules:

- On each required device/OS combination, run 10 normal selected-app launches, 10 leave-and-return visits, and 10 notification-shade/IME/system-dialog interruptions.
- Every normal launch and leave-and-return visit produces exactly one visible pause: zero misses and zero duplicates are permitted.
- System-surface interruptions produce zero additional pauses and do not revoke approval for the underlying app visit.
- Run process death, reboot/rebind, permission revoke/re-grant, screen unlock, Back dismissal, rotation, and both decision outcomes three times each per device. All three repetitions must match the documented recovery behavior with no loop or crash.
- Measure from the qualifying Accessibility event timestamp to overlay/Activity content-ready acknowledgement using monotonic time. Median presentation delay must be at most 1 second, and no normal launch may exceed 2 seconds.
- Continue opens/allows the chosen app exactly once. “Do something else” exits safely. Back approves the current visit without recording an outcome and does not re-prompt until a meaningful leave-and-return.
- TalkBack remains functional, no screen/window content is retrieved, and no hidden API or programmatic permission grant is used.
- The release gate is `GO` only if every reliability rule passes on every required device and the policy/disclosure evidence matches the tested build. Any reliability failure yields `NO-GO: RELIABILITY`; any declaration/disclosure mismatch yields `NO-GO: POLICY`. Missing required hardware yields `BLOCKED: DEVICE EVIDENCE`, not a pass or failure inference.

The result is committed as an evidence table with timestamps, device/build identifiers, measured overlay latency, failures, and a GO/NO-GO decision. Missing devices result in “release gate blocked,” never an inferred pass.

## Release and policy requirements

- Correct `RELEASE.md` to reflect current Google Play Accessibility Service declaration requirements, including a demo video for a non-accessibility-tool use case.
- Keep the in-app prominent disclosure synchronized with the recorded demo and store listing.
- Verify the public privacy-policy URL in a private browser session.
- Update privacy/data-safety descriptions if optional usage enrichment remains.
- Configure signing outside source control and verify the signed AAB/APK with standard Android/JDK tooling.
- Bump `versionCode` only when producing an actual upload candidate.
- Never claim Play approval before Console review completes.

## Delivery sequence

1. Branch/workspace setup and baseline verification.
2. Foreground classifier, coordinator boundary, and monitoring health with JVM tests.
3. Optional usage fallback, decision ordering, retargeting, and reactive aggregates with tests.
4. Room schema export and migration instrumentation tests.
5. Quiet Ocean semantic design system and shared primitives.
6. Onboarding, Home, picker, Pause, Insights, Journey, and widget refinement.
7. Compose semantics/previews and reduced-motion completion.
8. CI/release documentation and manifest policy gate.
9. Full automated verification and independent whole-branch review.
10. Physical-device evidence and release decision when hardware is available.

Tasks 2–4 define behavior that UI tasks consume. Visual token/primitives work may begin after interfaces are stable. The final branch must not merge until automated verification and whole-branch review pass. Release remains separately blocked until physical-device evidence passes.

## Acceptance criteria

### Automated completion

- Clean unit-test, lint, debug build, release build, and bundle gates.
- Foreground classifier and coordinator behavior have focused regression coverage.
- Missing Usage Access does not prevent viewing or selecting apps.
- Home reports setup, initializing, active, and degraded monitoring honestly.
- Pause decisions are committed before metrics/widget success invalidation.
- Home and Insights observe consistent committed aggregate data.
- Room schemas are exported and supported migrations are verified.
- Onboarding, Home, picker, Pause, Insights, Journey, and widget use the exact Quiet Ocean token table; their standard cards, headings, status, selections, and metric groups use the named shared primitives rather than screen-local equivalents.
- Picker rows, duration options, reason chips, monitoring recovery, pause decisions, metric cards, and icon actions satisfy the element-by-element semantic contract and its automated assertions.
- Every core screen satisfies the Compact portrait, Large type, and Compact landscape reflow rules; required controls remain reachable and text is not clipped.
- Every animation listed in the reduced-motion table has a deterministic Reduced assertion or preview and no component independently reads the system animation scale.
- Every pure content root and every named state/configuration preview in the preview inventory compiles without Hilt, ViewModel, Room, Settings, PackageManager, clock, or randomness dependencies.
- Release documentation matches the implemented permission and policy story.
- Independent whole-branch review has no unresolved critical or important findings.

### Release completion

- Physical-device matrix evidence exists and meets the defined reliability threshold.
- Signed/minified release APK is installed and exercised.
- Accessibility declaration/demo evidence matches the build.
- Public privacy policy is reachable.
- Final GO/NO-GO release decision is documented.

Automated completion may be achieved without connected hardware. Release completion may not.

## Success measure

A user can understand setup, select apps without granting optional data access, trust the visible monitoring state, encounter one calm and reliable pause per meaningful visit, make a choice without pressure, and immediately see consistent reflection data. A developer can open the project in Android Studio, preview the important UI states, run the quality gates, and know exactly what still requires physical-device evidence.
