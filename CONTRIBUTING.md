# Contributing to RaceControl

Thanks for your interest in RaceControl. Bug reports, feature ideas and pull requests are
all welcome. By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Before you start

- **Bugs and feature requests:** open an [issue](https://github.com/Race-Control-HQ/Race-Control/issues/new/choose)
  using the matching form. Search existing issues first.
- **Security vulnerabilities:** do not open a public issue. Follow [SECURITY.md](SECURITY.md).
- **Larger changes:** open an issue to discuss the approach before writing code, so nobody
  builds something that can't be merged.

## Repository layout

RaceControl is a monorepo of five independent pieces. Most changes touch only one.

| Piece | Folder | Stack |
|---|---|---|
| Backend | [`backend/`](backend/) | Python 3.12 · FastAPI · FastF1 |
| iOS app | [`RaceControlApp/`](RaceControlApp/) | Swift · SwiftUI · Xcode 16+ |
| Android app | [`RaceControlAndroid/`](RaceControlAndroid/) | Kotlin · Jetpack Compose · JDK 17 |
| Web app | [`RaceControlWeb/`](RaceControlWeb/) | TypeScript · Next.js · Node 20 |
| Project site | [`RaceControlSite/`](RaceControlSite/) | TypeScript · Next.js · Node 20 |

The iOS, Android and web apps are independent clients, each following its own platform's
conventions. A feature added to one does not have to land in the others in the same pull
request, but say so in the description so parity can be tracked (see
[`docs/VISUAL_PARITY.md`](docs/VISUAL_PARITY.md)).

## Running the checks locally

Each piece has its own CI workflow, path-filtered to its folder. Run the same commands
before you push.

### Backend

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements-lock.txt
python -m pytest -v
```

`./run.sh` starts the API on `http://localhost:8000` (docs at `/docs`).

If you change `requirements.txt`, regenerate `requirements-lock.txt` with the command in
that file's header and commit both together. CI fails if the lock file is missing a direct
dependency.

### iOS

```bash
cd RaceControlApp
xcodebuild test -project RaceControl.xcodeproj -scheme RaceControl \
  -destination "platform=iOS Simulator,name=iPhone 16" CODE_SIGNING_ALLOWED=NO
```

### Android

```bash
cd RaceControlAndroid
./gradlew testDebugUnitTest assembleDebug
```

### Web app and project site

```bash
cd RaceControlWeb   # or RaceControlSite
npm ci
npm run lint
npm run build
```

## Pull requests

1. Fork the repository and create a branch from `main`.
2. Keep the change focused: one fix or feature per pull request.
3. Add or update tests for behaviour you change. The backend, iOS and Android pieces all
   have unit test suites.
4. Update the relevant README if you change setup steps, endpoints or configuration.
5. Fill in the pull request template and make sure CI is green.

Never commit secrets. Real `.env` files, service-account keys and signing material are
git-ignored; only the `.env.example` templates are tracked.

## Licence

By contributing you agree that your contributions are licensed under the
[MIT License](LICENSE).
