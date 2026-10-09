# Run the Android App

Requirements: **Android Studio Ladybug or newer**, JDK 17.

## Build it

```bash
cd RaceControlAndroid
./gradlew assembleDebug        # or open the folder in Android Studio
```

Then run on an emulator or a connected device.

::: tip First build
Dependency versions are pinned in `gradle/libs.versions.toml`. If Android Studio offers upgrades, they should be safe to accept: nothing in the app depends on APIs newer than the versions listed.
:::

## Point it at the backend

[Start the backend](/getting-started/backend) first. The app defaults to `http://10.0.2.2:8000`, the emulator's alias for `localhost` on your machine, so an emulator needs no setup.

To change the address, in the app go to **⋮ overflow menu → Settings → Server address**.

| Running on | Address |
|---|---|
| Emulator | `http://10.0.2.2:8000` (the default) |
| Physical device | `http://<your-machine-LAN-IP>:8000`, for example `http://192.168.1.20:8000` |
| Deployed | Your HTTPS URL |

A physical device cannot see `10.0.2.2`. Your machine and device must be on the same network.

Tap **Test Connection**. It probes the unauthenticated `/api/health` first, then an authenticated endpoint, so a bad token is reported there rather than as errors scattered across the app.

## Cleartext HTTP

Debug builds permit plain HTTP to any host. **Release builds only permit cleartext to `localhost` and `10.0.2.2`.** Anything else must be HTTPS.

That is stricter than the iOS build's policy, deliberately. Android's network security config cannot express "any private LAN address", so the choice is between allowing cleartext everywhere or only for local development.

If you need a release build to reach a LAN backend over HTTP, add the address to `app/src/main/res/xml/network_security_config.xml`.

## Authentication

The published app authenticates with **Google Play Integrity**. It needs a real device or a Play-Store-enabled emulator image, not a bare AOSP image.

Leave the Settings token empty to rely on Play Integrity, or to talk to a local backend with no auth configured. A manually entered Settings token takes precedence, as an admin and development override.

See [Device Attestation](/architecture/authentication) for how the flow works, and [Device Attestation Setup](/self-hosting/attestation) for the Google Cloud and Play Console steps.

## Run the tests

```bash
cd RaceControlAndroid
./gradlew testDebugUnitTest
```

The unit tests cover the logic most likely to silently disagree with the iOS build: JSON scalar coercion, lap-time and gap formatting, grid deltas, flexible date parsing, team-colour legibility and LTTB downsampling.

## Where next

- [Android architecture](/architecture/android)
- [Android Feature Inventory](/architecture/android-feature-inventory): the screen-by-screen iOS to Android mapping
- [Android Status](/contributing/android-status): what has been verified and what is outstanding
