# Breathe review and improvement direction

Reviewed October 5, 2026: dgraciano8424/breathe (Android) and dgraciano8424/breathe-chrome.

## Recommendation

Keep the offline Android foundation: Kotlin, Compose, Room, narrow accessibility events and no server. The architecture supports the core experience. Improve choosing apps, taking a short pause and understanding recorded choices incrementally; a full rewrite is unnecessary.

## Implemented

### Android

- Fix the reproduced first-overlay crash by attaching lifecycle, saved-state and ViewModel owners to the window root and ComposeView, with lifecycle-based disposal.
- Cancel stale initialization, reset optional reasons, identify each pause session, wait for settings before starting its elapsed-time countdown, and record only its first choice.
- Hardware Back records Go back and opens home. Grounding tips and reasons are optional; Go back acts immediately. Permission onboarding scrolls and describes the actual action.
- Show permission grants separately from the live service connection and monitored-app list loading. Home has a chosen-app Test a pause flow and expandable diagnostics, including the last successfully displayed overlay in this process. No app names or visit history are saved by diagnostics.
- Dismiss an outstanding overlay when another app or the launcher comes forward; revoke the departed app's approval. Screen-off dismisses the pause and clears approvals. Keyboard windows, the notification shade and Breathe's own overlay preserve the underlying visit. Removing a monitored app also dismisses its pause and revokes its approval.
- Calculate levels from distinct local calendar days with recorded choices and badges from days/choice totals. Continue and Go back both count; days need not be consecutive. Recalculate directly from existing history without changing schema v5 or deleting events. Level names and badge thresholds change as part of this transition.
- Show both choices in insights and the home widget. Keep inferred session minutes separate and clearly labeled as estimates. Fix Sunday's Monday-based week boundary across locales.
- Inject the home IO dispatcher and cancel test ViewModels, removing a test race against real background work.

### Chrome 0.3.0

- Add 15-, 30- and 60-minute snooze in the popup and settings, with Resume now and a displayed deadline. Preserve the website list, durations, master-enabled preference and statistics.
- Save the snooze deadline locally; wake the worker with a Chrome alarm, check expiry on startup/settings requests, and recreate the alarm after restart. Ignore an early alarm when a newer snooze remains active. Chrome can deliver alarms late after sleep, so this is not an exact scheduling guarantee.
- Release an already open pause when snoozing. Resume restores redirect rules and revokes previous tab approvals. Roll back rules and alarm state if saving fails. No added permissions.
- Export versioned settings backups; validate, preview and confirm imports. Add missing websites, preserve existing per-site choices/statistics, and require separate access grants for imported websites. Backups exclude URLs, statistics and temporary snoozes.
- Undo the most recent website removal. Partial per-site updates prevent duration edits from overwriting newer enable/disable choices.

## Verification

- Chrome: 21 unit tests, 14 installed-extension Chromium tests, TypeScript, production build and ZIP pass. Tests cover countdowns, exact destinations, tab-scoped approvals, permissions, failed saves, backup/undo, pending-pause release, early/expiry alarms and snooze controls in both interfaces. A startup options-navigation race in the browser harness was fixed by waiting for the install handler. Native Chrome permission accept/deny dialogs remain a manual release check.
- Android: 58 JVM tests, debug APK and normal lintDebug pass. Lint has zero errors and 48 warnings, including existing dependency/toolchain and unused-resource warnings. The Python standard-library progress SQL check passes empty history, duplicate-day choices and nonconsecutive legacy history with estimated savings retained separately.
- Read-only Pixel emulator: confirmed chosen-app testing opens the overlay; notification shade preserves it; Home and screen-off remove it; reopening can pause again; Continue and hardware Back each record a choice. Home showed two choices, one Go back and one active day. Monitoring details showed connection/loading/last displayed pause. Permission-loss/recovery states were inspected. No accessibility service crash appeared in these checks.

## Remaining improvements

1. Unify the visual language. Chrome uses cream and sage; Android uses an ocean palette and decorative waves. Simplify Android's waves/cards, improve contrast, and verify large fonts and reduced motion on every screen before changing its whole theme.
2. Add Android timed pause-all, history export/deletion and clearer support instructions. Keep control local; accounts/cloud synchronization are unnecessary for the core flow.
3. Verify physical-phone behavior: app switching, keyboard changes, notification shade, lock/unlock, split screen, overlay revocation and OEM battery restrictions. Test database upgrades from older releases before store publication. Emulator checks do not establish behavior on every phone.

Accessibility window events cannot guarantee intervention before an app displays or loads content. Chrome handles new top-level HTTP/HTTPS GET visits; already loaded tab activation, SPA routing and some history/cache restores remain outside interception. Android APK is a debug test build, not a signed store release. Both changes remain in draft pull requests.

Platform references: [Android accessibility events](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent), [elapsedRealtime](https://developer.android.com/reference/android/os/SystemClock), [overlay constraints](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events), and [Chrome alarms](https://developer.chrome.com/docs/extensions/reference/api/alarms).
