# RaceControl Web

A web client for the RaceControl backend (`../backend`), replicating the iOS
(`RaceControlApp`) and Android (`RaceControlAndroid`) apps' end-user screens:
schedules, results, standings, driver/team profiles, circuits/track maps, lap
times, strategy, weather, telemetry, and race replays.

## Architecture

The backend has no browser-friendly auth mechanism (it's gated by Apple App
Attest / Google Play Integrity / a shared admin token — none of which a
browser can do without exposing a secret to every visitor). This app is a
**BFF (backend-for-frontend)**: it holds the backend's `API_TOKEN`
server-side and never ships it to the client.

- `src/lib/server/backend.ts` — server-only fetch helper used by Server
  Components for initial page loads (no extra HTTP hop).
- `src/app/api/proxy/[...path]/route.ts` — a GET-only proxy client components
  call for interactive refetches (season switching, telemetry scrubbing,
  etc). Only the `/api/*` data surface is reachable through it; the mobile
  App Attest / Play Integrity bootstrap endpoints are not exposed.

No changes are made to `backend/` or the mobile apps.

## Local development

```bash
cp .env.example .env.local   # point RACECONTROL_API_BASE_URL at your local backend
npm install
npm run dev
```

This expects the backend running locally (`cd ../backend && ./run.sh`). With
no auth configured on the backend, leave `RACECONTROL_API_TOKEN` empty.

## Deploying on Coolify

This repo is a monorepo: `backend/` and `RaceControlWeb/` each have their own
Dockerfile and deploy as separate Coolify services from the same repo, using
Coolify's **Base Directory** setting to point each service at its own folder.

1. Create the backend service first (see `../backend`), with `API_TOKEN` set
   to a generated secret (`openssl rand -hex 32`).
2. Create a second Coolify service from this same repo with Base Directory
   `RaceControlWeb`.
3. Set its environment variables:
   - `RACECONTROL_API_TOKEN` — the **same** value as the backend's `API_TOKEN`.
   - `RACECONTROL_API_BASE_URL` — prefer the backend's **internal Coolify
     service DNS name** (e.g. `http://backend:8000`) so traffic stays inside
     Coolify's network rather than round-tripping through the public
     hostname. Falls back to the backend's public URL if internal networking
     isn't set up.
4. Deploy. The container exposes port 3000 (`PORT` is set automatically by
   Coolify) and reports health at `/api/health`.

## Notably out of scope

- **Session reminder notifications** — the mobile apps schedule local
  notifications for upcoming sessions. There's no clean web equivalent
  without a Service Worker + push infrastructure the backend doesn't have
  today, so this was deliberately left out of the web MVP.
- **Favorites** are client-only (`localStorage`), matching the mobile apps —
  they don't sync anywhere, since the backend has no user accounts.
