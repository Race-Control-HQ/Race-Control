# Run the Site & Docs

Neither the project site nor this documentation needs the backend to be running.

Requirements: **Node 20+**.

## Project site

`RaceControlSite/` is the marketing and about site. It covers all the pieces, links out to each, and gives full attribution to the open data sources and open-source libraries the project depends on.

```bash
cd RaceControlSite
cp .env.example .env.local   # optional, sensible defaults are baked in
npm install
npm run dev
```

Open **http://localhost:3000**.

Every external link (backend URL, web app URL, docs URL, App Store and Play Store links) is env-driven through `src/lib/config.ts`, with a working fallback. The site builds and looks correct before any of it is configured.

### Where the site's content lives

Two data modules drive most of the site, so its pages cannot drift apart from each other:

- `src/lib/features.ts` is the screen-by-screen catalogue. It drives `/features`, the availability matrix on `/platforms`, and the counts on the home page.
- `src/lib/endpoints.ts` is the API surface, used for the endpoint counts.

**When the apps or the API change, update these two files** along with the matching pages here. They are hand-maintained, not generated.

### Screenshots

`<Screenshot>` (`src/components/Screenshot.tsx`) renders a real capture when one exists, and otherwise a hand-built UI illustration from `src/components/mockups/`, visibly labelled "UI illustration". A drawn mockup must never quietly read as a real capture.

To swap in a real screenshot, save it to `public/screenshots/` and set `src` on that entry in `src/lib/screenshots.ts`. Use one fixed season and round across all platforms, as described in the [Visual Parity Checklist](/contributing/visual-parity).

## Documentation

`RaceControlDocs/` is this site, built with [VitePress](https://vitepress.dev).

```bash
cd RaceControlDocs
npm ci
npm run docs:dev      # live-reloading dev server
npm run docs:build    # production build into docs/.vitepress/dist
npm run docs:preview  # serve the production build
```

See [Writing Documentation](/contributing/documentation) for how the pages are organised and how to add one.
