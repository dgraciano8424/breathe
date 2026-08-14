# Codebase Structure

**Analysis Date:** 2026-06-15
**Revised:** 2026-08-14 — brought in line with the tree at `96eb5df`. The 2026-06-15
version predated the achievements, widget, overlay and accessibility work, and described
a `remote/` package and three service classes that no longer exist.

## Directory Layout

```
breathe/                                  # Project root
├── app/                                  # Android application module
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Permissions, activities, accessibility service, widget receiver
│       │   ├── java/com/dgraciano/breathe/
│       │   │   ├── BreatheApp.kt         # @HiltAndroidApp application class
│       │   │   ├── MainActivity.kt       # Launcher activity; hosts main Compose nav graph
│       │   │   ├── data/
│       │   │   │   ├── db/               # Room database, DAOs
│       │   │   │   ├── model/            # Domain / Room entity data classes
│       │   │   │   └── repository/       # Repository classes (data access boundary)
│       │   │   ├── di/                   # Hilt @Module objects
│       │   │   ├── service/              # Accessibility service + its collaborators
│       │   │   ├── widget/               # Home-screen app widget
│       │   │   └── ui/
│       │   │       ├── achievements/     # Progress path screen
│       │   │       ├── appselect/        # App picker screen
│       │   │       ├── components/       # Shared composables (background, mascot, confetti, reduced motion)
│       │   │       ├── home/             # Main dashboard screen
│       │   │       ├── nav/              # NavGraph and route constants
│       │   │       ├── onboarding/       # Permission grant screen
│       │   │       ├── pause/            # The pause: overlay host, fallback activity, screen, ViewModel
│       │   │       ├── stats/            # Usage insights screen
│       │   │       └── theme/            # MaterialTheme, colors
│       │   └── res/
│       │       ├── drawable/             # Launcher art, widget background, ic_notification (orphaned)
│       │       ├── layout/               # Widget RemoteViews layout
│       │       ├── mipmap-anydpi-v26/    # Adaptive launcher icon
│       │       ├── values/               # strings.xml, themes.xml
│       │       └── xml/                  # accessibility_service_config, widget info, backup rules
│       ├── test/                         # JVM unit tests — 43 tests across 4 classes
│       └── androidTest/                  # Instrumented tests — still empty
├── .planning/codebase/                   # These documents
├── docs/                                 # Published privacy policy (GitHub Pages, /docs on main)
├── gradle/
│   ├── libs.versions.toml                # Version catalog
│   └── wrapper/                          # Gradle wrapper JAR and properties
├── APP_AUDIT.md                          # Standing audit: blockers, corrections, verification status
├── RELEASE.md                            # Play submission checklist and device verification
├── STORE_LISTING.md                      # Play listing copy
├── PRIVACY.md                            # Privacy policy source (must match docs/index.html)
├── build.gradle.kts                      # Root build file (plugin declarations only)
├── settings.gradle.kts                   # Module includes
├── gradle.properties
├── gradlew / gradlew.bat                 # Gradle wrapper scripts
└── README.md                             # Project overview and setup guide
```

## Directory Purposes

**`app/src/main/java/com/dgraciano/breathe/data/db/`:**
- Purpose: Room database definition and DAO interfaces
- Contains: `BreatheDatabase.kt` (abstract RoomDatabase, migrations 1→5), `BlockedAppDao.kt`, `InterventionEventDao.kt`
- Key files: `BreatheDatabase.kt` — add new DAOs here and register entities in the `@Database` annotation

**`app/src/main/java/com/dgraciano/breathe/data/model/`:**
- Purpose: Domain models and Room entities
- Contains: `BlockedApp.kt`, `InterventionEvent.kt`, `AppStat.kt`, `Achievement.kt`
- Key files: `InterventionEvent.kt` — defines outcome and reason string constants used throughout the app; `BlockedApp.kt` — holds `DEFAULT_PAUSE_SECONDS` and the per-app pause length

**`app/src/main/java/com/dgraciano/breathe/data/repository/`:**
- Purpose: Single source of truth for data access; ViewModels depend only on this layer
- Contains: `AppRepository.kt`, `StatsRepository.kt`, `AchievementRepository.kt`, `MentalHealthTipsRepository.kt`
- Key files: all are `@Singleton` concrete classes injected via Hilt. `MentalHealthTipsRepository` supplies the grounding tip shown during a pause — it is local data, not a network call

