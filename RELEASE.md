# Releasing Breathe

What the build already does, what you still have to do by hand, and what has to be
true before a submission is honest.

## Blocking, before any submission

- [ ] **Verify on a physical device.** Nothing below matters if the app does not
      work. See "Device verification" at the bottom — it is still outstanding and
      covers the app's core mechanism.
- [ ] **Confirm the `targetSdk` requirement** in Play Console. The project now
      targets API 36 (Android 16), which should clear the bar, but the requirement
      moves every August — check rather than assume. Note this was raised from 34
      without a device pass, so the Android 15 and 16 behaviour changes it brings
      (enforced edge-to-edge, stricter foreground-service and overlay rules) are
      unverified. They are in the device checklist below.
- [ ] **Turn on GitHub Pages** (one switch, then the URL is live). The page itself is
      committed at `docs/index.html`. In the repo: **Settings → Pages → Build and
      deployment → Source: "Deploy from a branch" → Branch: `main`, folder:
      `/docs` → Save.** First publish takes a minute or two, after which the policy
      is at:

      https://dgraciano8424.github.io/breathe/

      Paste that into Play Console under **App content → Privacy policy**. Confirm it
      loads in a private window first — Play rejects links that need a login, and a
      404 here is a common cause of review delay.

## One-time setup

### 1. Generate an upload key

```
keytool -genkeypair -v -keystore breathe-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias breathe
```

Keep this file. If you lose it you cannot ship an update to the same listing
without asking Google to reset the upload key.

### 2. Point the build at it

Create `keystore.properties` at the repo root — it is gitignored, and so are
`*.jks` and `*.keystore`:

```properties
storeFile=breathe-upload.jks
storePassword=...
keyAlias=breathe
keyPassword=...
```

Without this file the release build still compiles, it just comes out unsigned.
That keeps the build working for anyone without the key.

## Building

```
./gradlew bundleRelease     # AAB — what Play wants
./gradlew assembleRelease   # APK — for sideloading a release build yourself
```

Outputs land in `app/build/outputs/`. **Upload `mapping.txt` from
`app/build/outputs/mapping/release/`** with the bundle, or every crash report from
the store arrives as unreadable obfuscated names.

## What the build already handles

- R8 code shrinking and resource shrinking, with keep rules for the reflective
  paths (Gson DTOs, Room entities, Retrofit, the widget the system instantiates
  by name).
- `Log.d`/`Log.v` stripped from release builds; warnings and errors kept.
- Line numbers preserved for deobfuscation.
- Network logging gated to debug builds.
- Backup disabled and the database excluded from cloud backup and device transfer.
- Foreground service type declared as `specialUse` with its subtype property.

## Play Console answers

### Data safety form

Breathe collects and transmits **no** user data, so the form is short:

- Does your app collect or share any of the required user data types? **No.**
- Is all data encrypted in transit? Not applicable — no user data is transmitted.
- Do you provide a way for users to request data deletion? Uninstalling removes
  everything; there is no server-side data.

The only network call is an unauthenticated GET to `zenquotes.io` for quotation
text, carrying no user information.

### Permission declarations

Two permissions will be challenged; have these answers ready.

- **`SYSTEM_ALERT_WINDOW` (display over other apps).** Core functionality: the
  pause screen must appear over the app being opened. There is no alternative —
  launching an Activity from the background is unreliable on Android 10+, which is
  precisely why this app uses an overlay.
- **`PACKAGE_USAGE_STATS` (usage access).** Core functionality: the app cannot know
  when to intervene without knowing which app is in the foreground. Read on-device
  only, never transmitted.

`QUERY_ALL_PACKAGES` is deliberately **not** declared — the app uses a `<queries>`
launcher-intent filter instead, which needs no justification.

### Store listing, still to write

- [ ] Short description (80 characters)
- [ ] Full description (4000 characters)
- [ ] Feature graphic, 1024×500
- [ ] Phone screenshots, at least two — the pause screen and the home screen are
      the obvious pair
- [ ] App category and content rating questionnaire

## Device verification (outstanding)

None of this has been run on real hardware. In rough order of risk:

1. **Does the pause screen appear?** Open a monitored app. This is the whole
   product, and the overlay implementation has never been observed working.
2. **Does the v4 migration survive an upgrade?** Install over an existing build
   rather than a clean one, and confirm previously monitored apps are still there
   with their pause durations.
3. **Does the widget work?** Long-press the home screen, add it, then take a pause
   and confirm the count moves.
4. **Does the release build behave like the debug build?** R8 changes things.
   Sideload `assembleRelease` output and repeat step 1 — reflective paths that
   survive in debug can still break after shrinking.
5. **Behaviour changes from the `targetSdk` bump**, on an Android 15 or 16 device:
   edge-to-edge is enforced rather than opt-in, so check nothing is drawn under the
   status or navigation bars — particularly the pause screen, which is full-bleed.
   Confirm the foreground service still starts and the overlay still draws.
6. **Onboarding on an older device** (API 26–28) if you can find one, since that
   path had an API-level crash that was only recently fixed.
