# RaceControl for Android

The native Kotlin and Jetpack Compose client for the RaceControl backend, built against
Material 3 (minSdk 26). A counterpart to the iOS app, not a port: same backend, same data,
Android conventions where they differ from Apple's.

## Quick start

Requires an Android Studio release that supports AGP 9.4, JDK 17 and Android SDK Platform 37
(`compileSdk`). The Gradle wrapper pins Gradle 9.8.0.

```bash
./gradlew assembleDebug        # or open this folder in Android Studio
```

Start the backend first (`cd ../backend && ./run.sh`). On an emulator the app reaches it at
`http://10.0.2.2:8000` with no setup. On a physical device, set your machine's LAN address
under **⋮ → Settings → Server address** and tap **Test Connection**.

## Tests

```bash
./gradlew testDebugUnitTest
```

## Documentation

- [Run the Android app](https://docs.getracecontrol.com/getting-started/android)
- [Android architecture](https://docs.getracecontrol.com/architecture/android)
- [What's different from iOS, and why](https://docs.getracecontrol.com/features/platforms)
- [Feature inventory](https://docs.getracecontrol.com/architecture/android-feature-inventory):
  the screen-by-screen iOS to Android mapping
- [Device attestation (Play Integrity)](https://docs.getracecontrol.com/architecture/authentication)
- [Status](https://docs.getracecontrol.com/contributing/android-status) and
  [build plan](https://docs.getracecontrol.com/contributing/android-build-plan)

Questions? Ask in [Discussions](https://github.com/Race-Control-HQ/Race-Control/discussions).
To contribute, see the [contributing guide](https://docs.getracecontrol.com/contributing/).
