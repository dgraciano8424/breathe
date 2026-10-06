# Breathe quality status — October 6, 2026

Current review branches include the navigation/overlay quality pass and the architecture follow-up: independent pause policies, durable Android choice delivery, and modular Chrome extension 0.6.3.

| Check | Result |
|---|---|
| Android JVM suite | 121 passed; zero failures/errors/skips |
| Android lint | Zero errors; 48 warnings |
| Migration SQL | Populated versions 1–6 preserve records and match exported Room v6 tables/indices |
| Native Room upgrade | v5→v6 passed; earlier v4→v5 also passed |
| Chrome unit tests | 30 passed |
| Chrome strict TypeScript | Passed |
| Chrome production-build browser workflows | 32 passed |
| Chrome package | v0.6.3 ZIP; all 18 unpacked files match tested build |
| Android debug APK, optimized release APK and AAB | Passed; production outputs unsigned |
| Optimized release installation/overlay/history | v6 passed with test key; debugging off; correct three-choice total |
| Android durable recovery | Restart/replay deduplication, forced failure recovery and queued-history clearing passed |

## What changed

- Android combines discovery/save/error recovery, permanent Home navigation, optional usage data, practice mode, monitoring diagnostics, accurate visit tracking, snooze/resume, personal reminders and optional intentions.
- Pause windows supply Compose lifecycle owners and resume after attachment. Failed attempt-count reads preserve the configured pause duration. Choices are deduplicated; history clearing preserves app settings.
- Practice completion exits once. Theme defaults and system-bar icons suit the dark background; choice labels handle singular counts. Monitoring Details explains protected Android screens that hide overlays.
- Chrome rolls back counts and pending state when choice navigation fails, reacts to revoked website access, restores failed permission toggles, ignores stale failed reads, preserves keyboard focus, and fits long website names at 320 CSS pixels in light/dark themes.

- Both platforms have pure countdown/choice policies. Android's window host uses a dedicated ViewModel factory; Chrome composes storage, rules/alarms, settings, pauses, authorization and runtime controllers through one serial queue.
- Android acknowledges choices after committing to a Room queue. Delivery/removal are atomic, stable IDs prevent duplicates, failures retry, and process startup recovers pending choices. Clearing history includes the queue. Open Home/Insights/Achievements screens react to delivery.

## Emulator acceptance

The architecture follow-up upgraded populated v5 data to v6, preserving settings/history. A queued choice recovered on restart; repeating its ID did not duplicate history. A synthetic SQLite delivery failure left the choice queued, and recovery after removing that fault succeeded. Native Clear history deleted recorded and queued choices, preserving Clock's five-second duration.

The new live pause recorded one Go back and one Continue with an empty queue afterward. The optimized test-signed release preserved them and recorded another Go back. Native Insights showed exactly three choices: one Continue and two Go back. DEBUGGABLE was off and no app crash appeared. A temporary System UI ANR occurred while the emulator and Gradle were busy; it was dismissed before live acceptance.

Earlier navigation/widget/appearance acceptance follows:

On a disposable, read-only Pixel 10 AVD with networking disabled: opaque live Clock overlays; countdown blocking; Continue/Go back/Android Back; departure and screen unlock; keyboard/notification-shade approval; snooze/resume; saved reminder; 150% text; landscape scrolling; reduced-motion stillness; practice with repeated Done taps; native CSV saving and confirmed clear. CSV counts matched eight synthetic choices and excluded the reminder.

The launcher widget was placed through Android's widget picker, opened Breathe on tap, updated after a real choice, and returned to zero after history clearing. The optimized release copy preserved the selected duration/reminder, showed its live overlay, and recorded exactly one Go back and one Continue in the widget. No app crash appeared in the test log. The saved AVD and user's phone were untouched.

## Remaining acceptance

- Reconnect Samsung SM_S938U when available and repeat the core overlay, lock/unlock, reboot/rebind, permission-revocation, widget and optimized-release checks. Earlier Samsung navigation checks passed; its final overlay fix is still unverified.
- Exercise API 26–28 onboarding and the fallback pause Activity on a suitable device/test setup.
- Reload the updated unpacked Chrome extension. Native permission accept/deny prompts, toolbar popup, restart/sleep and media/companion-extension compatibility still need user-browser checks.
- Configure the production upload key before distribution. Emulator release copies use the Android debug test key; APK/AAB outputs without a production key are unsigned.

## Reviews and evidence

- [Android draft PR](https://github.com/dgraciano8424/breathe/pull/3)
- [Chrome draft PR](https://github.com/dgraciano8424/breathe-chrome/pull/1)
- [Detailed dated evidence](COMPETITION_READINESS.md)
- [Architecture and recording guarantees](ARCHITECTURE.md)
- Local synthetic screenshots and exports: outputs/quality-verification/.
- Chrome installed output was backed up to outputs/breathe-chrome/unpacked-backup-20261006-architecture before replacement; Chrome must reload Breathe to activate v0.6.3.

Latest unsigned release APK: 1,139,448 bytes. Test APK signature verification passed. Latest artifacts and SHA-256 hashes are in outputs/architecture-verification/results.json; earlier evidence remains in outputs/quality-verification/.
