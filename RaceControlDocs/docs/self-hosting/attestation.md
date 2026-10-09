# Device Attestation Setup

This page covers switching on device attestation for your own builds of the mobile apps. For what it is and why, see [Device Attestation](/architecture/authentication).

You only need this if you are distributing the apps. For local development, leave both mechanisms off and the backend stays open.

The two mechanisms are independent. Set up either, or both.

## Apple App Attest (iOS)

### Backend

| Variable | Value |
|---|---|
| `APP_ATTEST_ENABLED` | `true` |
| `APPLE_TEAM_ID` | Your 10-character Team ID from developer.apple.com |
| `APP_BUNDLE_ID` | Must match the app's bundle id (`com.owlmedia.racecontrol` by default) |
| `APP_ATTEST_PRODUCTION` | `false` for development, `true` for release |
| `JWT_SECRET` | `openssl rand -hex 32` |

::: warning The environment must match
`APP_ATTEST_PRODUCTION` must match the app's entitlement environment. Xcode debug builds are development; TestFlight and App Store builds are production. A mismatch rejects every attestation.
:::

Attested keys are persisted to `ATTEST_DB`, which defaults to a path under `/data`. Keep the volume, or every redeploy forces all installs to re-attest.

### App

App Attest is already wired through `RaceControl.entitlements` (`com.apple.developer.devicecheck.appattest-environment`, referenced by `CODE_SIGN_ENTITLEMENTS`).

1. Confirm "App Attest" is listed under **Signing & Capabilities**.
2. Match the entitlement's `development` or `production` value to the backend you are shipping against.

App Attest needs a real device. It does not work in the Simulator.

## Google Play Integrity (Android)

### One-time setup in Google Cloud and Play Console

None of this can be done from code.

1. Enable the **Play Integrity API** on a Google Cloud project.
2. Link that project to the app in Play Console (**Setup → API access**).
3. Create a service account in that project, grant it access to the linked app through Play Console, and download its JSON key.
4. Note the app's package name and, recommended, the SHA-256 digest of its release signing certificate (**Play Console → Setup → App signing**).

### Backend

| Variable | Value |
|---|---|
| `PLAY_INTEGRITY_ENABLED` | `true` |
| `ANDROID_PACKAGE_NAME` | The app's package name (`com.owlmedia.racecontrol` by default) |
| `GOOGLE_CLOUD_PROJECT_NUMBER` | The numeric project number linked in Play Console |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | The service-account JSON key, inline on one line |
| `GOOGLE_APPLICATION_CREDENTIALS` | Alternatively, a path to the key file |
| `ANDROID_SIGNING_CERT_SHA256` | Optional but recommended. Comma-separated SHA-256 digests of the signing certificates. |
| `JWT_SECRET` | `openssl rand -hex 32` |

Pinning the signing certificate guards against a re-signed or repackaged APK even if it somehow passed the other checks.

Two variables relax the checks while you bring up a release. Leave both at their defaults in production.

| Variable | Default | When to change it |
|---|---|---|
| `PLAY_INTEGRITY_MIN_DEVICE_VERDICT` | `MEETS_DEVICE_INTEGRITY` | Relax to `MEETS_BASIC_INTEGRITY` only temporarily, for testing on an emulator or rooted device before the app is live on Play. |
| `PLAY_INTEGRITY_ALLOW_UNEVALUATED` | `false` | Set to `true` only while bringing up a new release. Internal testing track builds, and apps without enough Play install history, report an unevaluated verdict. |

Play Integrity tokens are stateless JWTs, so there is no persistent store to keep.

::: danger Keep the service-account key out of the repository
The key file patterns are git-ignored, but check before you commit. Set the key as an environment variable or mount it as a file.
:::

### App

Play Integrity is already wired through `PlayIntegrityTokenProvider`, with the Settings-token fallback in `CompositeTokenProvider`.

The one remaining step is to set the real Google Cloud project number in `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` in `app/build.gradle.kts`. It is currently the placeholder `0L`.

Play Integrity needs a real device or a Play-Store-enabled emulator image.

## Check your setup

Both mechanisms expose a non-sensitive diagnostics endpoint:

```bash
curl https://your-backend.example/attest/status
curl https://your-backend.example/playintegrity/status
```

Then do a real-device smoke test of each before shipping. Neither mechanism's server-side verification has been tested end to end against real hardware by this project.
