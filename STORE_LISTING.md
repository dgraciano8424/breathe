# Play Store listing copy

Draft copy for the Play Console listing. Paste each block into the matching field.
Character counts are noted against Play's limits — recount after any edit, since the
Console rejects an over-length field rather than truncating it.

Everything here describes behaviour that exists in the tree today. Nothing is
aspirational: if a claim below stops being true, change the claim.

## Short description (limit 80)

```
A mindful pause before you open the apps that pull you in. No ads, no tracking.
```

79 characters.

### Alternates, if the above reads wrong to you

```
Pause before you open the apps that pull you in. Private, free, no tracking.
```
76 characters.

```
A breath between the reach and the scroll. Free, private, and ad-free.
```
70 characters.

## Full description (limit 4000)

```
You reach for your phone without deciding to. Breathe puts one calm moment between the reach and the scroll.

Choose the apps that pull you in. When you open one, Breathe surfaces a quiet full-screen pause — a breathing rhythm to follow, a line worth reading, and an honest question: do you actually want this right now?

Then you choose. Continue, or do something else. Both answers are fine. The point is that you made one.

WHAT A PAUSE LOOKS LIKE

• A breathing animation to settle into, set in a calm ocean palette
• A short reflective quote, different each time
• A gentle note of how many times you have already opened this app today
• Four honest reasons to tap, if you want to name it: Bored, Habit, Escaping, Curious
• A suggestion of something else you could do with the time
• Your choice: continue to the app, or step away

SET IT UP THE WAY YOU WANT

• Pick any apps on your phone — the picker highlights what you have used most this week, so the ones worth pausing are easy to find
• Set a different pause length for each app. Give the ones that pull hardest a longer breath before the button unlocks
• Add or remove apps whenever you like

SEE WHAT IT ADDS UP TO

• Time won back, today and across the week
• How often you chose presence over scrolling
• Which apps you pause most
• A progress path that grows as the reclaimed hours add up
• A home-screen widget with today's pause count, if you want the reminder in view

PRIVATE BY DESIGN

Breathe collects nothing. There are no accounts, no analytics, no advertising, and no crash reporting. Your list of apps, your pauses, and your reasons live in a database on your phone and are never uploaded. Device backup is switched off for that data, so it is not swept into a cloud backup or a phone-to-phone transfer either. Uninstalling deletes all of it.

The app makes one network request, and only one: fetching quotation text from the public ZenQuotes service. It carries nothing about you, your device, or the apps you use.

Breathe is free. There is nothing to buy inside it and nothing to subscribe to.

PERMISSIONS, AND WHY

Breathe asks for two permissions that Android treats as sensitive. Here is exactly what each is for.

• Usage access — so Breathe can tell which app you just opened, which is the only way it knows a pause is due. It also powers your statistics. Read on your device, never transmitted.
• Display over other apps — so the pause can appear over the app you are opening. Without it, the pause cannot reliably show up at all.

It also runs a foreground service, which is why you will see a quiet ongoing notification: that service is what notices when you open a paused app. Android requires that notification, and it is a fair trade — an app watching for launches should say so.

A NOTE ON WHAT THIS IS NOT

Breathe does not lock you out, shame you, or gamify your attention. There is no streak to protect and no penalty for continuing. Friction is the entire feature — one deliberate breath, then your own decision.

Requires Android 8.0 or newer.
```

3,085 characters — comfortably inside the 4,000 limit, with room to add anything
you want.

## Still to produce (needs a running app)

- [ ] Feature graphic, 1024×500
- [ ] Phone screenshots, at least two. The pause screen and the home screen are the
      obvious pair; the stats screen is a strong third once there is real data behind it.
- [ ] App category — "Health & Fitness" is the better fit than "Productivity" given the
      framing, but check what comparable apps sit under before committing.
- [ ] Content rating questionnaire.

## Claims to re-check before publishing

These are true as of this draft. They are the ones that would become false first:

- "no advertising, no analytics, no crash reporting" — stays true only while no SDK is
  added. Adding Crashlytics or similar makes this false and changes the Data safety form.
- "one network request ... ZenQuotes" — see `PRIVACY.md`, which must agree with this.
- "Requires Android 8.0 or newer" — tracks `minSdk 26`.
- The described pause behaviour has not yet been observed on hardware. See the device
  checklist in `RELEASE.md`; a listing describing a pause that does not appear is the
  worst version of this problem.
