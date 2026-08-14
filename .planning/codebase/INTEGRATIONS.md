# External Integrations

**Analysis Date:** 2026-06-15
**Revised:** 2026-08-14 — brought in line with the tree at `96eb5df`. The 2026-06-15
version documented a ZenQuotes integration in detail; that integration, and every file
path it named, no longer exist.

## APIs & External Services

**None.**

The app has no external integrations. It does not declare the `INTERNET` permission, so
it is structurally incapable of contacting any service.

This is recent and deliberate. Until `90f7e30` the app fetched motivational quotes from
ZenQuotes over Retrofit and cached them in a Room `quotes` table. The quote was then
passed into `PauseScreen` as a parameter that was never rendered — the entire network
stack existed to populate a value the user never saw. Both copies of the privacy policy
had disclosed ZenQuotes as a third party the app contacts, which was true about the
request and false about its purpose.

The client, the DTO, the DAO, the entity, the repository, `NetworkModule`, the Retrofit /
OkHttp / Gson dependencies, the ProGuard keep rules and the `INTERNET` permission were
all removed together. `MIGRATION_4_5` drops the `quotes` table.

**If an integration is ever added back**, these have to move together or the app ships a
lie: the manifest permission, `PRIVACY.md`, `docs/index.html` (the published policy Play
Console links to), the Data safety answers in `RELEASE.md`, and the "no internet access"
claims in `STORE_LISTING.md`.

## Data Storage

**Databases:**
- Room (SQLite) — local on-device database
  - Database class: `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt`
  - Database file: `breathe.db` (stored in app's private data directory)
  - Current schema version: **5**
  - Migrations, all defined inline in `BreatheDatabase`:
    - `MIGRATION_1_2` — adds the `intervention_events` table
    - `MIGRATION_2_3` — adds `minutesSaved` to `intervention_events`
    - `MIGRATION_3_4` — adds `pauseSeconds` to `blocked_apps`, defaulted so existing rows keep current behaviour
    - `MIGRATION_4_5` — drops the `quotes` table
  - Tables:
    - `blocked_apps` — user-selected apps to intercept, with per-app pause length (`app/src/main/java/com/dgraciano/breathe/data/model/BlockedApp.kt`)
    - `intervention_events` — log of every interception with outcome, optional reason and minutes saved (`app/src/main/java/com/dgraciano/breathe/data/model/InterventionEvent.kt`)
  - DAOs:
    - `app/src/main/java/com/dgraciano/breathe/data/db/BlockedAppDao.kt`
    - `app/src/main/java/com/dgraciano/breathe/data/db/InterventionEventDao.kt`
  - Connection: provided by Hilt via `app/src/main/java/com/dgraciano/breathe/di/DatabaseModule.kt`
  - Excluded from cloud backup and device-to-device transfer (`allowBackup="false"`, plus explicit `backup_rules.xml` and `data_extraction_rules.xml`)

**File Storage:**
- Local filesystem only — no cloud file storage

**Caching:**
- In-memory only: the accessibility service mirrors the blocked-package set so the event
  path never touches the database, and `SessionTimeHelper` caches per-app session
  averages with a 6-hour TTL
- No HTTP caching, because there is no HTTP

## Authentication & Identity

**Auth Provider:**
- None — no user accounts, no authentication
- No Firebase, no OAuth, no session tokens

## Android System APIs

**Accessibility (primary detection mechanism):**
- `android.accessibilityservice.AccessibilityService` — detects which app has come to the
  foreground, replacing the `UsageStatsManager` poll loop
  - Implementation: `app/src/main/java/com/dgraciano/breathe/service/BreatheAccessibilityService.kt`
  - Config: `app/src/main/res/xml/accessibility_service_config.xml`
  - Scoped to `typeWindowStateChanged` with `canRetrieveWindowContent="false"` — it can
    see which app opened, not what is inside it
  - Deliberately **not** `isAccessibilityTool`: Play policy excludes monitoring apps, and
    claiming it falsely risks developer-account termination
  - Bound and rebound by the system, including across reboot, so no boot receiver
  - Requires a Play Console declaration and an in-app prominent disclosure with
    affirmative consent, both of which exist

**Overlay window:**
- `WindowManager` / `TYPE_APPLICATION_OVERLAY` — draws the pause over the app being opened
  - Implementation: `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseOverlayHost.kt`
  - Permission: `SYSTEM_ALERT_WINDOW`, granted manually by the user
  - `PauseActivity` remains as a fallback when overlay permission is missing

**Usage Stats (optional, statistics only):**
- `android.app.usage.UsageStatsManager` — no longer used for detection. It now only
  estimates time spent per app for the stats screens
  - Permission: `android.permission.PACKAGE_USAGE_STATS` (manual grant; app works without it)
  - Implementation: `app/src/main/java/com/dgraciano/breathe/service/SessionTimeHelper.kt`
  - DI wiring: `app/src/main/java/com/dgraciano/breathe/di/SystemServiceModule.kt`

**Foreground Service / Boot Receiver:**
- **Neither exists.** `AppMonitorService` and `BootReceiver` were removed with the
  accessibility rewrite, along with the `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_SPECIAL_USE` and `RECEIVE_BOOT_COMPLETED` permissions, the
  `specialUse` Play justification and its demo-video requirement.
- Note the side effect: the persistent notification is gone, and with it the user's only
  ambient signal that monitoring is running. See `CONCERNS.md`.

**App Widget:**
- `PauseCountWidget` — RemoteViews home-screen widget showing today's pause count and time
  won back (`app/src/main/java/com/dgraciano/breathe/widget/PauseCountWidget.kt`),
  instantiated by the system from the manifest name

**Vibration:**
- `android.permission.VIBRATE` declared in manifest; used for haptic feedback

**Installed Packages Query:**
- `PackageManager`, scoped by a `<queries>` launcher-intent filter rather than
  `QUERY_ALL_PACKAGES`, to list installable apps for the picker at
  `app/src/main/java/com/dgraciano/breathe/ui/appselect/`

## Monitoring & Observability

**Error Tracking:**
- None — no Sentry, Crashlytics, or similar SDK. Adding one would introduce the first
  network egress the app has ever had and would falsify the privacy policy, the Data
  safety answers and the store listing simultaneously.

**Logs:**
- Android `Log` (Logcat) only, not abstracted. `Log.d`/`Log.v` are stripped from release
  builds by ProGuard; warnings and errors are kept.

## CI/CD & Deployment

**Hosting:**
- GitHub Pages serves the privacy policy from `/docs` on `main`
  (https://dgraciano8424.github.io/breathe/). No app distribution is configured.

**CI Pipeline:**
- None — no GitHub Actions or other CI configuration present. Every build and test run
  documented in `APP_AUDIT.md` was run by hand.

## Environment Configuration

**Required env vars:**
- None
- `JAVA_HOME` must point at a JDK 17 to build, which is a local toolchain requirement
  rather than app configuration

**Secrets location:**
- Release signing only: a gitignored `keystore.properties` at the repo root, described in
  `RELEASE.md`. No other credentials exist in this project.

## Webhooks & Callbacks

**Incoming:**
- None

**Outgoing:**
- None

---

*Integration audit: 2026-06-15. Revised 2026-08-14 against `96eb5df`.*
