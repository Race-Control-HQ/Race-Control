# Self-Hosting

RaceControl deploys as independent services from one repository. Each has its own `Dockerfile`, and each is pointed at its own folder with the host's base-directory setting.

These pages describe [Coolify](https://coolify.io), which is what the project uses. Any platform that can build a Dockerfile from a subfolder will work the same way.

## The services

| Service | Base directory | Port | Health check | State |
|---|---|---|---|---|
| [Backend](/self-hosting/backend) | `/backend` | `8000` | `/api/health` | Persistent volume at `/data` |
| [Web app](/self-hosting/web) | `/RaceControlWeb` | `3000` | `/api/health` | Stateless |
| [Project site](/self-hosting/site-and-docs) | `/RaceControlSite` | `3000` | `/api/health` | Stateless |
| [Docs](/self-hosting/site-and-docs#docs) | `/RaceControlDocs` | `8080` | `/` | Stateless |

Only the backend is required. The others are optional and independent of each other.

## The order to deploy in

1. **Backend**, with a generated `API_TOKEN` and a persistent volume.
2. **Web app**, given the same token and the backend's address.
3. **Project site** and **docs**, in any order. Neither holds credentials.
4. **Device attestation**, if you are shipping your own builds of the mobile apps. See [Device Attestation Setup](/self-hosting/attestation).

## Things to get right

- **Mount a volume at `/data` on the backend.** Without it every redeploy re-downloads everything.
- **Keep `WEB_CONCURRENCY=1`.** The backend refuses to start above one worker.
- **Never expose the backend's port directly.** The rate limiter depends on the reverse proxy being the only way in.
- **Use HTTPS.** The mobile apps only allow cleartext HTTP to local addresses.

Each of these is explained on the [backend page](/self-hosting/backend).

## Continuous deployment

The repository's GitHub Actions workflows are CI only. Nothing in them deploys anywhere, and there is no Play Store or TestFlight upload wired up. Deploys are triggered from your hosting platform.
