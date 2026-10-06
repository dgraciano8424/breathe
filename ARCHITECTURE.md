# Breathe architecture

Breathe has two independent implementations of the same product idea. The Android app and Chrome extension keep settings and history on the device. There is no backend, account, telemetry or synchronization between platforms.

## Android

Compose screens observe ViewModels. Repositories own data access; Hilt supplies dependencies. Room stores monitored apps, recorded choices and the durable pending-choice queue. Small local preferences hold the reminder and snooze deadline. Visit approvals exist only in process memory.

The accessibility service consumes window-state events and tracks foreground visits. It ignores transient keyboards and System UI windows, clears approvals on departure/screen off, and asks the overlay host to display a pause. The host manages Android window attachment, lifecycle owners and navigation. A dedicated factory constructs its ViewModel without exposing database and statistics dependencies to the window host.

`domain/PausePolicy.kt` owns countdown and duplicate-choice rules. It has no Android or storage dependencies and takes a clock function. Production supplies monotonic elapsed time; tests supply a controlled clock. The countdown starts when the ready pause is rendered. Continue is checked by the coordinator as well as the button. Go back has no countdown requirement.

### Recording a choice

1. The policy claims the choice and the screen disables conflicting taps.
2. The ViewModel captures the visit ID, target, intention and timestamp. `ChoiceRecorder` commits them to Room's `pending_choices` table on IO.
3. Only after that commit does the current host dismiss/navigate, and only a still-current Continue grants approval. A failed commit leaves a retry message and permits another choice without restarting the timer.
4. A process-scoped worker delivers pending choices to history. Inserting history and removing the queued record happen in one Room transaction. A unique history index on the choice ID prevents duplicate delivery. Legacy records retain null IDs.
5. Delivery failures retry with bounded backoff while the process lives. Every application startup signals recovery, including starts caused by the service or widget. Optional usage estimation failures use the existing 20-minute estimate rather than losing a choice.
6. History delivery refreshes widgets. Home, Insights and Achievements observe history changes so startup recovery also refreshes open screens.

A process killed before the initial durable commit cannot guarantee a recorded tap; the host has not acknowledged success yet. After the commit, the choice survives termination and is delivered when the process can run again. The queue adds no network, foreground service, exact alarm or new permission. Clearing history deletes recorded and queued choices in one transaction, preserving monitored apps and preferences.

Database v6 adds the queue and unique choice ID. The v5→v6 migration preserves settings and existing history. Exported v5/v6 schemas and populated migration checks document this boundary.

## Chrome

WXT builds a Manifest V3 extension with strict TypeScript and vanilla DOM interfaces. `entrypoints/background.ts` is the composition point. Runtime messages and browser events enter one serial queue; UI pages do not write shared state.

| Component | Responsibility |
|---|---|
| `state-store` | Local state access and temporary per-tab storage keys |
| `rule-controller` | Redirect/approval rules, expiry alarms and startup reconciliation |
| `settings-controller` | Validate settings and coordinate rollback when changes fail |
| `page-access` | Verify that pause actions originate from extension pause contexts |
| `pause-controller` | Save a pause, coordinate a choice and recover failed navigation |
| `runtime` | Register browser events and authorize/route messages |
| `serial-queue` | Order mutations and keep later work running after a rejection |
| `pause-policy` | Pure deadline and repeated/expired-choice rules shared by UI and coordinator |

Browser redirect rules handle enabled, explicitly granted websites. Local storage keeps settings and aggregate counts; session storage keeps active destination URLs, pauses and approvals. Per-tab allow rules end on departure, tab closure or the existing 20-minute limit. Settings and counts retain their existing formats in extension 0.6.3.

The pause page can only act on its own top-level tab. Settings mutations require a settings or popup context. The coordinator verifies the countdown independently of the UI. Failed navigation restores the previous counts, revokes the temporary permit and restores the pending choice. Revoked host access releases the pause.

## Platform boundaries and verification

Android detection depends on delivered accessibility events and permitted overlays; protected screens can hide them. Chrome handles new top-level HTTP(S) GET visits; already loaded tabs and SPA-only transitions remain outside interception. These are platform/product boundaries, not guarantees of enforced blocking.

Regression checks cover pure policies, coordinator failure paths, queue recovery, SQL migrations/indices, complete extension browser workflows, and optimized Android builds. Emulator acceptance verifies actual Room recovery and live overlay behavior. Samsung, older-API/fallback and native user-browser acceptance remain listed in `QUALITY_STATUS.md`.