**`app/src/main/java/com/dgraciano/breathe/di/`:**
- Purpose: Hilt dependency injection modules; all installed in `SingletonComponent`
- Contains: `DatabaseModule.kt`, `SystemServiceModule.kt`, `CoroutinesModule.kt`
- Key files: `DatabaseModule.kt` — add new DAO `@Provides` functions here when adding a Room entity. `CoroutinesModule.kt` provides the `@ApplicationScope` that lets pause-outcome writes survive the pause screen being torn down
- There is no `NetworkModule` — it was deleted with the network stack

**`app/src/main/java/com/dgraciano/breathe/service/`:**
- Purpose: Launch detection and its supporting state
- Contains: `BreatheAccessibilityService.kt`, `SessionApprovalStore.kt`, `SessionTimeHelper.kt`
- Key files: `BreatheAccessibilityService.kt` — receives window-state events, mirrors the blocked set in memory, decides whether to show the pause. `SessionApprovalStore` holds per-visit approvals so a "continue" is not re-prompted until the user leaves
- There is no foreground service and no boot receiver; the system binds the accessibility service, including after reboot

**`app/src/main/java/com/dgraciano/breathe/widget/`:**
- Purpose: Home-screen widget showing today's pause count and time won back
- Contains: `PauseCountWidget.kt` (the `AppWidgetProvider`), `WidgetRefresher.kt`
- Key files: `PauseCountWidget` is instantiated by the system from its manifest name, so it needs a ProGuard keep rule

**`app/src/main/java/com/dgraciano/breathe/ui/`:**
- Purpose: All Compose UI — screens, ViewModels, navigation, shared components, and theme
- Contains: one subdirectory per feature screen, plus `components/`, `nav/` and `theme/`
- Key files: `nav/NavGraph.kt` — add new routes here; `theme/Color.kt` and `theme/Theme.kt` — all colour tokens

**`app/src/main/java/com/dgraciano/breathe/ui/pause/`:**
- Purpose: The pause itself, which has two hosts for one screen
- `PauseOverlayHost.kt` — the primary path. Draws `PauseScreen` in a `TYPE_APPLICATION_OVERLAY` window, supplying the lifecycle, ViewModelStore and SavedStateRegistry owners a `ComposeView` would normally inherit from an Activity
- `PauseActivity.kt` — fallback when overlay permission is missing
- `PauseScreen.kt` — the composable both hosts render; `PauseViewModel.kt` — shared state and outcome recording

**`app/src/main/java/com/dgraciano/breathe/ui/<feature>/`:**
- Purpose: Feature-scoped screen and ViewModel, co-located together
- Pattern: `<Feature>Screen.kt` (Composable) + `<Feature>ViewModel.kt` (HiltViewModel)
- Example: `ui/home/HomeScreen.kt` + `ui/home/HomeViewModel.kt`

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/dgraciano/breathe/BreatheApp.kt`: Application class; Hilt root
- `app/src/main/java/com/dgraciano/breathe/MainActivity.kt`: Launcher activity and nav host
- `app/src/main/java/com/dgraciano/breathe/service/BreatheAccessibilityService.kt`: System-bound entry point for detection — not started by the app
- `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt`: Fallback pause host
- `app/src/main/java/com/dgraciano/breathe/widget/PauseCountWidget.kt`: System-instantiated widget provider

**Configuration:**
- `app/src/main/AndroidManifest.xml`: Permissions, activity/service/receiver declarations — must register new components here
- `app/src/main/res/xml/accessibility_service_config.xml`: Event scope and content-retrieval settings for the service; the file that backs the privacy claims in `PRIVACY.md` and `STORE_LISTING.md`
- `app/build.gradle.kts`: SDK versions, signing, Compose options, dependency declarations
- `gradle/libs.versions.toml`: All dependency versions
- `app/proguard-rules.pro`: Keep rules for reflectively instantiated classes

**Core Logic:**
- `app/src/main/java/com/dgraciano/breathe/service/BreatheAccessibilityService.kt`: Detection and the decision to intervene
- `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseOverlayHost.kt`: How the pause is drawn
- `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt`: Schema version and migrations
- `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`: All Compose navigation routes

**Navigation Routes:**
- `Routes` in `ui/nav/NavGraph.kt`: `onboarding`, `home`, `app_select`, `stats`, `achievements`

**Testing:**
- `app/src/test/`: JVM unit test root — `AppRepositoryTest`, `StatsRepositoryTest`, `SessionTimeHelperTest`, `PauseViewModelTest`
- `app/src/androidTest/`: Instrumented test root — still empty

## Naming Conventions

**Files:**
- Screen composables: `<Feature>Screen.kt` (e.g., `HomeScreen.kt`, `StatsScreen.kt`)
- ViewModels: `<Feature>ViewModel.kt` (e.g., `HomeViewModel.kt`, `PauseViewModel.kt`)
- Activities: `<Feature>Activity.kt` (e.g., `PauseActivity.kt`, `MainActivity.kt`)
- DAOs: `<Entity>Dao.kt` (e.g., `BlockedAppDao.kt`, `InterventionEventDao.kt`)
- Repositories: `<Domain>Repository.kt` (e.g., `AppRepository.kt`, `StatsRepository.kt`)
- Hilt modules: `<Concern>Module.kt` (e.g., `DatabaseModule.kt`, `CoroutinesModule.kt`)

**Directories:**
- Feature UI packages: all lowercase, one word where possible (`home`, `stats`, `pause`, `appselect`, `onboarding`, `achievements`, `components`)
- Data packages: domain-noun based (`db`, `model`, `repository`)
- DI package: `di` (flat, no sub-packages)

**Classes and functions:**
- Classes: `PascalCase`
- Functions: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE` in companion objects (e.g., `OUTCOME_DECLINED`, `DEFAULT_PAUSE_SECONDS`)
- Compose private helpers: `PascalCase` private functions within the same file (e.g., `TodaySummaryCard`, `StatCard`)

