# RaceControl Docs

The [VitePress](https://vitepress.dev) site behind
[docs.getracecontrol.com](https://docs.getracecontrol.com). This is the home for all
RaceControl documentation: getting started, features, the API reference, architecture,
self-hosting and everything for contributors. The README files elsewhere in the repo are
quick starts that point here.

## Quick start

Requires Node 20+.

```sh
npm ci
npm run docs:dev      # live-reloading dev server
npm run docs:build    # production build into docs/.vitepress/dist
npm run docs:preview  # serve the production build
```

The build fails on dead internal links, and CI runs it on every change to this folder.

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

To add a page: create the Markdown file, add it to the sidebar in
`docs/.vitepress/config.mts`, and link it from the section's `index.md`.

## Deploying with Coolify

- Base directory: `/RaceControlDocs`
- Build pack: `Dockerfile`
- Port: `8080` (the unprivileged nginx image listens there)

## More

How the docs are organised, the style to write in and which pages go stale first are all
covered in [Writing Documentation](https://docs.getracecontrol.com/contributing/documentation).

Questions? Ask in [Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions).
