# Writing Documentation

This site is the home for all RaceControl documentation. The README files in the repository are quick starts that point here.

## Where things go

| Content | Location |
|---|---|
| Anything in depth: setup, API, architecture, deployment, contributing | A page in `RaceControlDocs/docs/` |
| A quick start for the project or one piece | The `README.md` in that folder, kept short, linking here |
| Why a specific line of code is the way it is | A comment beside that code |

If you find yourself adding a long section to a README, it belongs here instead.

## Run it locally

```bash
cd RaceControlDocs
npm ci
npm run docs:dev      # live-reloading dev server
npm run docs:build    # production build into docs/.vitepress/dist
npm run docs:preview  # serve the production build
```

## Layout

```
docs/
  getting-started/   Run each piece locally
  features/          What the apps do, and where the platforms differ
  api/               Endpoint reference
  architecture/      How each piece is built
  self-hosting/      Deployment and configuration
  contributing/      Everything for contributors
  help/              Troubleshooting, common questions, support
  public/images/     Screenshots, grouped by platform
  .vitepress/        Site config and theme
```

## Add a page

1. Create the Markdown file in the matching folder.
2. Add it to the sidebar in `docs/.vitepress/config.mts`.
3. Link to it from the section's `index.md`.
4. Run `npm run docs:build`. The build fails on dead internal links.

Link to other pages with root-relative paths and no extension, such as `/api/endpoints`.

## Keeping the docs honest

These pages describe what the code does. When the code changes, update the matching page in the same pull request. The ones that go stale first:

| When you change | Update |
|---|---|
| A backend route | [Core Endpoints](/api/endpoints) or [Derived Analytics](/api/analytics), and `RaceControlSite/src/lib/endpoints.ts` |
| An environment variable | [Environment Variables](/self-hosting/environment) and the matching `.env.example` |
| A screen or feature | [Features](/features/index), and `RaceControlSite/src/lib/features.ts` |
| A deliberate Android divergence | [Android Feature Inventory](/architecture/android-feature-inventory) and [Platform Differences](/features/platforms) |
| A visualization | The table in the [Visual Parity Checklist](/contributing/visual-parity) |
| A CI workflow | [Continuous Integration](/contributing/ci) |
| A function's home in the backend | The module boundaries in [Backend architecture](/architecture/backend) |

## Files that stay in the repository

GitHub looks for a few files in the repository itself, so they stay there and are mirrored here:

| File | Mirrored at |
|---|---|
| `CODE_OF_CONDUCT.md` | [Code of Conduct](/contributing/code-of-conduct) |
| `SECURITY.md` | [Security Policy](/contributing/security) |
| `CONTRIBUTING.md` | A short pointer to [Contributing](/contributing/index) |

If you change the code of conduct or the security policy, change both copies.

## Style

- Write in British English, in plain sentences.
- Lead with what the reader needs to do, then explain why.
- Put commands in fenced `bash` blocks, one task per block.
- Use `code formatting` for file paths, commands, environment variables and endpoint paths.
- Prefer a table for anything with more than two attributes per item.
- Use `::: tip`, `::: warning` and `::: danger` blocks sparingly, for things that will bite.

## Screenshots

Images live in `docs/public/images/<platform>/` and are referenced as `/images/<platform>/<name>.png`.

Use one fixed season and round across all platforms, as described in the [Visual Parity Checklist](/contributing/visual-parity#shared-fixture). Resize captures before committing: around 1300px on the long edge for phone screenshots and 1600px wide for browser screenshots.

Clicking any screenshot on the site opens it full size.
