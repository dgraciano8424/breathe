# Codebase Structure

**Analysis Date:** 2026-06-15

## Directory Layout

```
breathe/                                  # Project root
├── app/                                  # Android application module
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # Permissions, activities, service, receiver declarations
│       │   ├── java/com/dgraciano/breathe/
│       │   │   ├── BreatheApp.kt         # @HiltAndroidApp application class
│       │   │   ├── MainActivity.kt       # Launcher activity; hosts main Compose nav graph
│       │   │   ├── data/
│       │   │   │   ├── db/               # Room database, DAOs
│       │   │   │   ├── model/            # Domain / Room entity data classes
│       │   │   │   ├── remote/           # Retrofit API interface and DTOs
│       │   │   │   └── repository/       # Repository classes (data access boundary)
│       │   │   ├── di/                   # Hilt @Module objects
│       │   │   ├── service/              # Background foreground service + receivers
│       │   │   └── ui/
│       │   │       ├── appselect/        # App picker screen
│       │   │       ├── home/             # Main dashboard screen
│       │   │       ├── nav/              # NavGraph and route constants
│       │   │       ├── onboarding/       # Permission grant screen
│       │   │       ├── pause/            # Interrupt overlay activity + screen
│       │   │       ├── stats/            # Usage insights screen
│       │   │       └── theme/            # MaterialTheme, colors
│       │   └── res/
│       │       ├── drawable/             # ic_notification.xml
│       │       ├── values/               # strings.xml, themes.xml
│       │       └── xml/                  # (empty currently)
│       ├── test/                         # JVM unit tests (no test files currently)
│       └── androidTest/                  # Instrumented tests (no test files currently)
├── gradle/
│   └── wrapper/                          # Gradle wrapper JAR and properties
├── build.gradle.kts                      # Root build file (plugin declarations only)
├── settings.gradle.kts                   # Module includes
├── gradle.properties                     # useAndroidX=true
├── gradlew / gradlew.bat                 # Gradle wrapper scripts
└── README.md                             # Project overview and setup guide
```

## Directory Purposes

**`app/src/main/java/com/dgraciano/breathe/data/db/`:**
- Purpose: Room database definition and DAO interfaces
- Contains: `BreatheDatabase.kt` (abstract RoomDatabase, migrations), `BlockedAppDao.kt`, `QuoteDao.kt`, `InterventionEventDao.kt`
- Key files: `BreatheDatabase.kt` — add new DAOs here and register entities in `@Database` annotation

**`app/src/main/java/com/dgraciano/breathe/data/model/`:**
- Purpose: Domain models and Room entities
- Contains: `BlockedApp.kt`, `InterventionEvent.kt`, `Quote.kt`, `AppStat.kt`
- Key files: `InterventionEvent.kt` — defines outcome and reason string constants used throughout the app

**`app/src/main/java/com/dgraciano/breathe/data/remote/`:**
- Purpose: Retrofit API interface and JSON DTOs
- Contains: `ZenQuotesApi.kt`, `QuoteDto.kt`
- Key files: Add new API interfaces here; DTOs stay here, domain models go in `data/model/`

**`app/src/main/java/com/dgraciano/breathe/data/repository/`:**
- Purpose: Single source of truth for data access; ViewModels depend only on this layer
- Contains: `AppRepository.kt`, `QuoteRepository.kt`, `StatsRepository.kt`
- Key files: All three are `@Singleton` concrete classes injected via Hilt

**`app/src/main/java/com/dgraciano/breathe/di/`:**
- Purpose: Hilt dependency injection modules; all installed in `SingletonComponent`
- Contains: `DatabaseModule.kt`, `NetworkModule.kt`, `SystemServiceModule.kt`
- Key files: `DatabaseModule.kt` — add new DAO `@Provides` functions here when adding a new Room entity

**`app/src/main/java/com/dgraciano/breathe/service/`:**
- Purpose: Background foreground service and Android component receivers
- Contains: `AppMonitorService.kt`, `ForegroundAppDetector.kt`, `BootReceiver.kt`
- Key files: `AppMonitorService.kt` — core polling loop; `BootReceiver.kt` — auto-start on reboot

**`app/src/main/java/com/dgraciano/breathe/ui/`:**
- Purpose: All Compose UI — screens, ViewModels, navigation, and theme
- Contains: One subdirectory per feature screen, plus `nav/` and `theme/`
- Key files: `nav/NavGraph.kt` — add new routes here; `theme/Color.kt` and `theme/Theme.kt` — all color tokens

**`app/src/main/java/com/dgraciano/breathe/ui/<feature>/`:**
- Purpose: Feature-scoped screen and ViewModel, co-located together
- Pattern: `<Feature>Screen.kt` (Composable) + `<Feature>ViewModel.kt` (HiltViewModel)
- Example: `ui/home/HomeScreen.kt` + `ui/home/HomeViewModel.kt`

## Key File Locations

