# Contributing

Thanks for your interest in RaceControl. Bug reports, feature ideas and pull requests are all welcome.

By taking part you agree to follow the [Code of Conduct](/contributing/code-of-conduct).

## Before you start

| You want to | Do this |
|---|---|
| Report a bug or request a feature | Open an [issue](https://github.com/Race-Control-HQ/Race-Control/issues/new/choose) using the matching form. Search existing issues first. |
| Ask a question or share an idea | Start a [discussion](https://github.com/Race-Control-HQ/Race-Control/discussions). |
| Report a security vulnerability | **Do not open a public issue.** Follow the [Security Policy](/contributing/security). |
| Make a larger change | Open an issue to discuss the approach before writing code, so nobody builds something that cannot be merged. |
| Fix a typo or improve these docs | Use the "Edit this page on GitHub" link at the bottom of any page. See [Writing Documentation](/contributing/documentation). |

## Repository layout

RaceControl is a monorepo of independent pieces. Most changes touch only one.

| Piece | Folder | Stack |
|---|---|---|
| Backend | `backend/` | Python 3.12, FastAPI, FastF1 |
| iOS app | `RaceControlApp/` | Swift, SwiftUI, Xcode 16+ |
| Android app | `RaceControlAndroid/` | Kotlin, Jetpack Compose, JDK 17 |
| Web app | `RaceControlWeb/` | TypeScript, Next.js, Node 20 |
| Project site | `RaceControlSite/` | TypeScript, Next.js, Node 20 |
| Documentation | `RaceControlDocs/` | VitePress, Node 20 |

## Platform parity

The iOS, Android and web apps are independent clients, each following its own platform's conventions.

A feature added to one does not have to land in the others in the same pull request. Say so in the description, so parity can be tracked. See the [Visual Parity Checklist](/contributing/visual-parity).

## The path of a contribution

1. **Set up.** Follow [Getting Started](/getting-started/index) for the piece you are changing.
2. **Make the change.** Keep it focused, and add or update tests.
3. **Run the checks.** The same commands CI runs are in [Development Setup](/contributing/development).
4. **Update the docs.** If you change setup steps, endpoints or configuration, update the matching page here in the same pull request.
5. **Open a pull request.** See [Pull Requests](/contributing/pull-requests).

## Good places to start

- The open items in [Android Status](/contributing/android-status).
- Gaps in the [Visual Parity Checklist](/contributing/visual-parity) table.
- Automated tests for the backend's Play Integrity verification, which has none yet.
- Issues labelled [`good first issue`](https://github.com/Race-Control-HQ/Race-Control/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22).

## Licence

By contributing you agree that your contributions are licensed under the [MIT License](https://github.com/Race-Control-HQ/Race-Control/blob/main/LICENSE).
