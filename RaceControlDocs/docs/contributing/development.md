# Development Setup

Each piece has its own CI workflow, path-filtered to its folder. Run the same commands locally before you push.

To get a piece running in the first place, see [Getting Started](/getting-started/index).

## Backend

```bash
cd backend
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -m pytest -v
```

`./run.sh` starts the API on `http://localhost:8000`, with docs at `/docs`.

### Dependencies

Dependencies are managed with [pip-tools](https://pip-tools.readthedocs.io).

- `requirements.in` is the hand-edited spec, setting floors and ceilings.
- `requirements.txt` is the compiled lock: the reproducible, exact-version install used by CI and the Docker image.

If you change `requirements.in`, recompile with the command in its header and commit both files together. CI fails if the lock is missing a direct dependency.

### Tests

Tests are split by feature, with one `test_*.py` file per domain. Add to the matching file, or add a new one for a new domain.

Before changing `fastf1_service.py` or `analytics_service.py`, read [Backend architecture](/architecture/backend) for the module boundaries and the conventions that must hold.

## iOS

```bash
cd RaceControlApp
xcodebuild test -project RaceControl.xcodeproj -scheme RaceControl \
  -destination "platform=iOS Simulator,name=iPhone 16" CODE_SIGNING_ALLOWED=NO
```

New Swift files under `RaceControlApp/RaceControl/` are picked up automatically. You do not need to edit the project file.

## Android

```bash
cd RaceControlAndroid
./gradlew testDebugUnitTest assembleDebug
```

Logic that has no Android dependency should have a JVM unit test. If you diverge from the iOS behaviour on purpose, record why in the [Android Feature Inventory](/architecture/android-feature-inventory).

## Web app and project site

```bash
cd RaceControlWeb   # or RaceControlSite
npm ci
npm run lint
npm run build
```

If you change the apps or the API, also update the site's data modules, `RaceControlSite/src/lib/features.ts` and `src/lib/endpoints.ts`. They are hand-maintained.

## Documentation

```bash
cd RaceControlDocs
npm ci
npm run docs:build
```

The build fails on dead internal links. See [Writing Documentation](/contributing/documentation).

## Changes that touch a chart

If your change adds or alters a visualization on any platform, or changes a chart-ready payload in `analytics_service.py`, work through the [Visual Parity Checklist](/contributing/visual-parity).

## Secrets

Never commit secrets. Real `.env` files, service-account keys and signing material are git-ignored. Only the `.env.example` templates are tracked.

If you add a new environment variable, add it to the matching `.env.example` and to [Environment Variables](/self-hosting/environment).
