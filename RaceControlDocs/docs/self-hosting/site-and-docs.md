# Self-Hosting the Site & Docs

Both are stateless and hold no backend credentials. Neither needs a persistent volume or a shared secret.

## Project site

`RaceControlSite/` is the project's marketing and about site.

1. **New Resource → Application → Public/Private Repository**, and point it at the repository.
2. **Build Pack:** `Dockerfile`. **Base Directory:** `/RaceControlSite`.
3. **Port:** `3000`. **Health check path:** `/api/health`.
4. Set the environment variables below. All are optional.
5. Deploy.

| Variable | Default | Purpose |
|---|---|---|
| `NEXT_PUBLIC_BACKEND_API_URL` | `https://racecontrol.owl-media.co.uk` | Where the site's API and Swagger links point. |
| `NEXT_PUBLIC_WEB_APP_URL` | `https://web.getracecontrol.com` | Link to the deployed web app. |
| `NEXT_PUBLIC_DOCS_URL` | `https://docs.getracecontrol.com` | Link to these docs. The site's old `/docs` routes redirect here. |
| `NEXT_PUBLIC_APP_STORE_URL` | unset | App Store listing. Until set, the site shows a "Coming soon" badge. |
| `NEXT_PUBLIC_PLAY_STORE_URL` | unset | Play Store listing. Until set, the site shows a "Coming soon" badge. |

::: info Build-time values
`NEXT_PUBLIC_*` variables are baked in when the site is built. Change one and you need to redeploy, not just restart.
:::

## Docs

`RaceControlDocs/` is this documentation, a static VitePress build served by an unprivileged nginx image.

1. **New Resource → Application → Public/Private Repository**, and point it at the repository.
2. **Build Pack:** `Dockerfile`. **Base Directory:** `/RaceControlDocs`.
3. **Port:** `8080`. The unprivileged nginx image runs as a non-root user and cannot bind ports below 1024.
4. **Health check path:** `/`.
5. Deploy.

There are no environment variables.

### How it is served

The build stage runs `npm run docs:build`. The output in `docs/.vitepress/dist` is copied into `nginxinc/nginx-unprivileged`.

`nginx/default.conf` serves clean URLs, so `/api/endpoints` is served from `api/endpoints.html`, and anything else is a real 404. Hashed assets are cached for a year; pages are cached for ten minutes.
