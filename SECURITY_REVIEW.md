# Breathe security and release review

Reviewed October 5, 2026. Android branch codex/breathe-reliability-and-controls; Chrome version 0.5.0. This is a source/configuration review with build and automated-check evidence, not an independent penetration test or a MASVS certification.

## Assessment

The offline design, narrow permissions and separation of state ownership are sound foundations. Suitable for continued development and controlled testing. Public release still needs release signing, physical-device behavior checks, older-database upgrade tests and final platform policy/store validation.

## Verified boundaries

| Area | Evidence | Limit |
| --- | --- | --- |
| Android network access | Merged release manifest has no INTERNET permission; source has no network client or WebView | This does not stop Android or user-selected document providers from doing their own work |
| Accessibility | Only window-state events; canRetrieveWindowContent=false | Android delivers app identity; there is no guarantee of intervention before content appears |
| Android components | Pause Activity and service private; service requires BIND_ACCESSIBILITY_SERVICE; immutable widget PendingIntent | Launcher Activity must remain exported; OS protection does not defend a compromised/rooted device |
| Local storage | Room in private app storage, explicit migrations, no destructive fallback; database/WAL, snooze and personal-reminder preferences excluded from backup/transfer | No separate application-level database encryption; detailed app/choice history and an optional personal reminder remain until cleared |
| Android export | System document picker; UTF-8 quoting and spreadsheet formula protection | Exported history leaves the app sandbox and follows the user's chosen storage provider |
| Chrome access | Optional per-site host grants; no website content scripts; default extension script policy; no account or external runtime network API | webNavigation receives navigation URLs for event handling; selected full destinations exist temporarily |
| Chrome command boundary | Own-extension sender check; trusted settings/popup URLs; pause mutations require matching top-level tab/document ID | Permissions and URL handling also need normal Chrome/Mac acceptance checks |
| Chrome data | Local aggregate statistics; full URLs/session approvals temporary; safeTarget only HTTP/HTTPS without credentials; textContent for user-supplied labels | Browser-local settings/history are not separately encrypted by the extension |

## Hardening completed in this review

1. Make the Android widget receiver non-exported. System widget updates and same-app refresh broadcasts can work with a private receiver; other ordinary apps no longer have an unnecessary broadcast entry point. Recheck placement/update/removal on a phone before release. [Android widget receiver guidance](https://developer.android.com/develop/ui/compose/glance/create-app-widget).
2. Remove package names, choice outcomes and the attached exception from the pause-recording failure log. Keep a generic failure marker. Debug/verbose log removal already exists in release ProGuard rules; warning/error logs require their own content discipline. [OWASP sensitive logging guidance](https://mas.owasp.org/MASWE/MASVS-STORAGE/MASWE-0005/).

## Automated evidence

- Existing current suites: Android 83 JVM tests; Chrome 24 unit tests and 18 installed-extension Chromium tests. Tests establish covered behavior, not universal security or device reliability.
- npm audit queried the installed Chrome dependency graph and returned zero advisories, including build dependencies. No runtime npm dependency network client is used.
- OSV querybatch checked 107 resolved Maven coordinates/versions from Android releaseRuntimeClasspath and returned no matching advisories. The query snapshot is saved in docs/security-audit-2026-10-05.json; the complete Gradle dependency output is also retained in the local outputs folder. Registry coverage and undisclosed issues remain limits; Android system components are outside this query.
- Android optimized unsigned production APK builds with R8/resource shrinking; merged release manifest has debuggable=false. The previously provided test APK is debug. No local release keystore configuration exists in this checkout, so this does not verify a signed store artifact.
- Selected private-key/token pattern checks in tracked Android text source found no matches. This is a limited scan, not proof that every possible secret is absent.
- Android lint has zero errors and 48 existing warnings. Deprecations and old library baselines need planned maintenance.

## Work before public release

1. Configure and protect the release/upload signing key; test the final signed/minified artifact. Never distribute the debug APK as the public release.
2. Check current snooze/overlay/widget behavior on physical phones, including permission revocation, screen lock, process recreation, reboot, split screen and OEM battery management. New snooze controls have not been device-tested.
3. Add executable Room migration tests from supported historical schemas. exportSchema is currently false, and the existing JVM/SQL checks do not substitute for a real upgrade. Confirm history and selected apps survive an in-place update.
4. Reconcile accessibility disclosure and store declarations with actual behavior and test native Chrome permission acceptance/denial on the target Mac. Store approval is not established by passing builds.
5. Define the local-data threat model and retention policy. App history is private but not separately encrypted. If protection against extracted app files is a requirement, design Keystore-backed encryption and safe migrations; Android's application sandbox alone does not provide that guarantee. [OWASP storage guidance](https://mas.owasp.org/MASVS/05-MASVS-STORAGE/).
6. Maintain dependency advisories and upgrade old AndroidX baselines in tested steps. Chrome also has a minor robustness improvement available: reject an oversized selected backup using File.size before reading the whole file; its parser currently rejects oversized text after the read. This is a user-selected-file availability issue, not a demonstrated remote exploit.

No critical remote data-exfiltration path was found in the reviewed code. That is a bounded finding, not a claim that the app has no vulnerabilities.
