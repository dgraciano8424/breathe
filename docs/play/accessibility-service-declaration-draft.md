# Breathe Accessibility Service Declaration Draft

**Submission status:** Draft only. This is a proposed Play Console declaration and review-video plan, not a claim of Google Play approval or eligibility. It must be rechecked against the exact release artifact, store listing, privacy policy, and every included SDK before submission.

**Scope:** The data-use statements below concern Breathe's `AccessibilityService` and the selected-package/pause-outcome feature. In particular, "does not transfer" means the service does not transmit that feature data off-device; it is not an assertion about unrelated app functionality or third-party SDK behavior. The release owner must complete the package-wide Data safety form from the shipped artifact.

## Classification

Breathe is not an accessibility tool. Its service metadata sets `isAccessibilityTool=false`.

## Core feature requiring the API

Breathe lets a user select distracting apps. When one of those selected apps enters the foreground, Breathe observes the package-level window-change event and presents one mindful breathing pause before the user chooses whether to return or continue. Android does not provide a narrower event API that can deliver this user-requested, app-specific foreground transition with equivalent timing.

## Data accessed

- Package identity from `TYPE_WINDOW_STATE_CHANGED` events.
- Only package identity is read; window content is not retrieved.
- The service cannot perform gestures and does not tap, scroll, type, or change settings.
- Selected package identifiers and Breathe outcomes are stored locally on the device.
- Breathe does not sell or share this information.

## User control

- The app presents a prominent disclosure immediately before opening Android Accessibility settings.
- The user must select “I understand and want to enable this feature” before the settings button is enabled.
- The system settings screen requires the user to enable the service.
- The user can disable the service at any time in Android Settings.
- Android Back, “I’ll do something else,” and Continue remain available in every Breathe moment.

## Review video sequence

1. Launch Breathe from a fresh install.
2. Show the complete prominent disclosure without cutting away.
3. Withhold consent: leave “I understand and want to enable this feature” unchecked and show that “Open Accessibility settings” is disabled, so Android Settings is not opened.
4. Leave Breathe and relaunch it; show the complete disclosure again, still before any Settings navigation.
5. Select the affirmative-consent checkbox and show that “Open Accessibility settings” becomes enabled.
6. Tap “Open Accessibility settings.”
7. Enable “Breathe mindful app pauses” in the Android system UI.
8. Return to Breathe and select one test social app.
9. Open the selected app and show the automatic Breathe moment.
10. Tap Continue and show that the selected app opens.
11. Leave, reopen the selected app, and tap “I’ll do something else.”
12. Show that Breathe returns to Home rather than preventing the user from leaving the moment.
13. Return to Android Accessibility settings and disable Breathe.
14. Reopen the selected app and show that no Breathe moment appears.

## Play Console declaration answers

These are proposed answers for the Accessibility API declaration. They are submission strategy, not policy determinations by Google Play.

| Declaration topic | Proposed answer |
| --- | --- |
| Does the app use `AccessibilityService`? | Yes. |
| Is the service an accessibility tool? | No. The service XML declares `android:isAccessibilityTool="false"`; Breathe does not represent the feature as disability support. |
| What core feature needs it? | The user-selected mindful-pause feature described in **Core feature requiring the API**. It reacts to a selected package becoming foregrounded and offers one pause; it does not block the selected app. |
| Why is the access needed? | **Engineering assessment for submission:** the product needs a package-level foreground-transition signal at the time a selected app is opened. The team has not identified a narrower Android API that supplies that event with equivalent timing for this feature. Re-evaluate this assessment for every release because Play policy requires narrower APIs and permissions where possible. |
| What accessibility data is accessed and used? | Only `event.packageName` from `TYPE_WINDOW_STATE_CHANGED`; it is compared on-device with the user-selected package set to decide whether to launch the Breathe pause UI. No event text, view hierarchy, screenshots, or window content is read. |
| Can the service control another app or device settings? | No. XML declares `canPerformGestures="false"`; the implementation does not invoke accessibility gestures or change settings. The user enables/disables the service in Android Settings. |
| Is content retrieved? | No. XML declares `canRetrieveWindowContent="false"`; the implementation passes only the event package name to its coordinator. |
| Does the service block, autonomously act on, or prevent disabling? | No. It offers a pause activity after a selected-app transition and fails open if launching it fails. The user can use Android Back or Continue to leave the moment and can disable the service in Android Settings. |
| Is the data used for advertising, profiling, account management, analytics, telemetry, sale, or sharing? | No for this service feature. It has no account flow and does not transmit selected-package identifiers, window-transition data, or pause outcomes off-device. |

### Data safety / permission-data-use response

For the Accessibility Service feature, the proposed data-use response is: package identity, selected-package identifiers, and pause outcomes stay on device and are not shared or transmitted off device. Google Play defines Data safety “collection” as transmitting data off a user's device and says solely on-device access need not be declared as collection. On that service-specific basis, this feature has no collected or shared data to declare.

This is **not** a package-wide Data safety “No data collected” answer. Google Play requires a package-wide, accurate declaration that includes data handled by all third-party libraries/SDKs. Before submitting, audit the release artifact and all SDKs, then answer the Data safety form for the whole package accordingly.

## Disclosure mapping

