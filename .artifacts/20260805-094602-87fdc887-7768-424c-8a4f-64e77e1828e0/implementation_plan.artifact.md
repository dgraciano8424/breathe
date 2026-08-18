# Nimbus Evolution & System Refinement Plan

This plan outlines the transformation of the current achievement system into a rewarding "Growth Pet" experience (Seed -> Plant -> Nimbus) and enhances the app's security and connectivity infrastructure.

## User Review Required

- **XP Balancing**: Does the proposed XP (Fresh Air) feel fair? (50 XP for a decline, 10 XP/min for breathing).
- **Security vs. Convenience**: We are opting for local encryption and easy unblocking to respect user autonomy, as requested.

## Proposed Changes

### [Gamification & Evolution]

We are merging the Seedling and Nimbus concepts into a single evolutionary path driven by "Fresh Air" (XP).

#### [Achievement.kt](file:///C:/Users/dgrac/StudioProjects/breathe/app/src/main/java/com/dgraciano/breathe/data/model/Achievement.kt)

- Replace simple minute-based levels with **Fresh Air Points (XP)**.
- Define 8 evolutionary stages:
  1. **Dormant Seed** (Level 0)
  2. **Submerged Sprout** (Level 1)
  3. **Tidal Fern** (Level 2)
  4. **Blooming Coral-Plant** (Level 3)
  5. **Air Producer** (Level 4 - starts emitting bubbles)
  6. **Ascending Spirit** (Level 5 - transition stage)
  7. **Nimbus Pup** (Level 6 - small cloud)
  8. **Storm King / Sky Warden** (Level 7+ - fully realized Nimbus)

#### [EvolutionBuddy.kt](file:///C:/Users/dgrac/StudioProjects/breathe/app/src/main/java/com/dgraciano/breathe/ui/components/EvolutionBuddy.kt) [NEW]

- A single Composable that replaces `NimbusBuddy.kt`.
- Uses a `when(stage)` block to switch drawing logic.
- **Seed Stage**: A glowing, pulsing oval at the bottom of the screen.
- **Plant Stages**: Procedural swaying stems using `Path` and `sin()` functions.
- **Nimbus Stages**: Enhanced version of the existing cloud code with added wind/rain effects at high levels.

---

### [Security & Connectivity]

#### [DatabaseModule.kt](file:///C:/Users/dgrac/StudioProjects/breathe/app/src/main/java/com/dgraciano/breathe/di/DatabaseModule.kt)

- Integrate **SQLCipher** to encrypt the Room database.
- Use a hardware-backed key (if available) or a user-specific salt for the passphrase.

#### [BackupManager.kt](file:///C:/Users/dgrac/StudioProjects/breathe/app/src/main/java/com/dgraciano/breathe/data/repository/BackupManager.kt) [NEW]

- Provide functionality to export/import an encrypted JSON of the user's progress.
- This allows "connectivity" (migrating data) without requiring a central server.

---

### [Refinement & Reliability]

#### [BreatheAccessibilityService.kt](file:///C:/Users/dgrac/StudioProjects/breathe/app/src/main/java/com/dgraciano/breathe/service/BreatheAccessibilityService.kt)

- Optimize the package detection logic to ensure zero lag.
- Add logging for "rules followed" to feed into the XP system.

## Verification Plan

### Automated Tests
- `gradle_build("app:unitTests")`
- New tests in `AchievementTest.kt` to verify XP-to-Stage mapping.

### Manual Verification
- **Evolution Preview**: Use Compose Previews to verify each of the 8 stages looks correct.
- **Database Inspection**: Attempt to open the DB file with a standard SQLite browser to ensure encryption is working.
- **XP Gain**: Manually trigger a "Decline" in the app and verify the XP bar increases.
