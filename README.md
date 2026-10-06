# Breathe

An Android app that intercepts distracting app launches and shows a mindful pause — a breathing animation, a grounding tip, and a simple choice: keep going or go back.

Built as a free alternative to [One Sec](https://one-sec.app), using only public Android APIs.

---

## How it works

1. An `AccessibilityService` listens for window-state changes to detect which app has come to the foreground
2. When a blocked app is detected, a full-screen pause is drawn in an overlay window on top of it
3. The screen shows a breathing animation, an optional grounding tip, an optional "why am I opening this?" prompt, and two buttons
4. **No, go back** → sends you home. **Yes, open [App]** → lets you through

Leaving an app clears its approval and dismisses an outstanding pause. Turning the screen off also clears approvals. Keyboard windows and the notification shade preserve the underlying visit. Detection depends on Android delivering a window-state event; it cannot guarantee interception before app content appears.

Home shows accessibility permission, live service connection, overlay permission and app-list loading separately. Use **Test a pause** to open one of your monitored apps, or **Details** to see the last successfully displayed overlay in this process. A permission grant alone does not prove a working connection.

Home also offers **Take a break**: snooze all app pauses for 15 minutes, 30 minutes or 1 hour, or choose **Resume now**. Starting a snooze dismisses a pending overlay and clears visit approvals without recording a choice. Your monitored apps, pause lengths, permission grants and history stay saved. The deadline is stored locally and survives process restarts and reboot. When it expires, future detected visits can pause again; Breathe does not interrupt an already open app just because the timer expires.

Snooze uses the device clock. Changing that clock can change when it ends. No exact-alarm or foreground-service permission is added: the accessibility event path checks the deadline directly, including after sleep, while open screens update their status from an observed timer. Permission or service failures can still prevent monitoring after a snooze ends.

Levels use distinct calendar days with recorded choices, and badges use days and choice totals. Both Continue and Go back count. Days do not have to be consecutive. Progress is recalculated from existing local history; no database schema change or history deletion is needed. Estimated skipped session time remains separate from achievements.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository pattern |
| DI | Hilt |
| Database | Room (monitored apps + intervention history) |
| Networking | None — the app is fully offline |
| Background | AccessibilityService (system-bound, rebound after reboot) |
| Build | Gradle 9.6.1 + Kotlin DSL |

---

## Project structure

```
app/src/main/java/com/dgraciano/breathe/
├── data/
│   ├── db/          # Room DAOs + Database
│   ├── model/       # BlockedApp, InterventionEvent entities
│   └── repository/  # AppRepository, StatsRepository
├── di/              # Hilt modules (Coroutines, Database, SystemService)
├── service/         # BreatheAccessibilityService, SessionApprovalStore, SessionTimeHelper
├── widget/          # PauseCountWidget (home-screen RemoteViews widget)
└── ui/
    ├── onboarding/  # Permission setup screen
    ├── home/        # Monitored apps list
    ├── appselect/   # App picker
    ├── pause/       # The breathing screen (PauseOverlayHost + PauseActivity + PauseScreen)
    ├── stats/       # Recorded choices and estimated skipped session time
    ├── achievements/# Progress path
    ├── nav/         # Compose navigation graph
    └── theme/       # Colors, Theme
```

---

## Setup

### Requirements

- Android Studio (latest stable)
- Android phone running Android 8.0+ (API 26+)
- A physical device for release checks. Emulators can verify basic overlay behavior;
  they do not reproduce OEM battery management or every accessibility/gesture behavior.

### Run locally

```bash
git clone https://github.com/dgraciano8424/breathe
```

Open the `breathe` folder in Android Studio. Wait for Gradle sync to complete, then hit Run.

### Permissions

Two special permissions must be granted manually. Interception does not work without
both — the app needs to know an app opened, and to be allowed to draw over it.

- **Accessibility access** — Settings → Accessibility → Breathe → On. Detects which app
  came to the foreground. Scoped to window-state events with
  `canRetrieveWindowContent="false"`, so it cannot read screen contents.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — Settings → Apps → Special App
  Access → Display over other apps → Breathe → Allow. Draws the pause over the app you
  are opening.

One further permission is optional:

- **Usage Access** (`PACKAGE_USAGE_STATS`) — Settings → Apps → Special App Access →
  Usage Access → Breathe → Allow. Only enriches the stats screens with time spent per
  app. Everything else works without it.

The onboarding screen walks you through these on first launch, and shows a disclosure
explaining what the accessibility service does before sending you to Settings.

---

## Key concepts (for learning)

**Why an accessibility service?** Android kills background processes aggressively, so
something has to stay alive to notice app launches. The first version used a foreground
service polling `UsageStatsManager` every 500ms — which worked, but meant a permanent
notification, a timer running whenever the screen was on, and a `specialUse` foreground
service type that Play makes you justify with a demo video.

An accessibility service is bound and kept alive by the system itself, and rebound after
reboot, so it needs no foreground service, no boot receiver, and none of the three
permissions those required. The trade is scrutiny: accessibility is a powerful API, Play
reviews it closely, and users are right to be cautious. The service is scoped as narrowly
as the feature allows — window-state events only, no window-content retrieval — so it can
see *which* app opened and nothing inside it.

**Why listen instead of poll?** Android has no public callback for "app X just launched",
so the original design polled: `UsageStatsManager` is a pull API you ask "what happened
recently?" on a timer. Polling has two costs — the timer, and latency, since the pause
could arrive up to half an interval after the app. Accessibility window events are pushed
as the window changes, so the pause can appear promptly when an app comes to the foreground.
This does not prevent the app from loading content or guarantee that none of it is briefly visible.

**Why not `isAccessibilityTool`?** Because it would be a lie. That flag marks services
built to assist users with disabilities; Play policy explicitly excludes monitoring apps,
and claiming it falsely risks losing the developer account.

**Why the repository pattern?** The UI doesn't need to know if data comes from a database or an API. The repository decides. This makes screens simple and logic testable.

**Why Hilt?** Without dependency injection, every class creates its own dependencies, making testing hard and code tightly coupled. Hilt wires everything together at startup so classes just declare what they need.

---

## Roadmap

See [REVIEW.md](REVIEW.md) for the latest reliability changes, verification and recommended product direction. Session-time totals are estimates from past usage, with a 20-minute fallback, rather than measured time saved.

- [x] App icons and launch screen
- [x] Per-app custom pause duration
- [x] Stats screen (how many pauses, how many times you went back)
- [x] Achievement progress based on active days and choices
- [x] Widget showing daily Continue and Go back choices
- [x] Live monitoring diagnostics and chosen-app setup test
- [ ] Play Store release

Verification: `./gradlew testDebugUnitTest assembleDebug lintDebug`. With Python 3 installed,
`python scripts/verify-progress-query.py` also exercises the Room aggregate SQL against
SQLite with empty history, multiple choices on one day, and nonconsecutive legacy history.

---

## License

MIT

## Local history controls

Your patterns includes Export history CSV and a confirmed Clear history action. The system file picker chooses where the UTF-8 CSV is saved. It includes timestamps, app names/packages, choices, optional reasons and estimated skipped minutes. Clearing resets recorded choices, milestones and estimates while keeping monitored apps and durations. Exported files remain wherever you saved them.

The ocean background is static. Reduced motion follows Android's animator setting, and monitored-app controls can wrap at larger font sizes.

## Security and release readiness

See [SECURITY_REVIEW.md](SECURITY_REVIEW.md) for the reviewed boundaries, dependency checks, hardening and remaining release work. Passing builds and automated tests do not replace physical-device, database-upgrade and final signed-release validation.

## Personalize your pause

Home offers **Add a personal reminder** (or **Edit your personal reminder**). Write up to 140 characters, such as taking a short walk or finishing a chapter. Save applies it to both the overlay and fallback pause screen; Cancel keeps the previous reminder. Clear text and Save removes it. This global preference stays local, is excluded from backup/transfer, and is not copied into choice history or exports.

**Add an intention (optional)** reveals Work, Learn, Relax, Connect, Curious, Bored, Habit and Escaping. Tap again to clear a selection. Skip it whenever you want. Go back remains available immediately, and Continue uses the same configured countdown. Existing recorded reason keys and history stay intact; no database schema change is introduced.
