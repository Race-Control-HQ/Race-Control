# RaceControl for iOS

The native SwiftUI client for the RaceControl backend, targeting iOS 17 and later.

## Quick start

Requires Xcode 16+ on macOS.

1. Open `RaceControl.xcodeproj` in Xcode.
2. Select an iPhone simulator and press **⌘R**.

The app connects to the production backend out of the box. To use a local or self-hosted
backend, change `AppConfig.apiBaseURL` in `RaceControl/Networking/APIClient.swift`.

## Tests

```bash
xcodebuild test -project RaceControl.xcodeproj -scheme RaceControl \
  -destination "platform=iOS Simulator,name=iPhone 16" CODE_SIGNING_ALLOWED=NO
```

## Documentation

- [Run the iOS app](https://docs.getracecontrol.com/getting-started/ios)
- [iOS architecture](https://docs.getracecontrol.com/architecture/ios)
- [Device attestation (App Attest)](https://docs.getracecontrol.com/architecture/authentication)
- [Design system](https://docs.getracecontrol.com/architecture/design-system)

Questions? Ask in [Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions).
To contribute, see the [contributing guide](https://docs.getracecontrol.com/contributing/).
