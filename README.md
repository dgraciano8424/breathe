# Breathe

An Android app that intercepts distracting app launches and shows a mindful pause — a breathing animation, a grounding tip, and a simple choice: keep going or go back.

Built as a free alternative to [One Sec](https://one-sec.app), using only public Android APIs.

---

## How it works

1. An `AccessibilityService` listens for window-state changes to detect which app has come to the foreground
2. When a blocked app is detected, a full-screen pause is drawn in an overlay window on top of it
3. The screen shows a breathing animation, a grounding tip, an optional "why am I opening this?" prompt, and two buttons
4. **No, go back** → sends you home. **Yes, open [App]** → lets you through

The pause resets each time you leave and re-open the app, so it shows every time — the friction is the feature.

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
    ├── stats/       # Insights and time reclaimed
    ├── achievements/# Progress path
    ├── nav/         # Compose navigation graph
    └── theme/       # Colors, Theme
```

---

## Setup

### Requirements

- Android Studio (latest stable)
- Android phone running Android 8.0+ (API 26+)
- A physical device. Emulators reproduce neither overlay behaviour nor the OEM battery
  managers that silently disable accessibility services, which is most of what can go
  wrong here

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
as the window changes, so the pause can be up before the app draws its first frame.

**Why not `isAccessibilityTool`?** Because it would be a lie. That flag marks services
built to assist users with disabilities; Play policy explicitly excludes monitoring apps,
and claiming it falsely risks losing the developer account.

**Why the repository pattern?** The UI doesn't need to know if data comes from a database or an API. The repository decides. This makes screens simple and logic testable.

**Why Hilt?** Without dependency injection, every class creates its own dependencies, making testing hard and code tightly coupled. Hilt wires everything together at startup so classes just declare what they need.

---

## Roadmap

- [x] App icons and launch screen
- [x] Per-app custom pause duration
- [x] Stats screen (how many pauses, how many times you went back)
- [x] Achievement progress and time-saved insights
- [x] Widget showing daily pause count
- [ ] Play Store release

---

## License

MIT
