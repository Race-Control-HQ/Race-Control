# RaceControl Site

The project's marketing/about site: covers all four RaceControl pieces (iOS, Android, Web,
Backend), links out to each, and gives full attribution to the open data sources and
open-source libraries the project is built on. Pure static/server-rendered content — no
backend credentials, no secrets, nothing user-specific.

Same tooling and design system as [`../RaceControlWeb`](../RaceControlWeb): Next.js App
Router, Tailwind v4, and the identical dark OLED colour tokens in `src/app/globals.css`
(ported from the mobile design system, see `RaceControlAndroid/docs/FEATURES.md` §5), so it
looks like part of the same product rather than a bolted-on brochure page.

## Pages

| Route | Content |
|---|---|
| `/` | Overview — what RaceControl is, headline capabilities, the four pieces |
| `/features` | The full screen-by-screen catalogue, searchable and filterable by platform |
| `/platforms` | iOS / Android / Web: conventions, deliberate divergences, availability matrix, store & source links |
| `/docs` | Docs landing, with links to the long-form markdown docs in the repo |
| `/docs/getting-started` | Run the backend and each client locally |
| `/docs/api` | Searchable reference for every backend endpoint |
| `/docs/architecture` | How one service feeds three clients, and why the web app needs a BFF |
| `/docs/self-hosting` | Coolify deployment, env vars, volumes, proxy trust boundary |
| `/open-source` | Attribution: FastF1, jolpica-f1, and every open-source library across the four pieces |

`/backend` permanently redirects to `/docs/api` (it was an earlier page).

## Content lives in data modules, not in JSX

Two modules are the source of truth for most of the site, so the pages can't drift
apart from each other:

- `src/lib/features.ts` — the screen-by-screen catalogue, transcribed from
  [`RaceControlAndroid/docs/FEATURES.md`](../RaceControlAndroid/docs/FEATURES.md) §3 and
  the root README §3. Drives `/features`, the availability matrix on `/platforms`, and
  the counts on `/`.
- `src/lib/endpoints.ts` — the full API surface, transcribed from the root README's
  endpoint tables. Drives `/docs/api` and the counts elsewhere.

**When the app or API changes, update these two files.** They are a hand-maintained
mirror of the real docs, not generated from them.

## Screenshots

There are no real screenshots in the repo yet, so `<Screenshot>`
(`src/components/Screenshot.tsx`) renders hand-built UI illustrations from
`src/components/mockups/` and visibly labels each one "UI illustration" — a drawn
mockup must never quietly read as a real capture.

To swap in a real screenshot: capture it, save to `public/screenshots/`, and set `src`
on that entry in `src/lib/screenshots.ts`. The illustration and its label disappear
automatically; no page or component changes. Use one fixed season and round across all
platforms, per the discipline in
[`docs/VISUAL_PARITY.md`](../docs/VISUAL_PARITY.md).

## Local development

```bash
cp .env.example .env.local   # optional — sensible defaults are baked in, see src/lib/config.ts
npm install
npm run dev
```

Open **http://localhost:3000**.

## Configuration

Every external link (backend URL, web app URL, App Store / Play Store links) is env-driven
via `src/lib/config.ts`, with a working fallback so the site builds and looks correct before
any of it is configured. See `.env.example` for the full list.

## Deploying on Coolify

This repo is a monorepo: `backend/`, `RaceControlWeb/` and `RaceControlSite/` each have their
own `Dockerfile` and deploy as independent Coolify services from the same repo, using
Coolify's **Base Directory** setting to point each service at its own folder.

1. **New Resource → Application → Public/Private Repository**, point it at this repo.
2. **Build Pack:** `Dockerfile`. **Base Directory:** `/RaceControlSite`.
3. **Port:** `3000`. **Health check path:** `/api/health`.
4. Set the environment variables from `.env.example` (all optional).
5. Deploy.

No persistent volume, no shared secrets with the backend — this service is entirely
stateless.
