# Breathe quality status — October 6, 2026

Current review branches combine the Android navigation/overlay work and reliability controls, and package Chrome extension 0.6.2.

| Check | Result |
|---|---|
| Android JVM suite | 111 passed; zero failures/errors/skips |
| Android lint | Zero errors; 48 warnings |
| Migration SQL | Populated versions 1–5 preserve records and match exported Room v5 schema |
| Native Room upgrade | v4→v5 passed on a read-only Pixel 10 emulator |
| Chrome unit tests | 26 passed |
| Chrome strict TypeScript | Passed |
| Chrome production-build browser workflows | 32 passed |
| Chrome package | v0.6.2 ZIP; all 18 unpacked files match tested build |
| Android debug APK, optimized release APK and AAB | Passed; production outputs unsigned |
| Optimized release installation/overlay/widget | Passed with test key; debuggable off; one of each choice |

## What changed

- Android combines discovery/save/error recovery, permanent Home navigation, optional usage data, practice mode, monitoring diagnostics, accurate visit tracking, snooze/resume, personal reminders and optional intentions.
- Pause windows supply Compose lifecycle owners and resume after attachment. Failed attempt-count reads preserve the configured pause duration. Choices are deduplicated; history clearing preserves app settings.
- Practice completion exits once. Theme defaults and system-bar icons suit the dark background; choice labels handle singular counts. Monitoring Details explains protected Android screens that hide overlays.
- Chrome rolls back counts and pending state when choice navigation fails, reacts to revoked website access, restores failed permission toggles, ignores stale failed reads, preserves keyboard focus, and fits long website names at 320 CSS pixels in light/dark themes.

## Emulator acceptance

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
- Local synthetic screenshots and exports: outputs/quality-verification/.
- Chrome installed output was backed up to outputs/breathe-chrome/unpacked-backup-20261006-quality before replacement; Chrome must reload Breathe to activate v0.6.2.

Final unsigned release APK: 1,129,264 bytes. Test APK signature verification passed. APK/AAB artifacts and SHA-256 hashes are recorded in outputs/quality-verification/results.json.
