# Breathe Zen-First Product Design

**Date:** 2026-08-02  
**Status:** Approved direction; awaiting written-spec review  
**Platform:** Android 10 and newer  
**Current stack:** Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Room

## Purpose

Breathe creates a calm space between impulse and action. When a person opens an app they selected as distracting, Breathe guides one breath and lets them decide whether they still want to continue.

The product succeeds when people become more intentional with their attention. It does not succeed by maximizing time spent inside Breathe.

## Product Promise

> A calm space between impulse and action. Take one breath, then choose intentionally.

The public promise depends on a timely pause appearing when a selected app opens. A notification that the user must notice and tap is not equivalent to this promise.

## Audience

Breathe serves a broad audience through a simple default experience:

- People who casually want to reduce social-media use.
- People who struggle with compulsive or automatic app opening.
- ADHD and neurodivergent users who benefit from predictable external structure.
- People who want gentle support rather than punitive blocking.

The first release does not expose separate gentle, balanced, and strict modes. One clear and accessible experience serves all users; deeper control is deferred until usage demonstrates a need.

## Experience Principles

1. **Calm, not clinical.** User-facing copy says “Breathe moment” or “pause,” never “intervention.”
2. **Predictable.** The core experience behaves the same way every time.
3. **Nonjudgmental.** Continuing is a valid choice, not a failure.
4. **Fail open.** Breathe never traps a user or blocks safety-critical access.
5. **Honest progress.** The app reports observed events, not inflated counterfactual estimates.
6. **Quiet reward.** Progress is visible without confetti, guilt, streak loss, scores, or social comparison.
7. **Accessible by default.** Skip, Back, reduced motion, large text, and screen-reader support are part of the free core.
8. **Private by default.** Selected apps, intentions, timestamps, and choices remain local in the initial product.
9. **No attention extraction.** Breathe has no ads and sends no notifications merely to increase app opens.

## Competitive Lessons

EasyQuit demonstrates that live progress, personal motivations, milestones, and a simple presentation can make an invisible behavior change feel tangible. Its store presence exceeds one million Android downloads and its positive reviews repeatedly mention progress statistics and personal motivations.

Breathe should borrow the idea of making progress visible, but not EasyQuit’s dense dashboard, badge volume, intrusive advertising, or unverified calculations. Recent EasyQuit reviews describe advertisements, incorrect counters, confusing data, and limited free value as reasons to leave.

Relevant sources:

