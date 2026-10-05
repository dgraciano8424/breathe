# Breathe review and improvement direction

Reviewed October 5, 2026: Android repository `dgraciano8424/breathe` and Chrome repository `dgraciano8424/breathe-chrome`.

## Recommendation

Keep the offline Android foundation: Kotlin, Compose, Room, narrow accessibility events and no server. The core concept is useful and the architecture does not justify a rewrite. Refresh the experience around three things: choosing apps, taking a short pause, and understanding actual choices. The Chrome extension can share that product language without pretending browser interception and Android detection are identical.

## Implemented in this pass

### Android

- Fix a reproduced crash when the first overlay opens: attach lifecycle, saved-state and ViewModel owners to the window root as well as its ComposeView child, and dispose the composition with its lifecycle.
- Cancel previous initialization and guard against late results when a new app retargets a pause. Reset the optional reason and visit count for the new session.
- Give each pause a session identity so equal-duration retargets still reset the screen countdown and transient UI. Wait for configuration loading before starting the countdown; calculate remaining time from an elapsed-time deadline instead of counting delayed ticks. Use the default duration if configuration loading fails.
- Record only the first choice for a pause, preventing duplicate taps and conflicting choices from producing multiple events or an unintended approval.
- Hardware Back now chooses Go back and opens home; previously it could simply reveal the app without approval.
- Make the grounding tip optional, keep reason selection optional with accessible selected states and larger targets, remove the confetti delay before leaving, and use neutral breathing/visit copy.
- Make permission onboarding scrollable and correct its disclosure: the app does open home when the user chooses Go back.
- Label inferred session time as an estimate in home, insights and achievement summaries. Remove the unsupported comparison between a short walk and scrolling. Continuing is acknowledged as a potentially intentional choice.
- Fix the Monday week boundary on Sunday regardless of locale.
- Inject the home screen's IO dispatcher and cancel ViewModels after tests, eliminating a test race against real background work.

### Chrome 0.2.0

- Export a versioned settings backup containing only the website list and default duration.
- Validate and preview imports before confirmation. Add missing websites while preserving existing per-site choices and local statistics. Require separate access grants for imported websites; importing never silently requests broad access.
- Undo the most recent website removal.
- Send partial per-site updates so changing duration cannot overwrite a newer enabled state, and vice versa.

## Next product improvements, in order

1. **Monitoring reliability and diagnostics.** Android currently reports whether its service is enabled, which is different from knowing that interception is actually functioning. Add a setup test with a chosen demo app and a small diagnostic state: service connected, overlay available, last detection. Test notification shade, app switcher, screen lock, split screen, permission revocation and Samsung battery management. The service currently ignores other app events while the overlay is showing; leaving via system UI needs particular attention. Do not promise that an accessibility window event arrives before the target app draws or loads content.
2. **Simplify progress.** Android levels and badges still derive from estimated avoided session time, including a 20-minute fallback. Move achievements toward observable milestones such as taking a pause on several days; offer a view of both Continue and Go back choices. Avoid treating every continued visit as failure or projecting estimates as measured reclaimed time. Preserve existing history through an explicit migration.
3. **Unify the visual language.** Chrome uses cream and sage; Android uses an ocean palette and decorative waves. Choose a common Breathe identity, maintain readable contrast, respect reduced motion throughout onboarding/navigation, and test large fonts. A visual refresh can be incremental rather than replacing the app architecture.
4. **User control and data portability.** Add Android pause-all with an explicit expiry, local history deletion/export, and useful support instructions. Keep app/website selection under the user's control. Scheduling can follow real use feedback; accounts and cloud synchronization are unnecessary for the core flow.
5. **Release hardening.** Test Android database upgrades and overlays on a physical phone, review toolchain deprecation warnings before a Gradle upgrade, and verify Chrome's native permission accept/deny flows before store submission. Browser history/cache restores and already-loaded tab activation remain outside Chrome's network-navigation interception.

## Verification

- Chrome: 17 unit tests, 11 browser tests using the installed release extension, TypeScript check, production build and ZIP pass. Browser checks include countdown enforcement, exact destinations, per-tab approval, permission recovery, failed-save rollback, backup confirmation and undo. Native Chrome permission prompts still require manual checking.
- Android: 51 JVM unit tests, debug APK build and `lintDebug` pass with the normal Gradle command. Lint reports zero errors and 47 warnings. An initial lint run crashed internally; later runs succeeded, and no lint suppression or toolchain downgrade was committed. An intermittent home screen test failure exposed its real-IO dispatcher race and was fixed with dispatcher injection.
- Emulator: reproduced the original overlay lifecycle crash, verified the corrected breathing overlay on a monitored Messages launch, and verified hardware Back dismisses the overlay and returns to the launcher without a crash. The emulator was run read-only. UIAutomator dumps suppress other accessibility services by default, so overlay checks used screenshots and window/service state instead. Physical-device overlay, battery management, accessibility gesture behavior and upgrade validation remain release checks.

Android interval timing uses [SystemClock.elapsedRealtime](https://developer.android.com/reference/android/os/SystemClock). Detection depends on [AccessibilityEvent window events](https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent), and overlays have [platform behavior constraints](https://developer.android.com/about/versions/12/behavior-changes-all#untrusted-touch-events).
