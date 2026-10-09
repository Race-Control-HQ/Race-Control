# Run the Web App

The web app is a Next.js client for the same backend, covering schedules, results, standings, driver and team profiles, circuits, lap times, strategy, weather, telemetry and race replays.

Requirements: **Node 20+**.

## Start it

[Start the backend](/getting-started/backend) first, then:

```bash
cd RaceControlWeb
cp .env.example .env.local   # RACECONTROL_API_BASE_URL=http://localhost:8000
npm install
npm run dev
```

Open **http://localhost:3000**.

## Configuration

| Variable | Local value | Notes |
|---|---|---|
| `RACECONTROL_API_BASE_URL` | `http://localhost:8000` | Where the backend is running. |
| `RACECONTROL_API_TOKEN` | empty | Leave empty for a local backend with no auth configured. |

The token is held server-side and never reaches the browser. See [Web App (BFF)](/architecture/web) for why.

## Check your change

```bash
cd RaceControlWeb
npm ci
npm run lint
npm run build
```

## Where next

- [Web App (BFF) architecture](/architecture/web)
- [Self-hosting the web app](/self-hosting/web)
