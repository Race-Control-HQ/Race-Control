# RaceControl 🏁

Modern, **native iOS and Android apps** and a web app for exploring historical Formula 1
data: drivers, teams, circuits, standings, full session results, telemetry and a
**lap-by-lap race replay**, powered by the [FastF1](https://docs.fastf1.dev) library
(2018–present).

[![Backend CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/backend-ci.yml)
[![Android CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/android-ci.yml)
[![iOS CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/ios-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/ios-ci.yml)
[![Web CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/web-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/web-ci.yml)
[![Site CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/site-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/site-ci.yml)
[![Docs CI](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/docs-ci.yml/badge.svg)](https://github.com/Race-Control-HQ/Race-Control/actions/workflows/docs-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**[Documentation](https://docs.getracecontrol.com)** ·
**[Website](https://getracecontrol.com)** ·
**[Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions)** ·
**[Report a bug](https://github.com/Race-Control-HQ/Race-Control/issues/new/choose)**

## What's in the repo

| Piece | Tech | Folder |
|------|------|--------|
| **iOS app** | Swift · SwiftUI (iOS 17+) | [`RaceControlApp/`](RaceControlApp/) |
| **Android app** | Kotlin · Jetpack Compose (Android 8+) | [`RaceControlAndroid/`](RaceControlAndroid/) |
| **Web app** | TypeScript · Next.js | [`RaceControlWeb/`](RaceControlWeb/) |
| **Data backend** | Python · FastAPI · FastF1 | [`backend/`](backend/) |
| **Project site** | TypeScript · Next.js | [`RaceControlSite/`](RaceControlSite/) |
| **Documentation** | VitePress | [`RaceControlDocs/`](RaceControlDocs/) |

Same backend, same data, same features. The iOS, Android and web apps are independent
clients, each following its own platform's conventions rather than one being a port of
another.

## Quick start

Start the backend first. Every client needs it. Requires Python 3.10+.

```bash
git clone https://github.com/Race-Control-HQ/Race-Control.git
cd Race-Control/backend
./run.sh            # creates a venv, installs deps, starts the API on :8000
```

Open http://localhost:8000/docs for the interactive API docs. A local backend needs no
configuration and no auth.

Then run whichever client you want:

| Client | Requirements | Run it |
|---|---|---|
| iOS | Xcode 16+ | Open `RaceControlApp/RaceControl.xcodeproj`, pick a simulator, press ⌘R |
| Android | Android Studio Ladybug+, JDK 17 | `cd RaceControlAndroid && ./gradlew assembleDebug`, or open the folder in Android Studio |
| Web | Node 20+ | `cd RaceControlWeb && cp .env.example .env.local && npm install && npm run dev` |

Full instructions for each piece, including physical devices and pointing the apps at your
own backend, are in **[Getting Started](https://docs.getracecontrol.com/getting-started/)**.

## Documentation

Everything in depth lives at **[docs.getracecontrol.com](https://docs.getracecontrol.com)**:

| | |
|---|---|
| [Getting Started](https://docs.getracecontrol.com/getting-started/) | Run the backend and each client locally |
| [Features](https://docs.getracecontrol.com/features/) | What the apps do, and where the platforms deliberately differ |
| [API Reference](https://docs.getracecontrol.com/api/) | Every backend endpoint |
| [Architecture](https://docs.getracecontrol.com/architecture/) | How one backend feeds three clients |
| [Self-Hosting](https://docs.getracecontrol.com/self-hosting/) | Deploy your own copy, with environment variables and device attestation |
| [Contributing](https://docs.getracecontrol.com/contributing/) | Development setup, CI, pull requests |
| [Help](https://docs.getracecontrol.com/help/) | Troubleshooting and common questions |

The docs are built from [`RaceControlDocs/`](RaceControlDocs/). Every page has an
"Edit this page on GitHub" link.

## Community

- 💬 **Questions and ideas:** [GitHub Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions)
- 🐛 **Bugs and feature requests:** [open an issue](https://github.com/Race-Control-HQ/Race-Control/issues/new/choose) using the matching form
- 🔒 **Security issues:** report privately, as described in [SECURITY.md](SECURITY.md). Please do not open a public issue.
- 🤝 **Code of conduct:** everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md)

## Contributing

Issues and pull requests are welcome, and most changes touch only one piece of the repo.
The [contributing guide](https://docs.getracecontrol.com/contributing/) covers setup, the
checks CI runs and what a good pull request looks like. For anything larger, open an issue
to talk through the approach first.

## Data and attribution

Data is sourced live by FastF1 from the F1 live-timing API and the Ergast/Jolpica database.
This is an unofficial project and is not associated with Formula 1 companies. F1, FORMULA 1
and related marks are trademarks of Formula One Licensing BV.

## License

Released under the [MIT License](LICENSE).