| Disclosure requirement / submission statement | Implemented wording or evidence | Review check |
| --- | --- | --- |
| Identify the sensitive API and data | `accessibility_disclosure_body`: “Breathe uses Android Accessibility access to notice the name of the app in the foreground when an app you selected opens…” | Show the entire disclosure before navigating to Settings. |
| Explain purpose | Same disclosure: “…then show your Breathe moment.” | The feature purpose matches the declaration and video. |
| Explain material limits | Same disclosure: “Breathe does not read screen content, tap buttons, or collect what you do inside apps.” | XML disables window-content retrieval and gestures. |
| Explain storage/control | Same disclosure: “Your selected apps and pause activity stay on this device. You can turn access off at any time in Settings.” | Show consent, Android enablement, and later disablement. |
| Obtain affirmative consent before settings | The checkbox reads “I understand and want to enable this feature.” `Open Accessibility settings` is disabled until it is selected. | First show withheld consent and the disabled button without opening Settings; relaunch Breathe to show the disclosure again; then capture the enabled button and user-initiated Settings navigation. |

## Source-to-declaration consistency check

Checked against the current source tree on 2026-08-03:

| Declaration claim | Source evidence | Result |
| --- | --- | --- |
| Not an accessibility tool | `breathe_accessibility_service.xml`: `isAccessibilityTool="false"` | Matches. |
| Window-state event and package identity only | XML declares `typeWindowStateChanged`; `BreatheAccessibilityService.onAccessibilityEvent` filters `TYPE_WINDOW_STATE_CHANGED` and passes `event.packageName?.toString()`. | Matches. |
| No window-content retrieval | XML: `canRetrieveWindowContent="false"`. | Matches. |
| No gestures | XML: `canPerformGestures="false"`. | Matches. |
| System settings and affirmative consent | `OnboardingScreen` starts `Settings.ACTION_ACCESSIBILITY_SETTINGS`; its button is enabled only when `disclosureAccepted && !hasAccessibilityPermission`. | Matches. |
| Disclosure text and local storage claim | `strings.xml` contains the disclosure and consent text quoted above; the selected-app and intervention-event repositories use the on-device Room database. | Matches for the feature scope. |
| Fail-open behavior | `BreatheAccessibilityService.launchPause` wraps `startActivity` in `runCatching`; a launch failure is logged rather than blocking the selected app. | Matches. |
| Every-moment navigation | `PauseActivity` provides Continue and default Android Back behavior. The visible decline control is labeled “I’ll do something else.” | Matches. The video uses the shipped label and shows both user-choice paths. |

## Reviewer-video recording notes

The numbered sequence above is the current-build recording script. It uses the shipped decline label, **“I’ll do something else,”** which records the declined outcome and returns to Home. Do not edit, accelerate, or cut away from the initial disclosure, withheld-consent/disabled-button state, repeated disclosure after relaunch, affirmative-consent state, Android Settings enable/disable state, selected-app transition, Continue path, decline path, or final disabled-service verification.

Record on a device with one clearly identifiable test social app. Begin at a fresh install, avoid notifications/overlays that conceal the system UI, and use the same release build submitted for review. The video should demonstrate prominent disclosure before Settings, affirmative consent, and non-blocking user choice: Continue returns to the selected app, “I’ll do something else” returns to Home, and after disabling the service the selected app opens without a Breathe moment. Separately, the implementation's `runCatching` around launch is the bounded fail-open behavior if the pause activity cannot be started.

## Policy references and risk notes

### Policy facts

- Google Play says non-accessibility-tool uses of `AccessibilityService` require an Accessibility declaration, clear in-app disclosure of data access/use, and affirmative user consent. It also says to use narrower APIs/permissions where possible and to document Accessibility API use in the Play listing. [Permissions and APIs that Access Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en&rd=1) (accessed 2026-08-03).
- Google Play requires a clear in-app disclosure immediately before requesting a sensitive API capability; a disclosure is not a substitute for a privacy policy or Data safety section. [Best practices for prominent disclosure and consent](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en) (accessed 2026-08-03).
- Google Play's video guidance calls for the app opening, the complete disclosure/consent flow, both consent and non-consent flows (including triggering the disclosure again), and a core feature using the declared capability. [Best practices for prominent disclosure and consent](https://support.google.com/googleplay/android-developer/answer/11150561?hl=en) (accessed 2026-08-03).
- Data safety “collection” means transmission off device. The form is package-wide and must cover third-party SDKs; developers are responsible for accuracy. [Provide information for Google Play's Data safety section](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en) (accessed 2026-08-03).
- Android's service metadata supports the `isAccessibilityTool`, window-content-retrieval, and gesture capabilities used in this review. [Android `AccessibilityServiceInfo` reference](https://developer.android.com/reference/android/accessibilityservice/AccessibilityServiceInfo) (accessed 2026-08-03).

### Submission strategy and risks (not policy determinations)

- Keep the listing, declaration, disclosure, and video limited to package/window transitions, one mindful pause, and user choice. Do not describe the feature as app blocking, content analysis, accessibility/disability support, or automated decision-making.
- Keep `isAccessibilityTool=false`, `canRetrieveWindowContent=false`, and `canPerformGestures=false` in the submitted artifact. Do not add telemetry, account handling, content retrieval, gestures, or data transfer for this feature without revising the disclosure, declaration, Data safety response, review video, and policy assessment.
- The “no narrower API” statement is an engineering conclusion, not a Google Play guarantee. Reconfirm it for the release and be prepared to explain why existing alternatives do not provide equivalent selected-app foreground-transition timing.
- Use the script's current shipped labels in the recording. If a later release changes a label or navigation path, update the declaration, video, and source-to-declaration check before submission. This draft makes no prediction that the declaration will be accepted.
