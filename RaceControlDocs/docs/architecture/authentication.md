# Device Attestation

The published apps authenticate with no user-visible key and no login.

## The problem

A shared `API_TOKEN` cannot ship in a public app. Anything bundled in an IPA or APK can be extracted. For Android that is a trivial decompile. It would not be a secret.

## The approach

Both published apps use their platform's own device-attestation service: **Apple App Attest** on iOS and **Google Play Integrity** on Android. Each install proves, through a hardware-backed or platform-backed check, that requests come from a genuine, unmodified copy of that app on a real device.

There is nothing embedded in the app to extract, and nothing to distribute.

The two mechanisms are fully independent. Enabling one never requires or affects the other. They mint interchangeable JWTs, so the backend authorises a request from either without needing to know which one issued it.

A third mechanism, the shared admin token, remains for the web app's server, for `curl`, and as a break-glass credential.

## Side by side

| | Apple App Attest (iOS) | Google Play Integrity (Android) |
|---|---|---|
| **1. Request a challenge** | `GET /attest/challenge` returns a one-time nonce | `GET /playintegrity/challenge` returns a one-time nonce |
| **2. Prove device and app identity** | `DCAppAttestService.attestKey`, tied to the nonce | Play Integrity API vouches for app and device, tied to the nonce |
| **3. Verify and issue a token** | `POST /attest/verify` checks the certificate chain to Apple's root, returns a JWT | `POST /playintegrity/verify` decodes the token through Google's REST API and checks its verdicts, returns a JWT |
| **4. Authenticated calls** | `GET /api/...` with `Bearer <JWT>` | `GET /api/...` with `Bearer <JWT>` |
| **5. Refresh before expiry** | `generateAssertion` and `POST /attest/token` (verifies signature and replay counter) | Request a fresh integrity token and verify again |
| **Client-side cache** | Keychain | Encrypted SharedPreferences (`PlayIntegrityTokenStore`) |
| **Device requirement** | Real device only. Does not work in the Simulator. | Real device or a Play-Store-enabled emulator image, not a bare AOSP image. |
| **Backend enable flag** | `APP_ATTEST_ENABLED=true` | `PLAY_INTEGRITY_ENABLED=true` |
| **Backend persistence** | Attested keys persisted to `ATTEST_DB` | Stateless JWTs, no persistent store needed |
| **Backend module** | `attest.py` | `playintegrity.py` |
| **Automated test coverage** | `test_attest.py`, `test_attest_endpoints.py`, using Apple's documented steps through `pyattest` | None yet |

Play Integrity has no persistent per-device key to renew, so its refresh is simply a fresh integrity token each time the cached JWT expires. Caching the JWT also keeps the app well under Play Integrity's per-app quota.

## Token precedence in the apps

Both apps prefer a manually entered Settings token first and fall back to attestation otherwise. The Settings token is an admin and development override, and is how an app talks to a local backend that has no auth at all.

On a 401, both apps invalidate the cached token and ask for one fresh token before giving up, which re-verifies against the attestation service.

## What this protects

- **It strongly binds API access to genuine installs** of the app on a real device, with per-IP rate limiting as defence in depth.
- **It is not a login.** There are no user accounts, because the data is public F1 history. Attestation stops abuse and scraping. It does not identify users.

::: warning Not yet verified end to end on real hardware
Neither mechanism's server-side verification has been tested end to end against real hardware. App Attest needs a real iPhone and an Apple developer account. Play Integrity needs a real Android device, or a Play-enabled emulator, and a linked Play Console project. Do a real-device smoke test of both before shipping.
:::

Adding a `test_playintegrity.py` alongside the App Attest tests is a welcome contribution.

## Setting it up

See [Device Attestation Setup](/self-hosting/attestation) for the configuration on each side, and [Authentication Endpoints](/api/authentication) for the routes.
