# Troubleshooting

## Backend

### The first request for a race is very slow

That is expected. The first request for a given race downloads and caches its data. FastF1 caches to `backend/.fastf1_cache/` locally, or `/data/fastf1_cache` in Docker, so later requests are fast.

The first uncached mini-sectors request is slower still, because it loads telemetry.

### Every redeploy is slow again

The container has no persistent volume, so the FastF1 cache is lost on each deploy. Mount a volume at `/data`. See [Persistent storage](/self-hosting/backend#persistent-storage).

### The container is killed while loading telemetry

It is running out of memory. Give it at least 1 GB of RAM, with 2 GB recommended, and lower `SESSION_CACHE_MAX` first.

### The backend refuses to start

`WEB_CONCURRENCY` is set above `1`. The caches are per-process, so `main.py` refuses to start with more than one worker. Set it back to `1`.

### An analytics endpoint returns `available: false`

The session's data is partial. The derived analytics endpoints return a valid empty body rather than an error in that case. Try a different race, or check the same race on the core endpoints.

### Every request returns 401

The backend has authentication configured and the request carries no valid token.

- From `curl`, send `Authorization: Bearer <API_TOKEN>`.
- From the web app, check `RACECONTROL_API_TOKEN` equals the backend's `API_TOKEN`.
- From a mobile app, see the attestation entries below.

## iOS

### The app cannot reach my local backend

- On a physical iPhone, use your Mac's LAN address, not `localhost`, and make sure both are on the same Wi-Fi.
- The backend must be listening on all interfaces. `./run.sh` does this.
- A deployed backend must be HTTPS. Cleartext HTTP is only allowed to `localhost` and LAN addresses.

Use **Test Connection** in Settings to see whether the problem is reachability or authentication.

### App Attest fails in the Simulator

App Attest only works on a real device. In the Simulator, use the admin token in Settings or an open local backend.

### Every attestation is rejected

`APP_ATTEST_PRODUCTION` on the backend does not match the app's entitlement environment. Xcode debug builds are development; TestFlight and App Store builds are production. Also check `APPLE_TEAM_ID` and `APP_BUNDLE_ID`.

## Android

### The app cannot reach my backend on a physical device

A physical device cannot see `10.0.2.2`. Go to **⋮ → Settings → Server address** and enter your machine's LAN address, such as `http://192.168.1.20:8000`.

### A release build will not connect over HTTP

Release builds only permit cleartext to `localhost` and `10.0.2.2`. Use HTTPS, or add the address to `app/src/main/res/xml/network_security_config.xml`.

### Play Integrity fails on an emulator

Play Integrity needs a Play-Store-enabled emulator image, not a bare AOSP image. For testing before the app is live on Play, see the relaxation variables in [Device Attestation Setup](/self-hosting/attestation).

### Play Integrity fails on every device

Check that `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER` in `app/build.gradle.kts` is no longer the placeholder `0L`, and that it matches `GOOGLE_CLOUD_PROJECT_NUMBER` on the backend.

### The app shows a "showing cached data" banner

The app could not reach the backend and is rendering the schedule or standings from its offline cache. Check the server address and your connection.

### Reminders do not fire

Check the notification permission (Android 13 and later ask at runtime) and that exact alarms are allowed for the app. Reminders are rescheduled after a reboot and refreshed daily.

## Web app

### Pages fail to load data

Check `RACECONTROL_API_BASE_URL` points at a running backend, and that `RACECONTROL_API_TOKEN` is empty for an open local backend or equal to the backend's `API_TOKEN` otherwise.

### It works locally but not in Coolify

If you used the backend's internal service DNS name, confirm both services are on the same Coolify network. Otherwise fall back to the backend's public URL.

## Docs

### The docs build fails with a dead link

VitePress fails the build on broken internal links. The error names the page and the link. Use root-relative paths with no extension, such as `/api/endpoints`.

## Still stuck?

See [Getting Support](/help/support).