## Where to Add New Code

**New feature screen:**
1. Create `app/src/main/java/com/dgraciano/breathe/ui/<feature>/` directory
2. Add `<Feature>Screen.kt` (Composable) and `<Feature>ViewModel.kt` (`@HiltViewModel`) in that directory
3. Add a route constant to the `Routes` object in `ui/nav/NavGraph.kt`
4. Add a `composable(Routes.<FEATURE>)` block in `BreatheNavGraph` in `NavGraph.kt`

**New Room entity:**
1. Add entity data class to `data/model/<Entity>.kt` with `@Entity` annotation
2. Add DAO interface to `data/db/<Entity>Dao.kt`
3. Register entity in `@Database(entities = [...])` array in `data/db/BreatheDatabase.kt`
4. Add abstract DAO accessor to `BreatheDatabase`
5. Bump the schema version and write a `Migration` — note the app ships with real installs, so a destructive fallback is not acceptable
6. Add a `@Provides` function for the DAO in `di/DatabaseModule.kt`

**New repository:**
1. Add `<Domain>Repository.kt` to `data/repository/` as a `@Singleton @Inject constructor` class
2. It will be automatically provided by Hilt; no module changes needed

**New DI binding:**
1. If providing a framework/system service: add `@Provides` to `di/SystemServiceModule.kt`
2. If providing a DB artifact: add `@Provides` to `di/DatabaseModule.kt`
3. If providing a coroutine scope or dispatcher: `di/CoroutinesModule.kt`
4. There is no network module, and adding one is a policy decision as much as a technical one — see `INTEGRATIONS.md`

**New system-instantiated component (service, receiver, widget):**
1. Add the Kotlin class to `service/` or `widget/`
2. Register it in `app/src/main/AndroidManifest.xml` under the `<application>` tag
3. **Add a ProGuard keep rule** in `app/proguard-rules.pro`. The system resolves these by name, so R8 renaming one breaks it in release builds only — a failure mode that debug testing cannot catch

**Utilities / shared helpers:**
- Shared Compose helpers live in `ui/components/`
- There is no `util/` package; add one at `app/src/main/java/com/dgraciano/breathe/util/` if non-UI shared logic emerges

## Special Directories

**`.planning/`:**
- Purpose: Planning documents (ARCHITECTURE.md, STACK.md, etc.)
- Generated: No (human/agent-written)
- Committed: Yes
- Caveat: these documents have drifted badly from the code twice. Treat a claim here as a lead, and the source as the answer

**`docs/`:**
- Purpose: The published privacy policy, served by GitHub Pages from `/docs` on `main`
- Committed: Yes. `docs/index.html` and `PRIVACY.md` must be edited together

**`.gradle/`, `build/`:**
- Purpose: Gradle build cache and outputs
- Generated: Yes
- Committed: No (in `.gitignore`)

**`gradle/wrapper/`:**
- Purpose: Gradle wrapper binary and version specification
- Generated: Partially (JAR is binary)
- Committed: Yes

---

*Structure analysis: 2026-06-15. Revised 2026-08-14 against `96eb5df`.*
