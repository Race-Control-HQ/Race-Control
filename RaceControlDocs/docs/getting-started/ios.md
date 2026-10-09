# Run the iOS App

Requirements: **Xcode 16+**, macOS.

## Build and run

1. Open `RaceControlApp/RaceControl.xcodeproj` in Xcode.
2. Select an iPhone simulator and press **⌘R**.
3. The app connects to the production backend at `https://racecontrol.owl-media.co.uk`.

Users of the published app do not configure a server.

## Use a local or self-hosted backend

Change `AppConfig.apiBaseURL` in `RaceControlApp/RaceControl/Networking/APIClient.swift` before building.

| Running on | Address |
|---|---|
| Simulator | `http://localhost:8000` |
| Physical iPhone | Your Mac's LAN address, for example `http://192.168.1.20:8000` |
| Deployed backend | Your HTTPS URL |

For a physical iPhone, your Mac and iPhone must be on the same Wi-Fi.

Tap **Test Connection** in Settings to check reachability and authentication.

::: info Cleartext HTTP
The app's App Transport Security policy allows cleartext HTTP only to `localhost` and LAN addresses, so a deployed backend must be HTTPS.
:::

## Authentication

The published app authenticates with **Apple App Attest**, which only works on a real device. In the Simulator the app falls back to the admin token in Settings, or to an open local server.

The optional admin token in Settings is a development and break-glass credential. It is stored in the device Keychain.

See [Device Attestation](/architecture/authentication) for how the flow works.

## Run the tests

```bash
cd RaceControlApp
xcodebuild test -project RaceControl.xcodeproj -scheme RaceControl \
  -destination "platform=iOS Simulator,name=iPhone 16" CODE_SIGNING_ALLOWED=NO
```

## Regenerating the Xcode project

The project uses Xcode 16's synchronized file groups, so new Swift files added to `RaceControlApp/RaceControl/` are picked up automatically, with no project edits needed.

If you ever need to rebuild the project file from scratch, an [XcodeGen](https://github.com/yonaskolb/XcodeGen) spec is provided:

```bash
cd RaceControlApp
brew install xcodegen && xcodegen generate
```

## Where next

- [iOS architecture](/architecture/ios)
- [Design System](/architecture/design-system)
