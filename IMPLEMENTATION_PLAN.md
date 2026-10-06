# Revised Plan: Fresh Air & Evolution Buddy

Supersedes `.artifacts/20260805-094602-.../implementation_plan.artifact.md` and its task list.

## Context

The committed artifact proposes three bundled workstreams: an XP-driven Seed→Nimbus "growth pet",
SQLCipher database encryption with an encrypted-JSON backup, and an accessibility-service perf pass.
Six review/design agents examined it against the tree. The gamification idea is sound and worth
building. The rest does not survive contact with the codebase:

- **It is stale.** The artifact is stamped 2026-08-05; the accessibility rewrite (`90f7e30`) landed
  2026-08-10 and *deleted* the 500ms poll loop the plan proposes to optimize. The current hot path
  (`BreatheAccessibilityService.kt:60-83`) is an int compare, two string compares, and a `Set` lookup
  against a `@Volatile` in-memory mirror that exists precisely so "the event path never touches the
  database" (`:44-46`). There is nothing to optimize.
- **SQLCipher protects against nobody.** See the threat model below.
- **The XP formula is unimplementable as written.** Breathing time is never persisted anywhere.
- **The verification plan cannot run.** `@Preview` appears zero times in the repo; there is no
  `androidTest` directory, no `ui-test-junit4`, no screenshot tooling, and no CI.

The intended outcome of this revision: ship the evolution buddy as a small, testable, fully-specified
change that does not touch the interception path, and delete the workstreams that add risk without
adding value.

## Gating constraint

`APP_AUDIT.md` Blocker 1: the interception path "has never been observed working" on hardware, and
"no further code should be written against the interception path until it is answered."

Nothing in this plan touches that path — that is deliberate, and it is why the plan is scoped small.
But **the device pass should still happen first.** The app is `versionCode = 1`, never released. If
the pause does not appear on hardware, all of this is decoration on a non-functioning app.

---

## Decision 1 — Drop SQLCipher entirely

Not "defer." Delete the line.

**Threat model.** Who reads `/data/data/com.dgraciano.breathe/databases/breathe.db`?

| Adversary | Already stopped by | SQLCipher adds |
|---|---|---|
| Another installed app | UID sandbox | nothing |
| `adb backup` / cloud / D2D transfer | `allowBackup="false"` + both rule files | nothing |
| `adb shell run-as` | release build is not debuggable | nothing |
| Phone stolen locked/off | FBE — mandatory at API 26, and `minSdk = 26` | nothing |
| Root on a **running, unlocked** device | — | nothing meaningful — root can ask Keystore to unwrap as the app, or read the live process |
| **The person holding the unlocked phone** | — | **nothing. They open the app and read Stats.** |

That last row is the only adversary who plausibly threatens this user — a partner, parent, or employer
is exactly who wants the data of someone tracking compulsive phone use. SQLCipher is powerless against
them. The correct control there is an **app lock** (`androidx.biometric`, ~50KB) gating launch and the
Stats route. Put that on the roadmap if the concern is real.

**Cost.** 1.03MB release APK (`APP_AUDIT.md:137`) → ~7MB across four ABIs (no splits/bundle config).
~600% growth. Worse than the size: attaching `SupportFactory` to an existing plaintext `breathe.db`
throws, so it needs a one-shot `sqlcipher_export` migration — layered on a 1→5 chain that
`exportSchema = false` means nothing can verify, with no `androidTest` to hold a migration test.
Failure mode is not "weak crypto", it is **an existing database becomes permanently unopenable**.

Also note the plan's own verification step is impossible: release builds are not debuggable, so
`run-as` fails and the file cannot be pulled without root. You could only check a build that doesn't ship.

**Reopen only if:** the DB starts holding free-text the user typed (the moment `reason` stops being a
four-value enum, re-run this analysis); the app gains network/sync/an account; or a regulated-data or
Play-policy obligation names encryption-at-rest. Even then prefer column-level over whole-DB.

## Decision 2 — Backup/export is a real feature, but not now and not encrypted

Independently valuable (data ownership is the app's spine; `allowBackup="false"` currently means a new
phone loses everything), adds **zero new permissions** via SAF, and touches no interception code. But
there are zero installs and therefore zero data to migrate — its value is entirely prospective.

