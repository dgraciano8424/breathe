# Competition readiness

Reviewed October 6, 2026. Product quality is the standard for this work, independent
of competition criteria: reliable behavior, clear choices, accessibility, and credible progress.

## Product case

Breathe interrupts an automatic app launch with a breath and a choice. Its strongest
distinction is respectful friction: the user remains in control, the app works offline,
and the experience offers a small alternative instead of punishment.

The entry should demonstrate the whole loop: choose an app, encounter the pause,
make an intentional choice, and see the result reflected in insights.

## First improvement pass

- Practice pause available from setup and home, using the existing pause screen.
  It needs no special permissions and does not record events or grant app approval.
- Setup scrolls and respects safe screen insets; its orb respects reduced motion.
- First-use home state has a visible action and no longer consumes a full screen
  below the journey and insights cards.
- Home list has bottom space so the floating button does not cover the last row.
- Duration choices wrap, have 48 dp minimum targets, and expose selected radio state.
- Navigation transitions shortened to 250 ms and omitted with reduced motion.
- App picker says Done because selections are persisted as they are changed;
  it also allows finishing with no monitored apps and respects navigation-bar insets.
- Zero usage no longer claims that the user has not used an app today.

## Highest-priority remaining work

1. **Prove interception on a physical device.** No device was connected during this
   pass. Build success does not verify accessibility events, overlay display, or
   per-visit approval. Follow the device sequence below before changing that path.
2. **Review the actual screens.** Capture setup, first-use home, picker, pause,
   insights, and journey on a phone. Check small screens, landscape, large fonts,
   TalkBack, gesture navigation, and reduced motion. This pass is code-verified;
   visual and usability verification are pending.
3. **Review permission status and data freshness.** Distinguish unavailable optional
   usage data from measured zero throughout home and insights, and check midnight
   refreshes while the app remains open. The picker already makes that distinction.
4. **Continue the accessibility pass.** Check reason selectors, progress indicators,
   motion behavior, and large-font layouts on a device. Picker rows now expose
   checkbox state to assistive technology.
5. **Prepare judging assets after the product pass.** Use real screenshots and a
   short recording of the real interception flow. Fit the story and evidence to the
   competition's criteria once supplied.

## Device acceptance sequence

- Install debug, open Breathe, and try practice before enabling permissions.
  Finish through both choices; verify stats and monitored apps remain unchanged.
- Enable accessibility and overlay access through the disclosure flow; decline
  optional usage access. Choose one app and configure its pause duration.
- Open that app: exactly one pause appears and Continue remains disabled for
  the configured duration. Test both leaving and continuing.
- After continuing, open the keyboard, notification shade, and in-app dialogs.
  None should cause an unintended new pause during the same visit.
- Leave the app, return, and verify a new pause. Test screen lock/unlock,
  rotation, reboot, and permission revocation/restoration.
- Check event totals after each choice; check returning after midnight updates
  today's numbers. Repeat interception checks on a signed release build.

## Validation record

- `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease` passed.
- 72 unit tests across nine suites; zero failures, errors, or skipped tests.
- Debug lint: zero errors, 47 warnings (same warning count as the prior audit).
- Debug APK and R8-shrunk release APK built successfully. Release signing and
  distribution readiness must be checked separately from compilation.
Device, screenshot, TalkBack, and release behavior remain unverified.

## Second improvement pass

- App discovery survives failed optional usage queries and sorts alphabetically
  when usage is unavailable. Duplicate launcher activities and Breathe itself are excluded.
- Picker selections show saving state; repeated taps on a pending row are ignored.
  Selection changes appear after persistence succeeds. Failures preserve selection
  and show retry guidance in a snackbar. Finishing waits for pending saves.
- Search preserves the total monitored count; stale click callbacks use current state.
- Picker distinguishes unavailable usage data from measured values, including
  sub-minute totals. Rows expose their checked state to screen readers.
- Insights identify saved time as estimated and explain the 20-minute fallback.
  Unsupported wellness comparison removed; leaving and continuing are both valid choices.
- Session estimates fall back safely when usage access is denied and immediately
  retry history after permission restoration rather than caching permission failure.
- Added regression coverage for optional usage denial, sorting, duplicate launches,
  concurrent taps, failed-save retry, stale callbacks, search counts, and load retry.

## Third improvement pass

