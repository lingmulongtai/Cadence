# Cadence

**An Android app with peripheral visual cues that respond to vehicle acceleration.**

車両の加速度に応じた画面端の視覚キューで、移動中の画面閲覧を支えるAndroidアプリ。

[日本語](README.md) · [Development plan](docs/development.md)

## Status

The native Kotlin rewrite is in progress.
**[Download recorder preview v0.1.0-alpha.1](https://github.com/lingmulongtai/Cadence/releases/tag/v0.1.0-alpha.1)**.
Record on your phone, choose a filename and folder, or share a ZIP directly. No PC or USB debugging is needed.
This prerelease does not include motion cues, vehicle detection or hand-motion filtering.
Phase 0 foundation and launch verification are complete; see the [verification record](docs/verification/phase-0.md).
Phase 1 includes a debug-only sensor recorder and JVM raw CSV replay.
The next step is collecting 3–5 minute physical city-drive and stationary hand-shaking sessions;
see the [collection guide](docs/recording.md). Phase 1 remains incomplete until real recordings are verified,
and phase 2 will not start before that gate is met.
See the [recording verification and commit history](docs/verification/phase-1.md).
The original Expo implementation remains on `legacy/expo` at `72c7cf6`.
Old completion checkboxes do not establish native functionality.
Screenshots and an overlay GIF will be added after the renderer is implemented and verified.

## Purpose

Cadence is intended for Android 8.0+ devices without built-in motion assistance.
The primary physical test device is a Japanese-carrier Galaxy S25 Ultra.
Availability of OS features depends on the device, region and software update.

The planned pipeline rotates linear acceleration into a horizontal reference frame using
non-magnetic orientation, estimates a vehicle axis and filters higher-frequency hand motion.
Cues represent acceleration, not integrated velocity. Devices without a gyroscope will use
a reduced-accuracy path. These properties still require real sensor recordings and road tests.
Cadence may help reduce discomfort; it does not promise medical benefit. Do not use it while driving.
Drawing above system UI, the notification shade, Quick Settings or the lock screen is out of scope.

## Installation and privacy

Download `Cadence-0.1.0-alpha.1-recorder.apk` from the
[prerelease page](https://github.com/lingmulongtai/Cadence/releases/tag/v0.1.0-alpha.1) on your Android 8.0+ phone.
Start recording → Stop and save → Save as. Choose Downloads or another folder and edit the ZIP filename.
Saved recordings remain available after reopening the app; Share ZIP opens the system share sheet.
See the [collection guide](docs/recording.md) and [verification/screenshots](docs/verification/recorder-alpha.1.md).

Production and F-Droid distribution remain future work. Play services recognition also needs a distribution
compatibility review before an F-Droid build can be offered.

The native app does not declare `INTERNET`. It has no networking, accounts, server,
ads or analytics SDK. Sensor data is not automatically transmitted. Debug CSV recordings are
stored in app-specific external storage. Only an explicit save/share action exports a ZIP containing
the original CSV and JSON device metadata. Cancellation or export failure leaves originals intact.
No latitude or longitude is recorded.
Cloud backup is disabled.

The current debug APK requests foreground-service, notification and optional location permissions
for recording, including `FOREGROUND_SERVICE_LOCATION` when GPS is selected.
The release variant excludes the recorder, CSV storage code and recording permissions.

Planned permissions: `SYSTEM_ALERT_WINDOW` for the overlay; `FOREGROUND_SERVICE` and
`FOREGROUND_SERVICE_SPECIAL_USE` for active work; `POST_NOTIFICATIONS` for controls on Android 13+;
optional `ACTIVITY_RECOGNITION` for vehicle detection; optional coarse/fine location for GPS;
battery-optimization exemption guidance in a later phase. Permissions are added as their features
are implemented. No screen capture or sampling above 200Hz is planned.

## Planned settings

Defaults: concentric adaptive halo, 8 medium dots, 60% opacity, medium gain,
1.5Hz cutoff and 50Hz sensors. 100Hz is opt-in. Auto-start and GPS assistance default to off.
A 30-second entry delay cancels on an exit event; a 60-second exit delay avoids flicker at stops.
Car, train/bus and boat presets will be tuned using recordings. These are later-phase features.

On Samsung devices, check Settings → Apps → Cadence → Battery, and remove Cadence from
sleeping/deep-sleeping apps under Battery / Device care → Background usage limits.
Names vary with One UI versions. In-app guidance will be added in the UI phase.

## Build

Use JDK 17, Android SDK Platform 37.0 and Build Tools 36.0.0.
Set `ANDROID_HOME` or `sdk.dir` in an untracked `local.properties`.
The wrapper downloads build dependencies; the installed app does not access the network.

```sh
./gradlew :app:assembleDebug :motion:test :app:lintDebug
```

On Windows use `./gradlew.bat`. Use an ASCII-only checkout path or a junction if AGP
rejects a path containing non-ASCII characters.
Debug output: `app/build/outputs/apk/debug/app-debug.apk`.

Modules: `:app` (Compose UI, tiles), `:overlay` (service, sensor ownership and Canvas renderer),
`:motion` (Android-independent Kotlin, replay and JVM tests), `:sensor` (platform wrappers),
`:data` (DataStore Preferences).

CPU use, one-hour battery use, latency and physical efficacy are unmeasured.
Emulator and synthetic tests do not satisfy physical acceptance criteria.

## License and acknowledgements

**GPL-3.0-only**; see [LICENSE](LICENSE).
Acknowledgement to the independent open-source
[Vehicle Motion Cues](https://f-droid.org/packages/dev.davidv.motionsickness/).
Cadence does not port its code or reproduce Apple's UI.
