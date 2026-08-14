# Play Store listing copy

Draft copy for the Play Console listing. Paste each block into the matching field.
Character counts are noted against Play's limits — recount after any edit, since the
Console rejects an over-length field rather than truncating it.

Everything here describes behaviour that exists in the tree today. Nothing is
aspirational: if a claim below stops being true, change the claim.

> Rewritten against `90f7e30`, which replaced the polling foreground service with an
> AccessibilityService and deleted the quote feature outright. An earlier draft of this
> file advertised the quotes, explained a foreground-service notification, and named
> usage access as the trigger for the pause. All three are now wrong. `PRIVACY.md` and
> `RELEASE.md` are the sources of truth for the permission wording — keep the three
> documents saying the same thing.

## Short description (limit 80)

```
A mindful pause before you open the apps that pull you in. No ads, no tracking.
```

79 characters.

### Alternates, if the above reads wrong to you

```
Pause before you open the apps that pull you in. Fully offline, nothing tracked.
```
Exactly 80 characters — at the limit, with no room to edit. Leans on the app having
no internet access at all.

```
A breath between the reach and the scroll. Free, private, and fully offline.
```
76 characters.

## Full description (limit 4000)

```
You reach for your phone without deciding to. Breathe puts one calm moment between the reach and the scroll.

Choose the apps that pull you in. When you open one, Breathe surfaces a quiet full-screen pause — a breathing rhythm to follow and an honest question: do you actually want this right now?

Then you choose. Continue, or do something else. Both answers are fine. The point is that you made one.

WHAT A PAUSE LOOKS LIKE

• A breathing animation to settle into, set in a calm ocean palette
• A gentle note of how many times you have already opened this app today
• Four honest reasons to tap, if you want to name it: Bored, Habit, Escaping, Curious
• A suggestion of something else you could do with the time
• Your choice: continue to the app, or step away

SET IT UP THE WAY YOU WANT

• Pick any apps on your phone — the picker highlights what you have used most this week, so the ones worth pausing are easy to find
• Set a different pause length for each app. Give the ones that pull hardest a longer breath before the continue button unlocks
• Add or remove apps whenever you like

SEE WHAT IT ADDS UP TO

• Time won back, today and across the week
• How often you chose presence over scrolling
• Which apps you pause most
• A progress path that grows as the reclaimed hours add up
• A home-screen widget with today's pause count, if you want the reminder in view

PRIVATE BY DESIGN

Breathe has no internet access. It does not request the internet permission at all, which means it is not merely unwilling to send your data anywhere — it is incapable of it. There are no accounts, no analytics, no advertising, and no crash reporting.

Your list of apps, your pauses, and your reasons live in a database on your phone. Device backup is switched off for that data, so it is not swept into a cloud backup or a phone-to-phone transfer either. Uninstalling deletes all of it.

Breathe is free. There is nothing to buy inside it and nothing to subscribe to.

ABOUT THE ACCESSIBILITY PERMISSION

Breathe asks for accessibility access, and you are right to be careful about that — it is a powerful permission and plenty of apps abuse it. Here is exactly what Breathe does with it.

It listens for one thing: the name of the app that just came to the front. That is the only way on Android to know a pause is due, and knowing it as the app opens is what lets the pause appear before the feed draws.

Breathe cannot read the contents of your screen. The service is configured to receive window-change events only, with screen-content retrieval switched off, so the text of your messages, your passwords, and everything you type are not available to it. It never taps, types, or acts on your behalf. And with no internet permission, nothing it observes can leave your phone.

THE OTHER PERMISSIONS

• Display over other apps — so the pause can appear over the app you are opening. Without it, the pause cannot reliably show up at all.
• Usage access — optional. It only enriches your statistics with time spent per app. Breathe works fully without it, and will not nag you for it.

A NOTE ON WHAT THIS IS NOT

Breathe does not lock you out, shame you, or gamify your attention. There is no streak to protect and no penalty for continuing. Friction is the entire feature — one deliberate breath, then your own decision.

Requires Android 8.0 or newer.
```

3,397 characters — inside the 4,000 limit, with room to add anything you want.

### Why the accessibility section is that long

It is the single biggest reason a privacy-minded user bounces off the install page, and
Play reviews accessibility-permission apps carefully. Saying plainly what the service
can and cannot see — and pairing it with the absent internet permission, which is
checkable — is worth more than the space it costs. Shorten it only if you are willing
to trade install-page trust for room you do not currently need.

## Still to produce (needs a running app)

- [ ] Feature graphic, 1024×500
- [ ] Phone screenshots, at least two. The pause screen and the home screen are the
      obvious pair; the stats screen is a strong third once there is real data behind it.
- [ ] App category — "Health & Fitness" is the better fit than "Productivity" given the
      framing, but check what comparable apps sit under before committing.
- [ ] Content rating questionnaire.

## Claims to re-check before publishing

These are true as of `90f7e30`. They are the ones that would become false first:

- "no internet access ... does not request the internet permission" — the strongest
  claim here and the easiest to break. Re-adding `INTERNET` for any reason falsifies it,
  changes the Data safety form, and contradicts `PRIVACY.md`.
- "cannot read the contents of your screen" — depends on
  `canRetrieveWindowContent="false"` and the window-state-only event filter in
  `app/src/main/res/xml/accessibility_service_config.xml`. Verify before publishing.
- "no analytics, no advertising, no crash reporting" — one SDK away from false.
- "Usage access — optional ... works fully without it" — true since `90f7e30`. It was
  not true before.
- "Requires Android 8.0 or newer" — tracks `minSdk 26`.
- The described pause behaviour still has not been observed on hardware, and detection
  was rebuilt since the last audit. See the device checklist in `RELEASE.md`; a listing
  describing a pause that does not appear is the worst version of this problem.
