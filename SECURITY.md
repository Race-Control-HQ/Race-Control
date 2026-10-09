# Security Policy

## Supported versions

Only the latest commit on `main` and the currently deployed backend, web app and store
builds are supported. Fixes are not backported.

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub:
[Report a vulnerability](https://github.com/Race-Control-HQ/Race-Control/security/advisories/new).

Include as much of the following as you can:

- which piece is affected (backend, iOS, Android, web app or site)
- steps to reproduce, or a proof of concept
- the impact you believe it has

You can expect an acknowledgement within 7 days. Once the report is confirmed, a fix will be
prepared privately and you will be credited in the advisory unless you ask not to be.

## Scope

Of particular interest:

- bypasses of device attestation (Apple App Attest, Google Play Integrity) or of the
  backend's token checks
- ways to reach the backend through the web app's BFF layer that it is meant to block
- secrets or keys exposed in the repository, build artefacts or API responses

Out of scope: issues in third-party services (FastF1, the F1 live-timing API,
Ergast/Jolpica), and denial of service by request volume alone.
