# Getting Started

RaceControl is a monorepo of independent pieces. To run it locally you start the backend, then whichever client you want to work on.

```bash
git clone https://github.com/Race-Control-HQ/Race-Control.git
cd Race-Control
```

## The order to do things in

1. **[Run the backend](/getting-started/backend).** Every client needs it. It runs natively with no auth and no configuration.
2. **Run a client.** Pick the one you care about:
   - [iOS app](/getting-started/ios) (Xcode 16+, macOS)
   - [Android app](/getting-started/android) (Android Studio with AGP 9.4 support, JDK 17)
   - [Web app](/getting-started/web) (Node 20+)
3. **Optionally, [run the project site or these docs](/getting-started/site-and-docs).** Neither needs the backend.

## What you need

| Piece | Requirements |
|---|---|
| Backend | Python 3.10+ |
| iOS app | Xcode 16+, macOS |
| Android app | Android Studio with AGP 9.4 support, JDK 17, Android SDK Platform 37 |
| Web app | Node 20+ |
| Project site | Node 20+ |
| Docs | Node 20+ |

You only need the toolchain for the pieces you plan to run. Most changes touch one piece.

## How the pieces connect locally

| Client | Default backend address | Notes |
|---|---|---|
| iOS app | `https://racecontrol.owl-media.co.uk` | The production backend. Change `AppConfig.apiBaseURL` to use a local one. |
| Android app | `http://10.0.2.2:8000` | The emulator's alias for `localhost` on your machine. |
| Web app | `RACECONTROL_API_BASE_URL` in `.env.local` | Set to `http://localhost:8000` for a local backend. |

A local backend started with `./run.sh` has no authentication, so no client needs a token to talk to it.

## Next steps

- New to the project? Take the [feature tour](/features/index) to see what the apps do.
- Planning to change something? Read [Contributing](/contributing/index) first.
- Want your own deployment? See [Self-Hosting](/self-hosting/index).