**Entry Points:**
- `app/src/main/java/com/dgraciano/breathe/BreatheApp.kt`: Application class; Hilt root
- `app/src/main/java/com/dgraciano/breathe/MainActivity.kt`: Launcher activity and nav host
- `app/src/main/java/com/dgraciano/breathe/ui/pause/PauseActivity.kt`: Interrupt overlay activity
- `app/src/main/java/com/dgraciano/breathe/service/BootReceiver.kt`: Boot-triggered service restart

**Configuration:**
- `app/src/main/AndroidManifest.xml`: Permissions, activity/service/receiver declarations — must register new components here
- `app/build.gradle.kts`: SDK versions, Compose options, dependency declarations
- `settings.gradle.kts`: Module list
- `gradle.properties`: AndroidX flag

**Core Logic:**
- `app/src/main/java/com/dgraciano/breathe/service/AppMonitorService.kt`: 500ms polling loop and interrupt trigger
- `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt`: Schema version and migrations
- `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`: All Compose navigation routes

**Navigation Routes:**
- `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`: `Routes` object holds all route string constants (`onboarding`, `home`, `app_select`, `stats`)

**Testing:**
- `app/src/test/`: JVM unit test root (no tests currently written)
- `app/src/androidTest/`: Instrumented test root (no tests currently written)

## Naming Conventions

**Files:**
- Screen composables: `<Feature>Screen.kt` (e.g., `HomeScreen.kt`, `StatsScreen.kt`)
- ViewModels: `<Feature>ViewModel.kt` (e.g., `HomeViewModel.kt`, `PauseViewModel.kt`)
- Activities: `<Feature>Activity.kt` (e.g., `PauseActivity.kt`, `MainActivity.kt`)
- DAOs: `<Entity>Dao.kt` (e.g., `BlockedAppDao.kt`, `InterventionEventDao.kt`)
- Repositories: `<Domain>Repository.kt` (e.g., `AppRepository.kt`, `StatsRepository.kt`)
- Hilt modules: `<Concern>Module.kt` (e.g., `DatabaseModule.kt`, `NetworkModule.kt`)
- DTOs: `<Entity>Dto.kt` (e.g., `QuoteDto.kt`)

**Directories:**
- Feature UI packages: all lowercase, one word where possible (`home`, `stats`, `pause`, `appselect`, `onboarding`)
- Data packages: domain-noun based (`db`, `model`, `remote`, `repository`)
- DI package: `di` (flat, no sub-packages)

**Classes and functions:**
- Classes: `PascalCase`
- Functions: `camelCase`
- Constants: `SCREAMING_SNAKE_CASE` in companion objects (e.g., `OUTCOME_DECLINED`, `CHANNEL_ID`)
- Compose private helpers: `PascalCase` private functions within the same file (e.g., `TodaySummaryCard`, `StatCard`)

## Where to Add New Code

**New feature screen:**
1. Create `app/src/main/java/com/dgraciano/breathe/ui/<feature>/` directory
2. Add `<Feature>Screen.kt` (Composable) and `<Feature>ViewModel.kt` (`@HiltViewModel`) in that directory
3. Add a route constant to `Routes` object in `ui/nav/NavGraph.kt`
4. Add a `composable(Routes.<FEATURE>)` block in `BreatheNavGraph` in `NavGraph.kt`

**New Room entity:**
1. Add entity data class to `data/model/<Entity>.kt` with `@Entity` annotation
2. Add DAO interface to `data/db/<Entity>Dao.kt`
3. Register entity in `@Database(entities = [...])` array in `data/db/BreatheDatabase.kt`
4. Add abstract DAO accessor to `BreatheDatabase`
5. Write a new `Migration` if bumping schema version
6. Add `@Provides` function for the DAO in `di/DatabaseModule.kt`

**New repository:**
1. Add `<Domain>Repository.kt` to `data/repository/` as a `@Singleton @Inject constructor` class
2. It will be automatically provided by Hilt; no module changes needed

**New DI binding:**
1. If providing a framework/system service: add `@Provides` to `di/SystemServiceModule.kt`
2. If providing a network client: add `@Provides` to `di/NetworkModule.kt`
3. If providing a DB artifact: add `@Provides` to `di/DatabaseModule.kt`

**New background service or receiver:**
1. Add Kotlin class to `service/`
2. Register in `app/src/main/AndroidManifest.xml` under `<application>` tag

**Utilities / shared helpers:**
- No `util/` or `common/` package exists yet; add one at `app/src/main/java/com/dgraciano/breathe/util/` if shared logic emerges

## Special Directories

**`.planning/`:**
- Purpose: GSD planning documents (ARCHITECTURE.md, STACK.md, etc.)
- Generated: No (human/agent-written)
- Committed: Yes

**`.gradle/`:**
- Purpose: Gradle build cache
- Generated: Yes
- Committed: No (in `.gitignore`)

**`gradle/wrapper/`:**
- Purpose: Gradle wrapper binary and version specification
- Generated: Partially (JAR is binary)
- Committed: Yes

---

*Structure analysis: 2026-06-15*
