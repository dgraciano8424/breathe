# Testing Patterns

**Analysis Date:** 2026-06-15

## Test Framework

**Runner:**
- JUnit 4 (`junit:junit:4.13.2`) — configured in `app/build.gradle.kts`
- Android instrumentation runner: `androidx.test.runner.AndroidJUnitRunner` (declared in `defaultConfig`)
- Config: no separate `junit4.xml` or `junit-platform.properties` — standard Android Gradle test setup

**Assertion Library:**
- JUnit 4 assertions (bundled with JUnit 4)
- MockK (`io.mockk:mockk:1.13.11`) for mocking

**Additional testing utilities:**
- `kotlinx-coroutines-test:1.8.1` — for testing suspend functions and coroutine flows

**Run Commands:**
```bash
./gradlew test                  # Run unit tests (JVM)
./gradlew connectedAndroidTest  # Run instrumented tests (device/emulator)
./gradlew testDebugUnitTest     # Run debug unit tests only
```

## Current Test Coverage State

**There are no test files in this project.** Both test source sets are empty directories:

- `app/src/test/java/com/dgraciano/breathe/` — empty (JVM unit tests)
- `app/src/androidTest/java/com/dgraciano/breathe/` — empty (instrumented tests)

The testing dependencies (JUnit 4, coroutines-test, MockK) are declared in `app/build.gradle.kts` but no tests have been written yet. The infrastructure is fully set up and ready for test authoring.

## Intended Testing Architecture (from declared dependencies)

Based on the declared test dependencies, the intended testing stack is:

| Concern | Tool |
|---|---|
| Test runner | JUnit 4 |
| Mocking | MockK |
| Coroutines | `kotlinx-coroutines-test` |
| Instrumented | `AndroidJUnitRunner` |

## Recommended Test Structure

Given the MVVM + Hilt + Room + Coroutines architecture, the following structure should be used when tests are added:

```
app/src/test/java/com/dgraciano/breathe/
├── data/
│   └── repository/
│       ├── AppRepositoryTest.kt
│       ├── QuoteRepositoryTest.kt
│       └── StatsRepositoryTest.kt
├── service/
│   └── ForegroundAppDetectorTest.kt
└── ui/
    ├── home/
    │   └── HomeViewModelTest.kt
    ├── pause/
    │   └── PauseViewModelTest.kt
    └── stats/
        └── StatsViewModelTest.kt

app/src/androidTest/java/com/dgraciano/breathe/
└── data/
    └── db/
        ├── BlockedAppDaoTest.kt
        └── InterventionEventDaoTest.kt
```

## Recommended Test Patterns

### ViewModel Unit Tests (with MockK + coroutines-test)

ViewModels should be tested by mocking their repository dependencies with MockK and using `TestCoroutineScope` / `runTest`:

```kotlin
class PauseViewModelTest {
    private val quoteRepo: QuoteRepository = mockk()
    private val statsRepo: StatsRepository = mockk()
    private lateinit var viewModel: PauseViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = PauseViewModel(quoteRepo, statsRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads quote and attempt count`() = runTest {
        val quote = Quote(text = "Test", author = "Author")
        coEvery { quoteRepo.getRandomQuote() } returns quote
        coEvery { statsRepo.getTodayAttemptCount(any()) } returns 2

        viewModel.init("com.example.app", "Example")

        assertEquals(quote, viewModel.quote.value)
        assertEquals(3, viewModel.attemptCount.value) // +1 for current attempt
    }
}
```

### Repository Unit Tests (with MockK)

Repository tests should mock DAOs and API interfaces:

```kotlin
class QuoteRepositoryTest {
    private val api: ZenQuotesApi = mockk()
    private val dao: QuoteDao = mockk(relaxed = true)
    private val repo = QuoteRepository(api, dao)

