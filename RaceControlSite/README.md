# RaceControl Site

The project's marketing and about site, at [getracecontrol.com](https://getracecontrol.com).
It covers every RaceControl piece, links out to each, and gives full attribution to the
open data sources and open-source libraries the project is built on. No backend
credentials, no secrets, nothing user-specific.

Documentation is not hosted here. It lives in [`../RaceControlDocs`](../RaceControlDocs)
and is served at [docs.getracecontrol.com](https://docs.getracecontrol.com); the site's
old `/docs` routes redirect there.

## Quick start

Requires Node 20+. No backend needs to be running.

```bash
cp .env.example .env.local   # optional — sensible defaults are baked in
npm install
npm run dev
```

Open http://localhost:3000.

## Pages

| Route | Content |
|---|---|
| `/` | Overview: what RaceControl is, headline capabilities, the pieces |
| `/features` | The full screen-by-screen catalogue, searchable and filterable by platform |
| `/platforms` | iOS, Android and web: conventions, deliberate divergences, availability matrix |
| `/open-source` | Attribution: FastF1, jolpica-f1 and every open-source library used |

## When the apps or API change

`src/lib/features.ts` and `src/lib/endpoints.ts` are hand-maintained mirrors of the docs.
Update them alongside the matching docs pages.

## Documentation

- [Run the site](https://docs.getracecontrol.com/getting-started/site-and-docs)
- [Self-hosting and environment variables](https://docs.getracecontrol.com/self-hosting/site-and-docs)

Questions? Ask in [Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions).
To contribute, see the [contributing guide](https://docs.getracecontrol.com/contributing/).
