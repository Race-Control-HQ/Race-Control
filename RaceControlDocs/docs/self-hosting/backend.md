# Self-Hosting the Backend

The backend ships with a `Dockerfile`, so Coolify can build and run it directly. Local development is unaffected: `./run.sh` still runs it natively with no auth.

## Create the app in Coolify

1. **New Resource → Application → Public/Private Repository**, and point it at the repository.
2. **Build Pack:** `Dockerfile`. **Base Directory:** `/backend`.
3. **Port:** `8000`. The container also honours `$PORT` if Coolify overrides it.
4. **Health check path:** `/api/health`, deliberately left unauthenticated so the platform can probe it.

## Environment variables

The ones that matter for a first deployment:

| Variable | Value | Notes |
|---|---|---|
| `API_TOKEN` | `openssl rand -hex 32` | Shared admin secret. The web app uses it. |
| `JWT_SECRET` | `openssl rand -hex 32` | Signs the short-lived app tokens. Needed for attestation. |
| `RATE_LIMIT_PER_MINUTE` | `120` | Per-IP limit. `0` disables it. |
| `FASTF1_CACHE` | `/data/fastf1_cache` | Already the image default. |
| `WEB_CONCURRENCY` | `1` | **Must stay `1`.** |
| `SESSION_CACHE_MAX` | `24` | Lower than the default 48 on a small container. |
| `CACHE_TTL_SECONDS` | `21600` | Response cache lifetime (six hours). |
| `ALLOWED_ORIGINS` | unset | Normally stays empty. |

The full list, including the attestation variables, is in [Environment Variables](/self-hosting/environment).

::: danger Open by default
If you set none of `APP_ATTEST_ENABLED`, `PLAY_INTEGRITY_ENABLED` or `API_TOKEN`, the API is fully open. That is fine for local development and wrong for a public deployment.
:::

## Persistent storage

Add a **persistent volume mounted at `/data`**.

FastF1 caches every session it downloads there. Without it each redeploy re-downloads everything, which is slow and burns through the upstream F1 and Jolpica rate limits. Budget a few GB if you plan to browse a lot of telemetry.

The App Attest key store (`ATTEST_DB`) also defaults to a path under `/data`. Keep the volume, or every redeploy forces all iOS installs to re-attest. That is harmless, just an extra round trip.

## Resources

Loading a race session with telemetry is pandas-heavy. Give the container **at least 1 GB of RAM, with 2 GB recommended**.

If it gets OOM-killed while loading telemetry, lower `SESSION_CACHE_MAX` first.

## Single worker only

The response cache, the session cache and the points-progression cache are plain in-process dictionaries with no sharing between workers. `main.py` refuses to start if `WEB_CONCURRENCY` is above `1`.

Do not raise it without first externalising those three caches to a shared store.

## Reverse proxy trust boundary

The per-IP rate limiter (`_client_key` in `main.py`) trusts the leftmost `X-Forwarded-For` entry as the real client address.

That is only safe because Coolify's Traefik is the sole ingress path and overwrites that header before it reaches the container. See the `--proxy-headers` and `--forwarded-allow-ips` flags in the `CMD` of `backend/Dockerfile`. The test `test_dockerfile_declares_the_proxy_trust_boundary` in `backend/test_main_infra.py` fails if those flags are ever removed.

::: danger Never expose port 8000 directly to the internet
If a caller can reach the app without going through the proxy, it can forge `X-Forwarded-For` and bypass the rate limiter entirely.
:::

## CORS

`ALLOWED_ORIGINS` is a comma-separated list of browser origins. Empty or unset denies all browser origins.

It should normally stay empty. Native iOS and Android clients are unaffected, because they do not send an `Origin` header, and the web app proxies server-side rather than calling the API from the browser.

## HTTPS

The mobile apps only allow cleartext HTTP to local addresses, so a deployed backend must be HTTPS. Coolify handles the certificate for you.

## Pointing the apps at your backend

**iOS.** Change `AppConfig.apiBaseURL` in `RaceControlApp/RaceControl/Networking/APIClient.swift` to your HTTPS URL, then rebuild. Tap **Test Connection** in Settings to check reachability and authentication.

**Android.** In the app, go to **⋮ overflow menu → Settings → Server address**, enter your HTTPS URL, and tap **Test Connection**.

**Web.** Set `RACECONTROL_API_BASE_URL`. See [Self-hosting the web app](/self-hosting/web).
