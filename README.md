# Breathe

An Android app that intercepts distracting app launches and shows a mindful pause — a breathing animation, a mental health quote, and a simple choice: keep going or go back.

Built as a free alternative to [One Sec](https://one-sec.app), using only public Android APIs.

---

## How it works

1. A foreground service polls `UsageStatsManager` every 500ms to detect which app is in the foreground
2. When a blocked app is detected, a full-screen pause screen launches on top of it
3. The screen shows a breathing animation, a randomized mental health quote (from [ZenQuotes API](https://zenquotes.io)), and two buttons
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
| Database | Room (blocked apps + quote cache) |
| Networking | Retrofit + OkHttp + ZenQuotes API |
| Background | Foreground Service + BroadcastReceiver (boot) |
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

- **Usage Access** (`PACKAGE_USAGE_STATS`) — Settings → Apps → Special App Access → Usage Access → Breathe → Allow

The onboarding screen walks you through this on first launch.

---

## Key concepts (for learning)

**Why a foreground service?** Android kills background processes aggressively to save battery. A foreground service stays alive but must show a persistent notification — Android's way of being transparent with the user.

**Why poll instead of listen?** Android doesn't provide a public event/callback for "app X just launched." `UsageStatsManager` is a pull API — you ask it "what happened recently?" on a timer.

**Why the repository pattern?** The UI doesn't need to know if data comes from a database or an API. The repository decides. This makes screens simple and logic testable.

**Why Hilt?** Without dependency injection, every class creates its own dependencies, making testing hard and code tightly coupled. Hilt wires everything together at startup so classes just declare what they need.

---

## Roadmap

- [ ] App icons and launch screen
- [ ] Per-app custom pause duration
- [ ] Stats screen (how many pauses, how many times you went back)
- [ ] Widget showing daily pause count
- [ ] Play Store release

---

## License

MIT
