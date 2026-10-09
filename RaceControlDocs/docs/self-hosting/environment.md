# Environment Variables

Every variable each service reads. The `.env.example` file in each folder is the template: copy it for local use, or set the values in your hosting platform.

Real `.env` files, service-account keys and signing material are git-ignored. Only the `.env.example` templates are tracked.

## Backend

Template: `backend/.env.example`.

### Authentication

Any one of the three mechanisms authorises an `/api` request, and they are independent. If all three are empty or disabled, the API is fully open.

**Apple App Attest**

| Variable | Default | Notes |
|---|---|---|
| `APP_ATTEST_ENABLED` | `false` | Gate the API with App Attest. Enable for an App Store build. |
| `APPLE_TEAM_ID` | empty | 10-character Team ID from developer.apple.com. |
| `APP_BUNDLE_ID` | `com.owlmedia.racecontrol` | Must match the app's bundle id. |
| `APP_ATTEST_PRODUCTION` | `false` | `false` is Apple's development environment (Xcode debug builds). Must match the app's entitlement. |
| `ATTEST_DB` | under `/data` | Where attested keys are persisted. |

**Google Play Integrity**

| Variable | Default | Notes |
|---|---|---|
| `PLAY_INTEGRITY_ENABLED` | `false` | Gate the API with Play Integrity. Enable for a Play Store build. |
| `ANDROID_PACKAGE_NAME` | `com.owlmedia.racecontrol` | The app's package name. |
| `GOOGLE_CLOUD_PROJECT_NUMBER` | empty | Numeric project number, linked in Play Console. |
| `GOOGLE_APPLICATION_CREDENTIALS_JSON` | empty | Service-account JSON key, inline on one line. |
| `GOOGLE_APPLICATION_CREDENTIALS` | unset | Path to the key file, as an alternative to the inline form. |
| `ANDROID_SIGNING_CERT_SHA256` | empty | Comma-separated SHA-256 digests of the signing certificates. Optional but recommended. |
| `PLAY_INTEGRITY_MIN_DEVICE_VERDICT` | `MEETS_DEVICE_INTEGRITY` | Weakest device verdict accepted. |
| `PLAY_INTEGRITY_ALLOW_UNEVALUATED` | `false` | Accept an unevaluated app or device verdict. Leave `false` for production. |

**Tokens**

| Variable | Default | Notes |
|---|---|---|
| `JWT_SECRET` | empty | Signs the app tokens. Generate with `openssl rand -hex 32`. |
| `JWT_TTL_SECONDS` | `86400` | App token lifetime (24 hours). The apps refresh automatically. |
| `API_TOKEN` | empty | Optional shared admin and break-glass secret. Also handy for `curl`, and required by the web app. |

### Runtime

| Variable | Default | Notes |
|---|---|---|
| `PORT` | `8000` | Port the API listens on. Coolify sets this automatically. |
| `WEB_CONCURRENCY` | `1` | Uvicorn workers. **Must stay `1`**: `main.py` refuses to start above it. |
| `RATE_LIMIT_PER_MINUTE` | `120` | Per-IP limit in requests per minute. `0` disables it. |
| `ALLOWED_ORIGINS` | empty | Comma-separated browser origins allowed through CORS. Empty denies all. |

### Caching

| Variable | Default | Notes |
|---|---|---|
| `FASTF1_CACHE` | `/data/fastf1_cache` in Docker | Where FastF1 caches downloaded timing and telemetry data. Mount a persistent volume there. |
| `SESSION_CACHE_MAX` | `48` | How many FastF1 sessions to hold in memory. Each loaded race session with telemetry can be tens of MB. Lower it on a small container. |
| `CACHE_TTL_SECONDS` | `21600` | Response cache lifetime in seconds. Historical data barely changes. |
| `FINGERPRINT_TYRE_ROUNDS` | `6` | Cap on the season sample used for the driver fingerprint's tyre axis. |

## Web app

Template: `RaceControlWeb/.env.example`. Copy to `.env.local` for local development.

| Variable | Default | Notes |
|---|---|---|
| `RACECONTROL_API_BASE_URL` | `http://localhost:8000` | Base URL of the backend. |
| `RACECONTROL_API_TOKEN` | empty | Bearer token sent to the backend. Set to the backend's `API_TOKEN` in production; leave empty for a local backend with no auth. |
| `PORT` | `3000` | Port the web app listens on. Coolify sets this automatically. |

## Project site

Template: `RaceControlSite/.env.example`. All optional. The site builds and links correctly with the defaults in `src/lib/config.ts`.

| Variable | Default | Notes |
|---|---|---|
| `NEXT_PUBLIC_BACKEND_API_URL` | `https://racecontrol.owl-media.co.uk` | Public production backend. |
| `NEXT_PUBLIC_WEB_APP_URL` | `https://web.getracecontrol.com` | The deployed web app. |
| `NEXT_PUBLIC_DOCS_URL` | `https://docs.getracecontrol.com` | This documentation site. |
| `NEXT_PUBLIC_APP_STORE_URL` | unset | Set once the iOS app is published. |
| `NEXT_PUBLIC_PLAY_STORE_URL` | unset | Set once the Android app is published. |

## Docs

None.
