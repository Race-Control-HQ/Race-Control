# RaceControl Web

The Next.js web client for the RaceControl backend. It is a BFF (backend-for-frontend):
it holds the backend's token server-side and never ships it to the browser.

## Quick start

Requires Node 20+, and the backend running locally (`cd ../backend && ./run.sh`).

```bash
cp .env.example .env.local   # RACECONTROL_API_BASE_URL=http://localhost:8000
npm install
npm run dev
```

Open http://localhost:3000. With no auth configured on the backend, leave
`RACECONTROL_API_TOKEN` empty.

## Checks

```bash
npm run lint
npm run build
```

## Documentation

- [Run the web app](https://docs.getracecontrol.com/getting-started/web)
- [Architecture, and why it needs a BFF](https://docs.getracecontrol.com/architecture/web)
- [Self-hosting](https://docs.getracecontrol.com/self-hosting/web)

Questions? Ask in [Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions).
To contribute, see the [contributing guide](https://docs.getracecontrol.com/contributing/).
