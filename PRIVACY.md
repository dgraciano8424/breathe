# Breathe — Privacy Policy

**Last updated:** 2026-08-07

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

- **Usage access** (`PACKAGE_USAGE_STATS`) — to detect which app is currently in
  the foreground, so Breathe knows when to offer a pause, and to estimate how long
  your typical session with an app lasts. This data is read on the device and is
  never transmitted.
- **Display over other apps** (`SYSTEM_ALERT_WINDOW`) — to draw the pause screen
  over the app you are opening. Without it, the pause cannot appear reliably.
- **Foreground service** — to keep the monitor running so it can notice when you
  open a paused app.
- **Run at startup** (`RECEIVE_BOOT_COMPLETED`) — so monitoring resumes after you
  restart your phone, without you having to reopen the app.
- **Internet** — used for one thing only: fetching inspirational quotes from the
  public ZenQuotes API to display on the pause screen. No information about you,
  your device, or your app usage is included in that request.
- **Vibrate** — for haptic feedback in the app's interface.

## Third parties

Breathe contacts one third-party service: **ZenQuotes** (`zenquotes.io`), to fetch
quotation text. The request sends no personal data and no identifiers beyond what
any HTTPS request necessarily reveals (such as your IP address, which is visible to
any server you connect to). Breathe has no analytics, no advertising, no crash
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
