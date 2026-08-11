# Breathe — Privacy Policy

**Published at:** https://dgraciano8424.github.io/breathe/
**Last updated:** 2026-08-10

> The published copy is `docs/index.html`, which is what Play Console links to.
> Edit both together — a policy that contradicts itself between two public copies
> is worse than either one alone.

Breathe adds a mindful pause before you open apps you have chosen to be mindful
about. This document describes exactly what it does with information about you.

## The short version

**Breathe does not collect, transmit, or share any personal data.** Everything it
records stays on your device, and is deleted when you uninstall the app.

## What Breathe stores on your device

Breathe keeps a local database on your phone containing:

- The apps you have chosen to pause before opening, and the pause length you set
  for each.
- A record of each pause: which app, when, whether you continued or chose not to,
  the optional reason you tapped, and an estimate of the minutes you reclaimed.

This database is used to show you your own statistics and progress. It is never
uploaded anywhere. Device backup is disabled for it, so it is excluded from cloud
backups and from device-to-device transfer.

## Permissions, and why each is needed

- **Accessibility access** — to detect which app has just come to the front, so
  Breathe knows when to offer a pause. It reads only the name of the app being
  opened: never the contents of your screen, your messages, or anything you type.
  It never performs actions on your behalf.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — to draw the pause screen
  over the app you are opening. Without it, the pause cannot appear reliably.
- **Usage access** (`PACKAGE_USAGE_STATS`) — optional. Used only to show how long
  you have spent in the apps you monitor, and to estimate the time a pause saved
  you. Breathe works without it. This data is read on the device and is never
  transmitted.
- **Vibrate** — for haptic feedback in the app's interface.

## Third parties

None. Breathe does not request the `INTERNET` permission, so it is incapable of
sending anything anywhere. There are no analytics, no advertising, no crash
reporting service, and no user accounts.

## Children

Breathe is not directed at children and does not knowingly collect information from
anyone.

## Deleting your data

Uninstalling Breathe removes its database and everything in it. You can also clear
it at any time from Android Settings → Apps → Breathe → Storage → Clear data.

## Changes

If this policy changes, the "last updated" date above will change with it.

## Contact

Questions about this policy: dgraciano8424@gmail.com
