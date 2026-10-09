# Web App (BFF)

`RaceControlWeb/` is a Next.js App Router client for the same backend. It replicates the mobile apps' end-user screens: schedules, results, standings, driver and team profiles, circuits and track maps, lap times, strategy, weather, telemetry and race replays.

## Why it needs a BFF

The backend has no browser-friendly auth mechanism. It is gated by Apple App Attest, Google Play Integrity or a shared admin token, and a browser can do none of those without exposing a secret to every visitor.

So the web app is a **backend-for-frontend**. It holds the backend's `API_TOKEN` server-side and never ships it to the client.

```
Browser ──► Next.js server ──► Backend
            holds API_TOKEN    Authorization: Bearer <API_TOKEN>
```

## Two paths to the backend

**Server Components** fetch the backend directly through `src/lib/server/backend.ts`, a server-only helper. This is used for initial page loads and costs no extra HTTP hop.

**Client components** call `src/app/api/proxy/[...path]/route.ts`, a same-origin, GET-only proxy. This is used for interactive refetches such as season switching, telemetry scrubbing and replay playback.

Only the `/api/*` data surface is reachable through the proxy. The mobile [attestation bootstrap endpoints](/api/authentication) are not exposed.

## Configuration

| Variable | Purpose |
|---|---|
| `RACECONTROL_API_BASE_URL` | Base URL of the backend. |
| `RACECONTROL_API_TOKEN` | Sent to the backend as a bearer token. Must equal the backend's `API_TOKEN`. Empty for a local backend with no auth. |
| `PORT` | Port the web app listens on (3000 by default). |

## Deliberately out of scope

**Session reminder notifications.** The mobile apps schedule local notifications for upcoming sessions. There is no clean web equivalent without a Service Worker and push infrastructure the backend does not have today.

**Synced favourites.** Favourites are client-only (`localStorage`), matching the mobile apps. They do not sync anywhere, since the backend has no user accounts.

## Design

The web app uses Tailwind v4 and the same dark OLED colour tokens as the mobile apps, defined in `src/app/globals.css`. See the [Design System](/architecture/design-system).

## Deployment

See [Self-hosting the web app](/self-hosting/web).
