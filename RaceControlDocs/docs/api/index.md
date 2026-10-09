# API Reference

The backend is a thin FastAPI layer over FastF1. It converts pandas timedeltas, timestamps and NaNs into JSON that is safe to parse, and caches every response.

- [Core Endpoints](/api/endpoints): schedule, results, standings, drivers, teams, circuits, replay, telemetry and race conditions.
- [Derived Analytics](/api/analytics): chart-ready analysis computed from data FastF1 already loads.
- [Authentication Endpoints](/api/authentication): the attestation bootstrap used by the mobile apps.

## Base URL

| Environment | Base URL |
|---|---|
| Local | `http://localhost:8000` |
| Production | `https://racecontrol.owl-media.co.uk` |

Every data endpoint is a `GET` under `/api`.

## Interactive documentation

FastAPI generates Swagger UI at `/docs` and an OpenAPI schema at `/openapi.json`. Use those for exact request and response schemas, and to try requests in the browser.

- Local: `http://localhost:8000/docs`
- Production: [racecontrol.owl-media.co.uk/docs](https://racecontrol.owl-media.co.uk/docs)

## Authentication

Any one of three mechanisms authorises an `/api` request. They are independent of each other.

| Mechanism | Used by | How |
|---|---|---|
| Apple App Attest | Published iOS app | Exchanges a device attestation for a short-lived JWT, sent as `Bearer <JWT>`. |
| Google Play Integrity | Published Android app | Exchanges an integrity verdict for a short-lived JWT, sent as `Bearer <JWT>`. |
| Shared admin token | Web app's server, `curl`, break-glass | `Authorization: Bearer <API_TOKEN>`. |

If none of the three is configured, the API is fully open. That is the default for a local `./run.sh` and is intended for development only.

`/`, `/api/health`, `/docs`, `/openapi.json` and `/redoc` are always reachable without authentication.

```bash
# Local, no auth
curl http://localhost:8000/api/schedule/2026

# With the shared token
curl -H "Authorization: Bearer $API_TOKEN" https://your-backend.example/api/schedule/2026
```

See [Device Attestation](/architecture/authentication) for how the mobile flows work.

## Conventions

**Path parameters.** `{year}` is a season from 2018 onwards. `{round}` is the round number within that season. `{session}` is a session identifier: `R` (race), `Q` (qualifying), `S` (sprint), `SQ` (sprint qualifying), `FP1`, `FP2` or `FP3`.

**Caching.** Responses are cached in memory for `CACHE_TTL_SECONDS` (six hours by default). FastF1 also caches downloaded session data to disk. The first request for a given race is slow; later ones are fast.

**Partial data.** The derived analytics endpoints return `available: false` with a valid empty body, rather than an error, when a session's data is incomplete.

**Rate limiting.** Requests are limited per IP (`RATE_LIMIT_PER_MINUTE`, 120 by default). See [the proxy trust boundary](/self-hosting/backend#reverse-proxy-trust-boundary) for what that depends on.

**CORS.** All browser origins are denied unless listed in `ALLOWED_ORIGINS`. Native clients do not send an `Origin` header, and the web app calls the backend from its own server.

**Number or string.** FastF1 emits numbers where Ergast/Jolpica emits strings for some of the same fields. Clients use a permissive scalar type (`JSONValue` on iOS, `JsonValue` on Android) so a type mismatch can never crash the UI. If you write a new client, do the same.
