# Technology Stack

**Analysis Date:** 2026-06-15

## Languages

**Primary:**
- Kotlin 1.9.24 - All application source code under `app/src/main/java/com/dgraciano/breathe/`

**Secondary:**
- XML - Android resource files (`app/src/main/res/`)

## Runtime

**Environment:**
- Android SDK 26 minimum (Android 8.0 Oreo), target SDK 34 (Android 14)
- JVM target: Java 17

**Package Manager:**
- Gradle 8.7 (via Gradle Wrapper at `gradle/wrapper/gradle-wrapper.properties`)
- Version catalog: `gradle/libs.versions.toml`
- Lockfile: Not present (no `gradle.lockfile`)

## Frameworks

**Core:**
- Jetpack Compose BOM 2024.06.00 - Declarative UI framework
  - `compose-ui`, `compose-material3`, `compose-activity`, `compose-navigation`, `compose-icons-extended`
- Android Gradle Plugin (AGP) 8.4.0 - Build tooling

**Dependency Injection:**
- Hilt 2.51.1 (`com.google.dagger:hilt-android`) - DI framework via `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`
- Hilt Navigation Compose 1.2.0 - ViewModel injection into Compose screens
- KSP 1.9.24-1.0.20 - Annotation processor for Hilt and Room

**Data Persistence:**
- Room 2.6.1 (`androidx.room`) - SQLite ORM; database defined at `app/src/main/java/com/dgraciano/breathe/data/db/BreatheDatabase.kt`

**Networking:**
- Retrofit 2.11.0 (`com.squareup.retrofit2:retrofit`) - HTTP client for REST API calls
- Retrofit Gson Converter 2.11.0 - JSON deserialization
- OkHttp 4.12.0 - HTTP client underlying Retrofit; logging interceptor enabled

**Background Work:**
- WorkManager 2.9.0 (`androidx.work:work-runtime-ktx`) - Listed as dependency; not yet wired to a Worker class in current source
- Kotlin Coroutines 1.8.1 (`kotlinx-coroutines-android`) - Async/suspend throughout services and repositories

**Navigation:**
- Navigation Compose 2.7.7 - Single-activity navigation graph at `app/src/main/java/com/dgraciano/breathe/ui/nav/NavGraph.kt`

**Testing:**
- JUnit 4.13.2 - Unit test runner
- Kotlin Coroutines Test 1.8.1 - Coroutine test utilities
- MockK 1.13.11 - Kotlin mocking library

## Key Dependencies

**Critical:**
- `com.google.dagger:hilt-android:2.51.1` - Entire DI graph depends on this; removing it requires rearchitecting injection across all ViewModels, repositories, services
- `androidx.room:room-runtime:2.6.1` - Primary data store; all local persistence flows through Room DAOs
- `com.squareup.retrofit2:retrofit:2.11.0` - Only HTTP client; used to fetch quotes from ZenQuotes API

**Infrastructure:**
- `androidx.activity:activity-compose:1.9.0` - Bridge between Activity lifecycle and Compose
- `androidx.compose.material:material-icons-extended` - Extended icon set for UI
- `com.squareup.okhttp3:logging-interceptor:4.12.0` - HTTP request/response logging (BASIC level; always-on, not gated by debug build type)

## Configuration

**Environment:**
- No `.env` files present
- No `BuildConfig` fields defined in `app/build.gradle.kts` (no API keys injected at build time)
- ZenQuotes base URL is hardcoded in `app/src/main/java/com/dgraciano/breathe/di/NetworkModule.kt`

**Build:**
- Root build script: `build.gradle.kts`
- App module build script: `app/build.gradle.kts`
- Version catalog: `gradle/libs.versions.toml` (single source of truth for all versions)
- ProGuard rules: `app/proguard-rules.pro` (minification enabled for release)
- Kotlin code style: `official` (set in `gradle.properties`)
- Compose compiler extension version: 1.5.14

**Key `gradle.properties` settings:**
- `android.useAndroidX=true`
- `android.enableJetifier=true`
- `android.nonTransitiveRClass=true`
- JVM heap: `-Xmx2048m`

## Platform Requirements

**Development:**
- JDK 17
- Android SDK with API 34 platform tools
- Gradle 8.7 (downloaded automatically by wrapper)

**Production:**
- Android 8.0+ (API 26+) devices
- `PACKAGE_USAGE_STATS` permission must be manually granted by the user in Settings
- Foreground service permission required for `AppMonitorService`
- `RECEIVE_BOOT_COMPLETED` to auto-start monitoring after device reboot

---

*Stack analysis: 2026-06-15*
