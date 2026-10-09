# Security Policy

## Supported versions

Only the latest commit on `main` and the currently deployed backend, web app and store builds are supported. Fixes are not backported.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub: [Report a vulnerability](https://github.com/Race-Control-HQ/Race-Control/security/advisories/new).

Include as much of the following as you can:

- which piece is affected (backend, iOS, Android, web app, site or docs)
- steps to reproduce, or a proof of concept
- the impact you believe it has

You can expect an acknowledgement within 7 days. Once the report is confirmed, a fix will be prepared privately and you will be credited in the advisory unless you ask not to be.

## Scope

Of particular interest:

- bypasses of [device attestation](/architecture/authentication) (Apple App Attest, Google Play Integrity) or of the backend's token checks
- ways to reach the backend through the [web app's BFF layer](/architecture/web) that it is meant to block
- secrets or keys exposed in the repository, build artefacts or API responses

Out of scope:

- issues in third-party services (FastF1, the F1 live-timing API, Ergast/Jolpica)
- denial of service by request volume alone

## For contributors

A few properties of the system are security-relevant and easy to break by accident:

- **The reverse-proxy trust boundary.** The rate limiter trusts `X-Forwarded-For`. See [Self-hosting the backend](/self-hosting/backend#reverse-proxy-trust-boundary).
- **The web proxy's allow-list.** Only `/api/*` is reachable through it. Do not widen it to the attestation endpoints.
- **The backend token never reaches the browser.** Keep backend calls in server-only code.
- **No secrets in the repository.** Only `.env.example` templates are tracked.
