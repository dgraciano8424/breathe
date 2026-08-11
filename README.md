# Breathe

An Android app that intercepts distracting app launches and shows a mindful pause — a breathing animation, a grounding tip, and a simple choice: keep going or go back.

Built as a free alternative to [One Sec](https://one-sec.app), using only public Android APIs.

---

## How it works

1. An `AccessibilityService` listens for window-state changes to detect which app has come to the front
2. When a monitored app is detected, a full-screen overlay is drawn on top of it
3. The screen shows a breathing animation, a grounding tip, an optional "why am I opening this?" prompt, and two buttons
4. **I'll do something else** → sends you home. **Continue to [App]** → lets you through, after the pause elapses

"Continue" stays disabled for the length of your chosen pause (8 seconds by default), and the back button counts as turning back rather than a way around it. The friction is the feature.

Choosing to continue grants a five-minute window before you are asked again, so the app does not nag you while you are deliberately using something.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Repository pattern |
| DI | Hilt |
| Database | Room (monitored apps + intervention history) |
| Preferences | DataStore |
| Networking | None — the app is fully offline |
| Background | AccessibilityService (system-bound) |
| Build | Gradle 8.7 + Kotlin DSL |

---

## Project structure

```
app/src/main/java/com/dgraciano/breathe/
├── data/
│   ├── db/          # Room DAOs + Database
│   ├── model/       # BlockedApp, Quote entities
│   ├── remote/      # Retrofit API + DTOs
│   └── repository/  # AppRepository, QuoteRepository
├── di/              # Hilt modules (DB, Network, SystemService)
├── service/         # AppMonitorService, ForegroundAppDetector, BootReceiver
└── ui/
    ├── onboarding/  # Permission setup screen
    ├── home/        # Monitored apps list
    ├── appselect/   # App picker
    ├── pause/       # The breathing screen (PauseActivity + PauseScreen)
    ├── nav/         # Compose navigation graph
    └── theme/       # Colors, Theme
```

---

## Setup

### Requirements

- Android Studio (latest stable)
- Android phone running Android 8.0+ (API 26+)
- A physical device — `UsageStatsManager` is unreliable on emulators

### Run locally

```bash
git clone https://github.com/dgraciano8424/breathe
```

Open the `breathe` folder in Android Studio. Wait for Gradle sync to complete, then hit Run.

### Permissions

The app requires one special permission that must be granted manually:

- **Accessibility access** — required. Detects which app is opening. Settings → Accessibility → Breathe
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — required. Draws the pause on top of the app you are opening
- **Usage access** (`PACKAGE_USAGE_STATS`) — optional. Only adds time-spent figures to your stats

The onboarding screen walks you through these on first launch. See [PRIVACY.md](PRIVACY.md) for exactly what is read and stored — nothing leaves your device.

---

## Key concepts (for learning)

**Why an AccessibilityService?** Android has no general public callback for "app X just launched." The two options are polling `UsageStatsManager` on a timer, or listening to accessibility window events. This app started with polling and moved to accessibility events: they arrive as the app comes to the front rather than up to half a second later, and they need no always-running foreground service. It is also what every comparable app ships.

**Why an overlay rather than an Activity?** Launching an Activity from the background is heavily restricted on modern Android. Drawing into a `TYPE_APPLICATION_OVERLAY` window sidesteps that entirely — and it means the "display over other apps" permission is one the app genuinely uses.

**Why the repository pattern?** The UI doesn't need to know if data comes from a database or an API. The repository decides. This makes screens simple and logic testable.

**Why Hilt?** Without dependency injection, every class creates its own dependencies, making testing hard and code tightly coupled. Hilt wires everything together at startup so classes just declare what they need.

---

## Roadmap

- [x] App icons and launch screen
- [x] Stats screen (how many pauses, how many times you went back)
- [x] Achievement progress and time-saved insights
- [x] Configurable pause length
- [ ] Per-app custom pause duration
- [ ] Scheduled blocking hours
- [ ] Widget showing daily pause count
- [ ] Translations
- [ ] Play Store release

---

## License

MIT