Sequence it **after** the device pass and after this plan. When it is built:

- **Plaintext JSON + SAF, no encryption in v1.** A Keystore-wrapped device-bound key makes the export
  undecryptable on the new device, defeating the only stated purpose. (And the artifact's "or a
  user-specific salt" is simply wrong — a salt is public by construction.) The honest control is
  disclosure, not cryptography. Optional passphrase (PBKDF2-SHA256 ≥210k, AES-256-GCM) as v2.
- **`kotlinx.serialization`**, ~100-200KB post-R8. Decisive reason: it is pure JVM and therefore
  testable in the only test tier this project has. `android.util.JSONObject` is disqualified because
  `isReturnDefaultValues = true` makes `org.json` stubs return `null` silently — the worst possible
  property for a migration format. (Gson was removed *with the network stack*, not on principle —
  `CONCERNS.md:139-141` — so there is no precedent being violated.)
- **Envelope with `formatVersion`**, independent of Room's version. Import **merges**, drops incoming
  `id`s, de-dups on `(packageName, timestamp, outcome)` — making re-import idempotent, which is what
  turns a scary button into a safe one.
- **Needs a Settings screen** (`Routes.SETTINGS`, gear in `HomeScreen`'s existing `TopAppBar` actions),
  which several homeless items in `CONCERNS.md` are already waiting for.
- **Docs in the same commit:** `PRIVACY.md` + `docs/index.html` in lockstep (`PRIVACY.md:6-8` mandates
  it), `STORE_LISTING.md`, `RELEASE.md`. Play Data-safety needs **no** change — a user-initiated local
  file write is not collection; say so in `RELEASE.md` so nobody re-litigates it.

Note: shipping export makes SQLCipher *less* necessary, not more. Once a plaintext export exists by
design, the softest copy is a file in Downloads, not one inside the sandbox behind FBE.

## Decision 3 — Fresh Air is derived, decline-weighted, and day-bucketed

**No new persisted state. No schema migration. Database stays at v5.**

The artifact's "50 XP/decline + 10 XP/min breathing" cannot be built: `minutesSaved` is the *avoided
doom-scroll estimate*, not breath time, and the countdown lives only in `PauseScreen`'s local state.
It is also actively wrong as an incentive — at `PauseScreen.kt:283-293` "No, go back" is enabled
immediately while "Yes" is gated on `secondsLeft <= 0`, so **XP per breathing second pays more for
capitulating** and pays nothing to the fastest, healthiest decline.

And the current curve is broken, not merely mistitled: `SessionTimeHelper` returns a hardcoded
`DEFAULT_SESSION_MINUTES = 20` without usage access (`:10,77`), so today's levels are already a noisy
decline count. At 3 declines/day, level 7 arrives on **day 720**. Stages 6-7 are unreachable.

```
dayXp(declines, minutes) = 50 * min(declines, 10)   // the win itself
                         + 25                        // showing up that day
                         + min(minutes, 120)         // estimated time reclaimed
freshAir = Σ dayXp over every local day with ≥1 decline
```

- **OPENED = 0 XP. Never negative. No decay.** Opening is permitted, not punished — that is the
  README's thesis. Decay would make Nimbus rot for the user who lost the habit, and would destroy the
  recompute-anywhere property.
- The **per-day caps** are what stop this being rescaled minutes: the minutes term is subordinate and
  bounded, so a bad estimate can't dominate, and cross-device variance compresses to ~1.3x.

| # | Stage | `minXp` | 3/day | 1/day | 8/day |
|---|---|---|---|---|---|
| 0 | Dormant Seed | 0 | day 0 | day 0 | day 0 |
| 1 | Submerged Sprout | 125 | day 1 | day 2 | day 1 |
| 2 | Tidal Fern | 600 | day 3 | day 7 | day 2 |
| 3 | Blooming Coral | 1,800 | day 8 | day 19 | day 4 |
| 4 | Air Producer | 4,500 | day 20 | day 48 | day 9 |
| 5 | Ascending Spirit | 9,000 | day 39 | day 95 | day 17 |
| 6 | Nimbus Pup | 18,000 | day 77 | day 190 | day 34 |
| 7 | Storm Warden | 36,000 | day 154 | day 379 | day 67 |

Stage 1 = 125 so it is reachable by the count terms alone (two declines in one day = exactly 125),
meaning the noisy minutes estimate never decides a user's first promotion. From stage 3 it is a clean
doubling. Reference user tops out in ~5 months — long but finite.

**Minutes coexist, reframed and demoted.** `totalMinutesSaved`/`hoursDisplay` stay and stay honest,
but stop driving progression and get labeled an estimate. `TimeSavedCard`'s headline becomes Fresh Air;
stats become `Fresh Air | Resisted | Time reclaimed`. Minute-keyed badges (`Hour/Day/Week/Month Saved`)
**stay as-is** — now that minutes aren't the ladder, badges are their natural home. Add `Seven Days`
and `Rooted` (7/30 active days) so the new currency has milestones too.

## Decision 4 — Two renderers and a growth ramp, not eight bespoke stages

The difference between "Submerged Sprout" and "Tidal Fern" is stem height and leaf count. Those are
parameters, not drawings. The arc that matters is **seabed → water column → sky** — which the existing
"Tranquil Ocean" palette in `Color.kt` already supports, so **no new palette is needed**.

```
ui/components/EvolutionBuddy.kt   [NEW ~90]   the only @Composable; owns the box, ONE infinite
                                              transition, semantics, label
ui/components/SproutBuddy.kt      [NEW ~130]  internal DrawScope.drawSprout(...)
ui/components/NimbusBuddy.kt      [EDIT]      KEPT — composable wrapper removed, file keeps
                                              drawCloud/drawGlow/drawWind + new drawNimbus(...)
data/model/EvolutionStage.kt      [NEW ~50]   pure enum + stageFor() + growthWithin(); no Compose
```

~220 new lines, versus ~700-1200 for the artifact's single-file `when(stage)` — which would also have
violated `CONVENTIONS.md:16` ("named for what they draw") and `:259-260` (single responsibility).

- **Renderers are `DrawScope` extensions, not composables.** One `Canvas`, one transition, shared
  channels. Nesting composables would multiply infinite transitions.
- **`NimbusBuddy.kt` survives**, so `CONVENTIONS.md:16` — which cites it as the exemplar — stays true.
- **Level 4 "Air Producer" is free**: `drawSprout(1f)` plus a small `drawNimbus(0.2f)` in the same box.
  Both renderers in one frame is the most legible moment in the whole arc, at zero new drawing code.
- **Phase 2** (after the device pass): bubbles at level 3+, rain at level 7. ~60 lines, additive.

**Layout.** "A glowing oval at the bottom of the screen" is not achievable — the buddy is a
`LazyColumn` item near the top (`HomeScreen.kt:129-140`) and scrolls. Resolve it by making the
composition self-contained: a **fixed 168dp box** with an implicit seabed at its bottom edge; seed sits
on that edge, stems rise from it, cloud floats in the upper third. The
`(58 + (strength-1)*7).coerceAtMost(120).dp` formula at `:83` is **deleted** — size stops being a
function of level, and `growth` scales content *within* a constant box. This also kills `LazyColumn`
item resize and scroll jump on stage change.

**Reduced motion.** The rule the existing code never states: freezing is right for *oscillating*
motion, wrong for *translational* motion — a bubble frozen mid-flight reads as a bug.

```
sway   -> 0f      upright (freeze at REST, not midpoint — a bent stem looks broken)
pulse  -> 1f      as NimbusBuddy:76 already does
bubbles-> static droplets clinging at leaf tips, NOT frozen mid-flight
rain   -> static droplets under the cloud edge
stage  -> instant snap(), never a morph
```

**Semantics.** On the `Column`, not the Canvas: `semantics(mergeDescendants = true)` with a pure
`evolutionDescription(stage, levelName)` function (unit-testable). It must describe the *creature*
("Your companion is a young sprout, growing on the sea floor") — the neighbouring `JourneyCard`
already announces level name and numbers, so repeating them would double-announce.

**No stage transitions.** A stage change happens at most 7 times per user lifetime, inside
`refreshStats()`, very likely while the buddy is off-screen. Doing it properly needs persisted
`lastSeenStage` — otherwise it re-fires on every process restart — which is real state and a real
migration surface for a flourish nobody sees. The continuous `growth` parameter already makes the buddy
change visibly at *every* level, which delivers the felt progression for free.

**Prerequisite fix, same commit.** `WaveBackground.kt:58-68` builds 3 `Path`s per frame with ~216
`lineTo` calls each, full-screen, on all 6 screens, **and never consults `rememberReducedMotion()`**.
Reduced motion on the home screen is currently a lie — Nimbus obediently freezes while the entire
background keeps rolling. ~6 lines: pin `phase`, hoist the `Path`s into `remember` + `rewind()`.
(`ConfettiOverlay` is ungated at `PauseScreen.kt:312` — same bug, fix if any evolution celebration
ever reuses it.)

**Perf budget.** Zero heap allocation inside the draw lambda (`drawCloud:141-147` and `drawWind:179-184`
currently allocate ~38 objects/frame — move the `listOf(Triple(...))` tables to file-level constants).
`Path` hoisted via `remember`, `rewind()` per draw. **Read animated `State` inside the draw lambda, not
via `by` in the composable body** — `NimbusBuddy:45-72` does the latter and recomposes every frame.
≤4 `animateFloat` channels total, shared with per-element phase offsets. No whole-buddy `graphicsLayer`.

---

## Conflict resolved

The XP and UI agents disagreed on two points; my calls:

1. **Rename the 8 levels?** The UI agent said no — renaming has user-visible progress semantics. But
   `versionCode = 1` and the app has never been released, so there are no users whose earned names
   would change. **Rename.** Keep the no-demotion test anyway; it's cheap and covers the dev's install.
2. **`EvolutionBuddy(levelIndex: Int)` or `(stage: Level)`?** Take the **domain object** — it makes the
   0-vs-1-based bug unrepresentable — and derive both `stage` and `growth` from `level.index` inside.
   Best of both designs.

## The 0-vs-1-based trap

`HomeViewModel.kt:124` currently does `_nimbusStrength.value = currentLevel.index + 1`, feeding
`NimbusBuddy`'s 1-based `strength`, while the new stages are 0-based. **Delete `_nimbusStrength`
(`:51-52`) and `:124` entirely** rather than patching them. The 1-based convention dies with the
composable wrapper — it must not survive as a second coordinate system. `WIND_UNLOCK_STRENGTH = 3`
(currently 1-based, meaning index 2) gets rebased to an explicit index constant.

## Files

**New**
- `data/model/EvolutionStage.kt` — pure enum, `stageFor()`, `growthWithin()`, `evolutionDescription()`
- `data/model/DayProgress.kt` — projection with explicit `@ColumnInfo` (do not repeat `AppStat`'s
  untyped column matching, per `CONCERNS.md`)
- `ui/components/EvolutionBuddy.kt`, `ui/components/SproutBuddy.kt`
- `app/src/test/.../AchievementsTest.kt`, `EvolutionStageTest.kt`, `AchievementRepositoryTest.kt`

**Changed**
- `data/model/Achievement.kt` — `minMinutes: Long` → `minXp: Long`; 8 renamed stages; `computeLevel(freshAir)`;
  `computeBadges(minutes, declines, activeDays)`; `UserProgress` gains `freshAir`/`activeDays`, keeps
  `totalMinutesSaved`/`hoursDisplay`. Add `object FreshAir` with the constants and `dayXp()`.
- `data/db/InterventionEventDao.kt` — one new query, no schema change:
  `SELECT strftime('%Y-%m-%d', timestamp/1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS declines,
  COALESCE(SUM(minutesSaved),0) AS minutes FROM intervention_events WHERE outcome='DECLINED' GROUP BY day`
  (`'localtime'` matches `StatsRepository`'s existing `Calendar`-local boundaries)
- `data/repository/AchievementRepository.kt` — fold `getDeclineDays()` instead of two aggregates
- `ui/home/HomeViewModel.kt` — delete `:51-52` and `:124`
- `ui/home/HomeScreen.kt` — `:49` drop the `nimbusStrength` collect (`progress` is already collected at
  `:50`); `:138` → `EvolutionBuddy(level = progress?.currentLevel)`; `:370-373` XP wording
- `ui/achievements/AchievementsScreen.kt` — `:161` XP remaining; `:183` stat swap; `:247` `minXp` label.
  `formatMinutes` survives for badges.
- `ui/components/NimbusBuddy.kt` — drop the composable wrapper, expose `internal DrawScope.drawNimbus`
- `ui/components/WaveBackground.kt` — reduced motion + `Path` hoisting
- `app/src/test/.../HomeViewModelTest.kt:60-66` — fixture compile break
- `.planning/codebase/CONVENTIONS.md` + `TESTING.md` — one sentence each (first `@Preview`s; a
  components file with no public composable), or they drift, which is this repo's recurring failure

**Untouched, deliberately:** `BreatheDatabase.kt` (stays v5), `InterventionEvent.kt`, `PauseViewModel.kt`,
`PauseScreen.kt`, `PauseOverlayHost.kt`, `PauseActivity.kt`, `DatabaseModule.kt`, `PauseCountWidget.kt`,
`AchievementsViewModel.kt`, `BreatheAccessibilityService.kt`.

## Verification

Three tiers, **no new dependencies**. The strategy is to push every decision out of the pixels into
pure functions, test those in the existing 48-test JVM suite, and eyeball only the pixels.

**Tier 0 — JVM unit tests (the real safety net).**
- `stageFor(0..7)` covers all 8 levels, no gaps, no off-by-one; `growthWithin` monotonic, clamped 0..1
- thresholds strictly increasing; `computeLevel` promotes at exactly `minXp` and not at `minXp - 1`;
  `progressToNext` returns `1f` at the last stage without dividing by zero
- `dayXp`: the 11th decline earns 0, minutes clamp at 120, a 0-decline day contributes 0
- **monotonicity**: adding any DECLINED row never decreases XP; adding any OPENED row never changes it
- **no-demotion table**: ~8 legacy `(minutes, declines, activeDays)` profiles assert
  `newStage.index >= oldMinuteLevel.index`
- `AchievementRepositoryTest` with `mockk<InterventionEventDao>` — the DAO is an interface, fully JVM-safe
- `evolutionDescription` non-blank and distinct per stage (this is the TalkBack coverage, for free)

**Tier 1 — the repo's first `@Preview`s.** One `EvolutionBuddyAllStagesPreview` rendering all 8 levels
in a `Column`, plus one at `widthDp = 320`. Zero new deps (`ui-tooling-preview` is already
`implementation` at `app/build.gradle.kts:92`, `ui-tooling` is `debugImplementation` at `:97`).
Justification: reaching level 7 legitimately requires 36,000 Fresh Air — there is no other way to see
the top stage. `rememberReducedMotion` is preview-safe; its `runCatching{}.getOrDefault(false)`
(`ReducedMotion.kt:20-26`) degrades cleanly.

**Tier 1.5 — one line in `RELEASE.md`'s device checklist:** "buddy renders at the current level; set
animation scale to 0 and confirm it holds still."

**Explicitly rejected:** Paparazzi/Roborazzi goldens (no CI per `TESTING.md:174` — a golden nobody runs
is a `@Preview` at 10x the cost, rotting in binary) and `ui-test-junit4` + `androidTest` (new source
set, emulator per run, no CI → runs approximately never; Tier 0 covers the same ground for free).

**Build gates:** `./gradlew lintDebug testDebugUnitTest assembleDebug` then `assembleRelease`. Record
the new APK size against the tracked 1.03MB in `APP_AUDIT.md:137` — silently changing a tracked figure
is exactly the drift this repo has spent two revisions cleaning up.

**Honest limit:** none of this proves the drawing looks good, and none of it touches Blocker 1. That is
the right trade at this moment — and it is the argument for phase 1 being ~220 lines rather than ~1000.

## Recommended order

1. **Device pass** — Blocker 1 (does the pause appear?) and Blocker 2 (shade/IME revoking approval)
2. `exportSchema = true` + committed schema JSON — nearly free, and a prerequisite for any future data
   work being safe, regardless of what happens to the rest
3. **This plan** — Fresh Air + EvolutionBuddy phase 1
4. Settings screen + export/import
5. SQLCipher: never, absent an entry condition above. App lock instead if at-rest privacy is the worry.
