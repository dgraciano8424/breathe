# Privacy Policy — Breathe

**Last updated:** 2026-08-10

Breathe helps you pause before opening apps you'd rather use less. To do that it needs
to know which app you're opening. This document explains exactly what that means.

**The short version: everything stays on your phone. There is no account, no server, no
analytics, and no advertising.**

---

## What Breathe accesses

**Accessibility access**
Breathe uses Android's accessibility service to detect which app has just come to the
front. That is the only way to show your pause before the app opens. It reads only the
package name of the app being opened — never the contents of your screen, your messages,
or anything you type — and it never performs actions on your behalf.

**Usage access (`PACKAGE_USAGE_STATS`) — optional**
Android grants this only when you turn it on manually in Settings, and Breathe works
without it. It is used solely to show how long you've spent in the apps you monitor, and
to estimate the time a pause saved you.

**Installed app list**
Breathe lists the apps that have a launcher icon, so you can pick which ones to monitor.
It uses Android's scoped package-visibility declaration, not the broad
`QUERY_ALL_PACKAGES` permission.

**Display over other apps (`SYSTEM_ALERT_WINDOW`)**
Used to show the breathing pause on top of the app you're opening.

**Notifications (`POST_NOTIFICATIONS`)**
Used only for the persistent, silent notification Android requires while monitoring runs.

---

## What Breathe stores

On your device, in a private app database:

- The apps you've chosen to monitor
- For each pause: the app, a timestamp, whether you continued or turned back, an estimate
  of time saved, and — only if you tap one — the reason chip you selected
- Your pause-length preference

History older than a year is deleted automatically, and you can clear all of it at any
time from Settings.

This data is **excluded from Google cloud backup and device-to-device transfer**
(`allowBackup="false"`), so it does not leave your phone. Uninstalling Breathe deletes it.

---

## What Breathe sends off your device

**Nothing.** Breathe does not request the `INTERNET` permission, so it is incapable of
sending anything anywhere. There is no account, no server, no analytics SDK, no crash
reporting, no advertising SDK, and no tracking of any kind.

---

## What Breathe never does

- Sell or share your data — there is nobody to share it with
- Read the contents of your screen, your messages, or anything inside other apps
- Track your location
- Require an account or collect your email

---

## Your control

- Stop monitoring any app at any time from the home screen
- Turn monitoring off entirely from Settings, or in Android's accessibility settings
- Delete your entire history from Settings → Your data → Clear history
- Revoke any permission in Android Settings
- Uninstall to delete all stored data permanently

---

## Children

Breathe is not directed at children under 13 and does not knowingly collect data from them.

---

## Changes

Material changes to this policy will be reflected in the "Last updated" date above and in
the app's release notes.

## Contact

Questions: dgraciano8424@gmail.com
