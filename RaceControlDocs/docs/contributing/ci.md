# Continuous Integration

Independent GitHub Actions workflows live in `.github/workflows/`. Each is path-filtered, so a change to one piece does not run the others' checks.

[![Backend CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/backend-ci.yml)
[![Android CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/android-ci.yml)
[![iOS CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/ios-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/ios-ci.yml)
[![Web CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/web-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/web-ci.yml)
[![Site CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/site-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/site-ci.yml)
[![Docs CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/docs-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/docs-ci.yml)

## Workflows

| Workflow | Runs on changes to | What it does |
|---|---|---|
| `backend-ci.yml` | `backend/**` | Installs the pip-compile lock `requirements.txt`, checks it covers every direct dependency in `requirements.in`, runs the whole `pytest` suite, then imports `main.py` with no environment variables to confirm the open-by-default local-dev path still boots. |
| `android-ci.yml` | `RaceControlAndroid/**` | `./gradlew testDebugUnitTest`, then `./gradlew assembleDebug`. Uploads the test reports as a build artifact. |
| `ios-ci.yml` | `RaceControlApp/**` | Unsigned `xcodebuild build` against `generic/platform=iOS Simulator`, then `xcodebuild test` on an iPhone simulator, on a macOS runner. |
| `web-ci.yml` | `RaceControlWeb/**` | `npm ci`, `npm run lint`, `npm run build` for the web app. |
| `site-ci.yml` | `RaceControlSite/**` | `npm ci`, `npm run lint`, `npm run build` for the project site. |
| `docs-ci.yml` | `RaceControlDocs/**` | `npm ci`, `npm run docs:build` for this site. The build fails on dead internal links. |

A further workflow, `labeler.yml`, labels pull requests by the component they touch, using the mapping in `.github/labeler.yml`.

Each workflow also runs when its own file changes, and can be started by hand from the Actions tab.

## CI only

Nothing here deploys anywhere.

- Deploying the backend, web app, site and docs is done from the hosting platform. See [Self-Hosting](/self-hosting/index).
- There is no Play Store or TestFlight upload wired up. Those would need a Play Console service-account key and Apple signing certificates as repository secrets, which have not been set up.

## Release notes

`.github/release.yml` defines the categories for GitHub's auto-generated release notes. Pull requests are sorted by label, and the first matching category wins: new features, bug fixes, accessibility, documentation, dependencies, CI and tooling, then everything else.
