# Self-Hosting the Web App

`RaceControlWeb/` ships with a `Dockerfile` and deploys as a second, independent Coolify service from the same repository.

## Before you start

[Deploy the backend](/self-hosting/backend) first, with `API_TOKEN` set to a generated secret:

```bash
openssl rand -hex 32
```

## Create the app in Coolify

1. **New Resource → Application → Public/Private Repository**, and point it at the repository.
2. **Build Pack:** `Dockerfile`. **Base Directory:** `/RaceControlWeb`.
3. **Port:** `3000`. `PORT` is set automatically by Coolify.
4. **Health check path:** `/api/health`.

## Environment variables

```bash
RACECONTROL_API_BASE_URL=http://backend:8000
RACECONTROL_API_TOKEN=<same value as the backend's API_TOKEN>
```

| Variable | Notes |
|---|---|
| `RACECONTROL_API_TOKEN` | The **same** value as the backend's `API_TOKEN`. |
| `RACECONTROL_API_BASE_URL` | Prefer the backend's internal Coolify service DNS name, for example `http://backend:8000`. |

Using the internal DNS name keeps traffic inside Coolify's network rather than round-tripping through the public hostname. Fall back to the backend's public URL if internal networking is not set up.

## What is and is not exposed

The token stays on the web app's server. The browser only ever talks to the web app's own origin.

Only the backend's `/api/*` data surface is reachable through the web app's proxy. See [Web App (BFF)](/architecture/web).

## State

None. No persistent volume is needed.
