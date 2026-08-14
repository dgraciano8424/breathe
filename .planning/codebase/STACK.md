# Technology Stack

**Analysis Date:** 2026-06-15
**Revised:** 2026-08-14 — brought in line with the tree at `96eb5df`. The 2026-06-15
version described the project two architectures back: it predated both the overlay
rewrite and the accessibility rewrite, and every version number in it was stale.

## Languages

**Primary:**
- Kotlin 2.2.10 - All application source code under `app/src/main/java/com/dgraciano/breathe/`

**Secondary:**
- XML - Android resource files (`app/src/main/res/`)

## Runtime

**Environment:**
- Android SDK 26 minimum (Android 8.0 Oreo), compile and target SDK 36 (Android 16)
- JVM target: Java 17

**Package Manager:**
- Gradle 9.6.1 (via Gradle Wrapper at `gradle/wrapper/gradle-wrapper.properties`)
- Version catalog: `gradle/libs.versions.toml`
- Lockfile: Not present (no `gradle.lockfile`)

## Frameworks

**Core:**
- Jetpack Compose BOM 2024.06.00 - Declarative UI framework
  - `compose-ui`, `compose-material3`, `compose-activity`, `compose-navigation`, `compose-icons-extended`
- Android Gradle Plugin (AGP) 9.2.1 - Build tooling
- Kotlin Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`), version tracking Kotlin

**Dependency Injection:**
- Hilt 2.60.1 (`com.google.dagger:hilt-android`) - DI framework via `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`
- Hilt Navigation Compose 1.2.0 - ViewModel injection into Compose screens
- KSP 2.2.10-2.0.2 - Annotation processor for Hilt and Room

**Data Persistence:**
- Room 2.6.1 (`androidx.room`) - SQLite ORM; database defined at `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt`, schema version 5

**Networking:**
- **None.** Retrofit, OkHttp and Gson were removed in `90f7e30` along with the quote
  feature they served, and the `INTERNET` permission went with them. The app makes no
  network calls and cannot be made to without adding both a client and a permission.

**Lifecycle (used directly, not incidentally):**
- `lifecycle-runtime-ktx` and `lifecycle-viewmodel-ktx` 2.8.2, plus `savedstate-ktx` 1.2.1
- These are direct dependencies because `PauseOverlayHost` draws Compose content in a
  `WindowManager` overlay and has to supply the `ViewTree` lifecycle, ViewModelStore and
  SavedStateRegistry owners that a `ComposeView` would otherwise inherit from an Activity.
  Removing them breaks the overlay, not just a convenience API.

**Background Work:**
- Kotlin Coroutines 1.8.1 (`kotlinx-coroutines-android`) - Async/suspend throughout the
  service and repositories
- No WorkManager. It was listed as a dependency but never wired to a Worker, and was
  removed.
- No foreground service, and no `WorkManager`-style scheduling: the app's only long-lived
  component is an AccessibilityService, which the system binds and rebinds itself.

**Navigation:**
- Navigation Compose 2.7.7 - Single-activity navigation graph at `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`

**Testing:**
- JUnit 4.13.2 - Unit test runner
- Kotlin Coroutines Test 1.8.1 - Coroutine test utilities
- MockK 1.13.11 - Kotlin mocking library
- No Compose UI test dependency and no instrumented tests. See `TESTING.md`.

## Key Dependencies

**Critical:**
- `com.google.dagger:hilt-android:2.60.1` - Entire DI graph depends on this; removing it requires rearchitecting injection across all ViewModels, repositories, and the accessibility service
- `androidx.room:room-runtime:2.6.1` - The only data store; all persistence flows through Room DAOs
- `androidx.lifecycle:*` / `androidx.savedstate` - Load-bearing for the overlay, as above

**Infrastructure:**
- `androidx.activity:activity-compose:1.9.0` - Bridge between Activity lifecycle and Compose
- `androidx.compose.material:material-icons-extended` - Extended icon set for UI

## Configuration

**Environment:**
- No `.env` files present
- No API keys, base URLs, or secrets of any kind — there is no external service to
  configure

**Build:**
- Root build script: `build.gradle.kts`
- App module build script: `app/build.gradle.kts`
- Version catalog: `gradle/libs.versions.toml` (single source of truth for all versions)
- ProGuard rules: `app/proguard-rules.pro`; `isMinifyEnabled` and `isShrinkResources` both on for release
- Release signing read from a gitignored `keystore.properties`; absent, the release build still compiles and comes out unsigned
- Kotlin code style: `official` (set in `gradle.properties`)

`buildFeatures` now enables `compose` only. The `buildConfig = true` flag was removed on
2026-08-14: it existed solely to gate network logging to debug builds, and both the
logging and the network stack are gone. `BuildConfig.java` is confirmed no longer
generated, and no source references it.

**Key `gradle.properties` settings:**
- `org.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8`
- `android.useAndroidX=true`
- `android.nonTransitiveRClass=true`
- `kotlin.code.style=official`
- No `android.enableJetifier` — it is not set, and the earlier claim that it was `true` was wrong

## Platform Requirements

**Development:**
- JDK 17. The Android Studio JBR at `C:\Program Files\Android\Android Studio\jbr` works;
  a bare shell typically has no `java` on `PATH`, so `JAVA_HOME` must be set explicitly
- Android SDK with the API 36 platform
- Gradle 9.6.1 (downloaded automatically by wrapper)
- A physical device for any meaningful verification — see `TESTING.md`

**Production:**
- Android 8.0+ (API 26+) devices
- Accessibility access must be granted manually by the user in Settings — required for
  detection
- `SYSTEM_ALERT_WINDOW` must be granted manually — required to draw the pause
- `PACKAGE_USAGE_STATS` optional; enriches statistics only

---

*Stack analysis: 2026-06-15. Revised 2026-08-14 against `96eb5df`.*