    @Test
    fun `getRandomQuote fetches from remote when cache empty`() = runTest {
        coEvery { dao.count() } returns 0
        coEvery { api.getQuotes() } returns listOf(QuoteDto("q", "a", "h"))
        coEvery { dao.getRandom() } returns Quote(text = "q", author = "a")

        val result = repo.getRandomQuote()

        assertNotNull(result)
        coVerify { dao.deleteAll() }
        coVerify { dao.insertAll(any()) }
    }
}
```

### Room DAO Tests (Instrumented)

DAO tests require a real or in-memory Room database and must run as instrumented tests:

```kotlin
@RunWith(AndroidJUnit4::class)
class BlockedAppDaoTest {
    private lateinit var db: BreatheDatabase
    private lateinit var dao: BlockedAppDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BreatheDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.blockedAppDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun insertAndRetrieveBlockedApp() = runTest {
        val app = BlockedApp(packageName = "com.test", appName = "Test")
        dao.insert(app)
        assertTrue(dao.isBlocked("com.test"))
    }
}
```

## Mocking

**Framework:** MockK (`io.mockk:mockk:1.13.11`)

**Key MockK patterns for this codebase:**

```kotlin
// Mock suspend functions
coEvery { repo.getRandomQuote() } returns Quote(text = "q", author = "a")
coEvery { repo.isBlocked(any()) } returns true

// Verify suspend calls
coVerify { repo.recordEvent(any()) }

// Relaxed mock (all functions return defaults)
val dao: BlockedAppDao = mockk(relaxed = true)

// Capture Flow emissions (use Turbine or runTest + toList)
val flow = MutableStateFlow(emptyList<BlockedApp>())
every { dao.getAll() } returns flow
```

**What to mock:**
- DAOs in repository tests
- Repository interfaces in ViewModel tests
- `UsageStatsManager` in `ForegroundAppDetector` tests
- `ZenQuotesApi` in `QuoteRepository` tests

**What NOT to mock:**
- Room in-memory database in DAO tests — use the real Room in-memory builder
- Kotlin data classes and model objects — construct them directly

## Testing Coroutines

All repository and ViewModel functions use coroutines. Use `runTest` from `kotlinx-coroutines-test`:

```kotlin
@Test
fun `suspend function test`() = runTest {
    // Suspend functions can be called directly inside runTest
    val result = repo.getTodayTotalAttempts()
    assertEquals(5, result)
}
```

For `StateFlow` emission testing, advance time or use `UnconfinedTestDispatcher`:

```kotlin
@Before
fun setup() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
}
```

## Test Types

**Unit Tests (JVM — `src/test/`):**
- Scope: Repositories, ViewModels, pure Kotlin logic (`ForegroundAppDetector`, `StatsRepository` time calculations)
- No Android framework required; MockK stubs replace Android dependencies
- Target: business logic, state transitions, error handling branches

**Integration/Instrumented Tests (`src/androidTest/`):**
- Scope: Room DAOs with in-memory database, database migrations
- Require Android runtime — run on device or emulator
- `BreatheDatabase.MIGRATION_1_2` in `BreatheDatabase.kt` should have a migration test

**E2E / UI Tests:**
- No Compose UI test dependency declared (e.g., `androidx.compose.ui:ui-test-junit4` is absent from `libs.versions.toml`)
- Compose UI testing is not currently configured

## Coverage

**Requirements:** None enforced — no JaCoCo or coverage threshold configuration present.

**View Coverage:**
```bash
./gradlew testDebugUnitTest jacocoTestReport  # Only if JaCoCo configured in future
```

## Key Gaps

- **No tests exist** — the test directories are empty despite dependencies being declared
- **No Compose UI test dependency** — `ui-test-junit4` and `ui-test-manifest` are absent from `libs.versions.toml`; Compose screens cannot be tested with Compose test APIs until added
- **No Hilt testing dependency** — `hilt-android-testing` is not declared; Hilt injection in instrumented tests will require manual setup or this dependency
- **No JaCoCo coverage enforcement** — code coverage is not measured or gated

---

*Testing analysis: 2026-06-15*
