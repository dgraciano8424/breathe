# External Integrations

**Analysis Date:** 2026-06-15

## APIs & External Services

**Motivational Quotes:**
- ZenQuotes API (`https://zenquotes.io/api/`) - Fetches a list of motivational quotes displayed during the pause/intervention screen
  - SDK/Client: Retrofit 2.11.0 + OkHttp 4.12.0
  - Interface: `app/src/main/java/com/dgraciano/breathe/data/remote/ZenQuotesApi.kt`
  - DTO: `app/src/main/java/com/dgraciano/breathe/data/remote/QuoteDto.kt`
  - DI wiring: `app/src/main/java/com/dgraciano/breathe/di/NetworkModule.kt`
  - Auth: None required (public API)
  - Endpoint used: `GET /quotes` — returns `[{"q": "...", "a": "...", "h": "..."}]`
  - Caching strategy: Quotes fetched once and stored in Room (`quotes` table); re-fetched only when table is empty (see `app/src/main/java/com/dgraciano/breathe/data/repository/QuoteRepository.kt`)

## Data Storage

**Databases:**
- Room (SQLite) — local on-device database
  - Database class: `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt`
  - Database file: `breathe.db` (stored in app's private data directory)
  - Current schema version: 2
  - Migration: `MIGRATION_1_2` adds `intervention_events` table (defined inline in `BreatheDatabase`)
  - Tables:
    - `blocked_apps` — stores user-selected apps to intercept (`app/src/main/java/com/dgraciano/breathe/data/model/BlockedApp.kt`)
    - `quotes` — cached quotes from ZenQuotes API (`app/src/main/java/com/dgraciano/breathe/data/model/Quote.kt`)
    - `intervention_events` — audit log of every interception with outcome and optional reason (`app/src/main/java/com/dgraciano/breathe/data/model/InterventionEvent.kt`)
  - DAOs:
    - `app/src/main/java/com/dgraciano/breathe/data/db/BlockedAppDao.kt`
    - `app/src/main/java/com/dgraciano/breathe/data/db/QuoteDao.kt`
    - `app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt`
  - Connection: provided by Hilt via `app/src/main/java/com/dgraciano/breathe/di/DatabaseModule.kt`

**File Storage:**
- Local filesystem only — no cloud file storage

**Caching:**
- Quotes cached in Room `quotes` table (see above)
- No HTTP-level caching (no OkHttp cache configured)

## Authentication & Identity

**Auth Provider:**
- None — no user accounts, no authentication
- No Firebase, no OAuth, no session tokens

## Android System APIs

**Usage Stats (Android OS):**
- `android.app.usage.UsageStatsManager` — used to detect which app is currently in the foreground
  - Permission: `android.permission.PACKAGE_USAGE_STATS` (must be granted manually by user)
  - Implementation: `app/src/main/java/com/dgraciano/breathe/service/ForegroundAppDetector.kt`
  - DI wiring: `app/src/main/java/com/dgraciano/breathe/di/SystemServiceModule.kt`
  - Query window: last 10 seconds, polled every 500ms from `AppMonitorService`

**Foreground Service:**
- `AppMonitorService` runs as a foreground service (`foregroundServiceType="specialUse"`) to continuously monitor the active app
  - Implementation: `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt`
  - Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`
  - Notification channel: `breathe_monitor` (importance: MIN — silent)

**Boot Receiver:**
- `BootReceiver` listens for `ACTION_BOOT_COMPLETED` to restart `AppMonitorService` after device reboot
  - Implementation: `app/src/main/java/com/dgraciano/breathe/service/BootReceiver.kt`
  - Permission: `RECEIVE_BOOT_COMPLETED`

**Vibration:**
- `android.permission.VIBRATE` declared in manifest; used during pause/breathing interaction

**Installed Packages Query:**
- `PackageManager` used (within the Android system) to list installed apps for the app-selection UI at `app/src/main/java/com/dgraciano/breathe/ui/appselect/`

## Monitoring & Observability

**Error Tracking:**
- None — no Sentry, Crashlytics, or similar SDK integrated

**Logs:**
- OkHttp `HttpLoggingInterceptor` at `BASIC` level (always-on, not conditioned on `BuildConfig.DEBUG`)
- Android `Log` (Logcat) — standard Android logging, not abstracted

## CI/CD & Deployment

**Hosting:**
- Not configured — no Play Store publishing scripts, no Firebase App Distribution

**CI Pipeline:**
- None — no GitHub Actions, Bitrise, or other CI configuration files present

## Environment Configuration

**Required env vars:**
- None — no environment variables required; the ZenQuotes base URL is hardcoded in `NetworkModule.kt`

**Secrets location:**
- No secrets — ZenQuotes API requires no API key; no other credentials exist in this project

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None (ZenQuotes is a one-way pull, not a push/webhook integration)

---

*Integration audit: 2026-06-15*