- [EasyQuit Google Play listing](https://play.google.com/store/apps/details?id=com.herzberg.easyquitsdrinking)
- [EasyQuit review analysis](https://chrome-stats.com/d/com.herzberg.easyquitsdrinking/reviews)
- [RevenueCat State of Subscription Apps 2025](https://www.revenuecat.com/state-of-subscription-apps-2025)
- [Google Play subscription transparency policy](https://support.google.com/googleplay/android-developer/answer/9900533)

## V1 Product Boundary

### Included

- Automatic detection of user-selected app launches, contingent on the feasibility gate below.
- One optional guided inhale and exhale.
- An always-available Skip action.
- A neutral choice to return or continue.
- App selection and per-app enable controls.
- Temporary and indefinite global pause controls.
- Minimal onboarding and permission recovery.
- A calm Home screen with today’s observed events and an optional personal intention.
- Local event persistence with explicit loading, empty, error, and disabled states.
- Accessibility, privacy, reliability, and release-policy hardening.

### Explicitly deferred

- Schedules and quiet hours.
- Multiple breaths or breathing modes.
- Follow-up reminders.
- Quotes, rotating prompts, or content libraries.
- Detailed analytics and pattern insights.
- Time-reclaimed estimates.
- Streaks, badges, scores, goals, achievements, and leaderboards.
- Themes, soundscapes, and extensive customization.
- Widgets.
- Accounts, cloud sync, family plans, and social features.
- Subscription and trial mechanics.

Deferred features are candidates, not promises. Each must earn inclusion through user evidence and must preserve the calm core.

## Existential Android Feasibility Gate

The current foreground service directly launches an Activity, but Android 10 and newer restrict background Activity starts. A foreground service is not a general exemption. Before product UI expansion, Breathe will run a time-boxed Accessibility Service and Google Play policy spike.

The prototype must be deliberately narrow:

- Observe package/window changes only for detecting selected apps.
- Do not read window content.
- Do not perform gestures or control another app.
- Do not describe Breathe as an accessibility tool when it is not one.
- Present a prominent disclosure before the system permission screen.
- Require affirmative user consent.
- Explain what is observed, why it is needed, what is stored, and how to turn it off.

The spike passes only if both conditions are met:

1. Automatic Breathe moments appear reliably and without loops across the required Android/device matrix.
2. The Accessibility Service use has a credible Google Play declaration, disclosure, and review path.

If either condition fails, work stops at this gate. The product owner then chooses between reframing Breathe as a notification-driven mindful prompt or postponing a consumer Play release. The notification fallback must not silently retain the “pause before opening” promise.

Relevant platform guidance:

- [Android background Activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal)
- [Google Play Accessibility Service policy](https://support.google.com/googleplay/android-developer/answer/10964491)
- [Google Play package visibility policy](https://support.google.com/googleplay/android-developer/answer/10158779)

## Core User Journey

### 1. Onboarding

Onboarding contains three short steps:

1. **Promise:** “A small pause before the apps you choose.”
2. **Selection:** “Which app would you like to pause before?” The user selects at least one app.
3. **Permission and preview:** Breathe explains the required access in plain language, opens the system screen only after consent, verifies the resulting state, and previews the exact Breathe moment.

Adding a personal intention is optional and occurs on Home, not as a required setup field.

### 2. Selected App Opens

The detector creates one foreground-session identity for the selected package. Duplicate events for the same session do not open another Breathe moment.

### 3. Breathe Moment

The screen uses Breathe’s tranquil ocean palette and a clear orb animation:

- Intro: “Pause for one breath.”
- Inhale: the orb expands while displaying “Breathe in.”
- Exhale: the orb contracts while displaying “Breathe out.”
- Decision: “Do you still want to open {App name}?”
- Primary calm action: “Not right now.”
- Secondary action: “Continue to {App name}.”
- Persistent text action: “Skip breath.”

The initial cycle lasts no more than 15 seconds. Skip and Android Back work immediately, including while the animation runs.

### 4. Outcome

- **Not right now:** record the choice durably, then return the user to the prior safe screen.
- **Continue:** record the choice durably, open the selected app exactly once, and suppress further Breathe moments for that app during a short foreground cooldown so the user cannot enter a loop.
- **Skip or Back:** fail open without judgment. Record a skipped/dismissed outcome only if persistence can occur without delaying escape.

No paywall, promotion, achievement, quote, personal intention, or subscription message appears inside the Breathe moment.

## Screen Structure

### Home

Home contains one vertically scrollable calm surface:

- “Today” heading.
- Observed Breathe moments completed today.
- Observed “Not right now” choices today.
- Optional personal intention with add, edit, and remove actions.
- “Choose apps” action.
- Permission/monitoring health when attention is required.
- Loading, empty, error, disabled, and ready states.

After event integrity and core-loop reliability pass, Home may receive a single compact seven-day event view. It shows observed daily pause and “Not right now” counts with accessible text equivalents. It has no streak, score, comparison, success rate, goal, or missed-day warning.

### Choose Apps

- Searchable list of launchable user apps.
- Per-app enabled switch.
- Clear loading, empty, permission, and error states.
- No per-app schedule or breathing configuration in V1.

### Pause Breathe

The global control offers exactly:

- “Pause for 15 minutes.”
- “Pause until tomorrow.”
- “Turn Breathe off.”

Home always explains the current paused or disabled state and provides a direct resume action.

### Settings

Settings contains only operational controls required for trust:

- Permission status and recovery.
- Reduced-motion behavior.
- Optional haptics and sound, both off by default if included.
- Privacy explanation and local-data deletion.
- About/support and required legal links.

## Conceptual Architecture

The existing MVVM, Repository, Hilt, and Room structure remains. V1 adds focused boundaries rather than a broad rewrite.

- **Selected-app configuration:** owns the set of packages Breathe observes and each enabled state.
- **Detection service:** observes foreground package transitions, applies selected-app and global-pause rules, creates session identities, and suppresses duplicates.
- **Breathe moment coordinator:** turns an eligible session into one visible pause and guarantees at-most-once navigation to the selected app.
- **Outcome repository:** persists completed, continued, returned, and skipped outcomes idempotently.
- **Daily summary:** derives Home counts only from persisted observed events.
- **Permission health:** exposes ready, permission-needed, paused, disabled, and unsupported states to onboarding and Home.

The detection service must not query Room every 500 milliseconds. It consumes a cached in-memory selected-package set that updates reactively when configuration changes.

## Data and Integrity Rules

Each Breathe session has a stable identifier and records:

- Selected package identifier.
- Detection timestamp.
- Breathe moment shown timestamp when presentation succeeds.
- Outcome: returned, continued, skipped, or dismissed.
- Outcome timestamp.

Event recording is a suspending operation. The Activity does not finish a completed choice until the Room write succeeds or a bounded failure path informs the user and permits safe exit. Retries with the same session identifier cannot double-count.

V1 does not perform a 28-day UsageStats scan in the interaction path. It does not calculate time reclaimed. Home counts come solely from persisted Breathe events.

## Accessibility Contract

- Motion communicates timing but is never the only signal.
- Respect Android reduced-motion settings; provide a static or low-motion breath indicator.
- Screen readers announce “Breathe in,” “Breathe out,” and the final question, not continuous animation progress.
- Support 200% font scale without clipped content or unreachable actions.
- Support narrow phones, landscape, and adaptive layouts.
- Interactive targets are at least 48dp.
- Focus order follows the visual and decision sequence.
- Color is not the only indicator of state.
- Haptics and sound remain optional.
- Skip and Back never require completion of the breath.
- Emergency, phone, authentication, permission, system Settings, and other safety-critical flows are never selected or intercepted.

## Error and Recovery Behavior

- **Accessibility or usage permission missing:** do not pretend monitoring is active. Home shows a direct recovery action.
- **Service stopped or killed:** recover through the system-managed lifecycle when permitted and surface a degraded state when not operational.
- **Duplicate detection:** suppress using foreground-session identity and cooldown rules.
- **Pause UI failure:** fail open and never trap the selected app.
- **Persistence failure:** keep choices actionable, show a brief plain-language message, and avoid reporting unrecorded progress.
- **Selected app unavailable:** remove or mark the selection unavailable without crashing.
- **Process recreation:** restore the current Breathe session without reopening the destination more than once.
- **Reduced motion or animation failure:** use the static timing alternative.

## Privacy and Policy

- Store selected apps, optional intention, session timestamps, and outcomes locally.
- Do not collect window contents, contacts, messages, credentials, or browsing content.
- Disable Android backup or explicitly exclude the Room database and preferences containing selected packages, intentions, timestamps, and outcomes.
- Replace `QUERY_ALL_PACKAGES` with launcher-intent visibility unless a narrower method is proven insufficient and policy-approved.
- Gate HTTP logging to debug builds.
- Provide local-data deletion without requiring an account.
- Keep privacy and accessibility capabilities outside any future paywall.

## Ethical Retention

Breathe’s return loop is utility-driven:

1. An automatic impulse triggers a Breathe moment.
2. The breath creates enough space for conscious choice.
3. Home makes observed intentional choices visible.
4. Quiet continuity helps the user recognize change over time.
5. Optional personalization increases relevance without increasing pressure.

The product does not use ads, punitive streaks, expiring rewards, social comparison, misleading estimates, notification spam, or deliberately difficult cancellation.

## Future Subscription Posture

Subscription work begins only after reliable automatic pausing and value-qualified week-four retention are demonstrated.

The free core permanently includes:

- At least one selected app.
- The one-breath Breathe moment.
- Skip, Back, Continue, and Not right now.
- Accessibility, privacy, data deletion, and global disable controls.
- Basic observed daily progress.

Potential Breathe Plus value comes from convenience and personalization:

- Additional selected apps.
- Advanced schedules.
- Deeper pattern insights.
- Optional reflections and intentions.
- Widgets and expanded personalization.
- Backup/sync only with explicit consent and an appropriate privacy design.

No subscription offer appears during onboarding before value is experienced or inside a Breathe moment. Any trial clearly states its duration, renewal price, renewal timing, and cancellation path. Annual can be presented as best value while monthly remains available.

## Success Metrics

### North-star metric

**Value-qualified week-four retention:** the percentage of activated users who, during week four, complete at least three Breathe moments and choose “Not right now” at least once.

### Guardrails

- Permission-onboarding completion rate.
- Percentage of eligible app openings that produce exactly one Breathe moment.
- Median time from selected-app detection to Breathe moment presentation.
- Duplicate-presentation rate.
- Completed-choice persistence rate.
- Skip and Back latency.
- Global-disable and app-disable rates.
- Crash- and ANR-free session rates.
- Qualitative reports that Breathe feels calm, helpful, respectful, and easy to disable.

A high return rate is not considered success if it is accompanied by loops, coercion, difficulty disabling, excessive skipping, or user frustration.

## Verification Strategy

### Unit tests

- Selected-package eligibility.
- Session identity and duplicate suppression.
- Delayed event handling and rapid app switching.
- Service/process restart and last-known state recovery.
- Permission grant, denial, revocation, and recovery.
- Temporary global-pause expiration.
- Idempotent outcome persistence and failure handling.
- Daily summary accuracy.

### Instrumented flow tests

- Selected app opens, Breathe moment appears, breath completes, and each choice persists.
- Skip and Back work during every animation phase.
- Continue launches the destination once.
- Not right now returns safely.
- Activity recreation and process death do not duplicate events or navigation.

### Accessibility and layout tests

- TalkBack focus and announcements.
- Android reduced-motion behavior.
- 200% font scale.
- Narrow phone and landscape layouts.
- Contrast and non-color state indicators.

### Real-device matrix

- Pixel and Samsung devices.
- Android 10, 12 or 13, 14, and the current Android release.
- Reboot, service kill, process death, Doze, battery saver, screen off/on, rapid app switching, and permission revocation.

### Release gates

- Accessibility feasibility and Play-policy spike passes.
- No direct background `startActivity()` from an ordinary foreground service.
- No duplicate Breathe moment in one foreground session.
- Completed choices persist in stress testing.
- No long UsageStats scan occurs on the main or interaction path.
- Every degraded permission/service state is visible and recoverable.
- Unit tests, instrumented tests, lint, and release assembly pass.

## Ordered Scopes

1. **Automatic-pause feasibility:** prototype the narrow Accessibility Service and produce a written go/no-go decision.
2. **Core reliability:** harden lifecycle, duplicate suppression, permission recovery, durable outcomes, and interaction-path performance.
3. **Breathe moment:** implement the ocean breathing orb and accessible one-breath decision experience.
4. **Simple onboarding:** promise, app selection, disclosure, permission verification, and preview.
5. **App management:** app selection, per-app enabled state, and temporary/global pause controls.
6. **Calm Home:** today’s observed counts, optional intention, operational health, and all UI states.
7. **Seven-day continuity:** add the compact event view only after event-integrity gates pass.
8. **Visual refinement:** unify typography, spacing, color, motion, dark mode, icons, transitions, and adaptive layouts.
9. **Release readiness:** complete privacy, policy, performance, battery, device, test, and store requirements.
10. **Retention and subscription validation:** measure sustained voluntary value before designing Breathe Plus.

Each scope receives its own implementation plan, independent testable deliverable, and completion gate. Work does not proceed past Scope 1 if automatic pausing lacks a reliable and credible Play distribution path.

## Out of Scope for This Design

- iOS, desktop, browser extension, and cross-platform sync.
- Clinical diagnosis, treatment, therapy, or medical claims.
- Parental control, enterprise device management, or hard device lockdown.
- Reading or modifying the content of another app.
- Guaranteed prevention of access to any selected app.