- Weekly totals now use the preceding or current Monday in the phone's time zone.
  The Calendar implementation could choose the following Monday on a Sunday in
  locales whose week starts on Sunday. Regression tests cover Sunday, Monday,
  and local midnight on a daylight-saving transition.
- Insights recover from database-read errors with a visible retry action. Concurrent
  refreshes share the active load, and failed refreshes preserve the last successful
  values. The screen refreshes when resumed instead of loading twice on entry.
- Suggested activities no longer promise five minutes of breathing from an estimate
  smaller than five minutes.
- Home usage-query tests now control the IO dispatcher, fixing a timing-dependent
  failure exposed by the full suite. Production still uses Dispatchers.IO.
- The companion extension is the private `dgraciano8424/breathe-chrome` repository,
  now checked out separately at `C:/Users/dgrac/StudioProjects/breathe-chrome`.

## Joint app and extension pass

- Home now distinguishes unavailable usage data from a measured sub-minute value,
  matching the picker. Missing optional data no longer appears as measured zero.
- Journey loading handles failures with a retry action, preserves previously loaded
  progress, coalesces overlapping loads, and refreshes on resume.
- Extension site-setting messages now send only the edited field. The background
  merges edits against current saved settings so duration and toggle changes do
  not overwrite one another. Permission failures in the site toggle are recoverable.
- Extension validation: 12 unit tests and 11 release-build Chromium tests passed;
  TypeScript compile and ZIP packaging passed. Native prompts, toolbar behavior,
  and browser restart remain manual release checks.

## Recovery pass and verification resource policy

- Home app removal and pause-duration changes now handle write failures with retry
  guidance, clear pending state, and prevent competing changes to the same app.
- Extension pending-pause reloads preserve current protection messaging and release
  the countdown when the site is no longer protected, without adding a new pause count.
- Extension checks passed: 15 unit tests, TypeScript compile, 12 release-build Chromium
  tests, and ZIP packaging.
- Notify the user before builds or browser tests. Run them sequentially because VS Code
  and `vmmem` also need memory. Shut down idle build daemons after checks.
- This Android pass runs unit tests and lint only, with one worker, a 768 MB Gradle heap,
  in-process Kotlin compilation, and a single-use Gradle daemon. The heap setting is
  not a total-process RAM cap. No new Android APK is packaged in this pass.
- These checks passed: 72 unit tests and lint with zero errors and 47 existing warnings.
  Android packaging results above refer to the prior pass.

## Home statistics refresh recovery
- Failed home insight reads now retain the previous complete figures and show feedback instead of crashing. Returning to the screen retries the refresh.
- Overlapping refresh requests share the active read, avoiding duplicate usage scans.
- Validation: 74 unit tests and lintDebug passed with a single worker, in-process Kotlin compilation, and a 768 MB Java heap. No APK packaging in this pass.


## Exact-midnight statistics inclusion
- Period queries now include events exactly at the local day/week boundary using timestamp >= since.
- scripts/check_stats_queries.py executes the five actual DAO period queries against SQLite fixtures covering before/at/after midnight and empty results; all passed.
- Android unit tests (74) and lintDebug passed using one worker and a 768 MB Java heap. No APK packaging.


## Real-phone navigation verification
- Added a permanent home directory: Choose apps, Settings, Insights, Achievements. Settings reuses the permission disclosure flow, offers Manage for granted access, Back to home, and Practice; it stays open when permissions are already granted.
- Setup now offers Go to home even before grants; monitoring-off feedback remains visible.
- Installed debug APK with adb install -r on the connected Samsung SM_S938U. Verified setup -> Home -> Settings, Insights, Achievements, Choose apps, and Settings -> Practice -> Back on the actual phone.
- Accessibility access is currently off, so actual interception is not yet verified. Overlay and optional usage are granted. No permissions were enabled automatically.
- assembleDebug, 74 unit tests, and lintDebug passed with one worker and a 768 MB Java heap. Screenshot outputs/phone-verification/settings.png captures the installed Settings page.
- Found the user's actual Chrome extension in Documents/Codex/2026-10-05/i-wa/work/breathe-chrome (v0.6.1), with installed unpacked output under that task's outputs/breathe-chrome/unpacked. It differs from the StudioProjects v0.1.0 checkout. Future work on the user's installed extension should use the v0.6.1 source and preserve its additional features.

