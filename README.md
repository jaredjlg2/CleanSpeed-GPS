# CleanSpeed

CleanSpeed is a simple, ad-free Android GPS speedometer + trip tracker.

## Features

- Kotlin + Jetpack Compose single-screen UI
- Runtime location permission handling (`ACCESS_FINE_LOCATION`)
- Foreground location tracking service (Android 14-compatible `foregroundServiceType="location"`)
- Live speed + trip stats (distance, duration, avg speed, max speed)
- Unit toggle with persistence (mph / km/h / knots)
- Keep-screen-on toggle (persisted)
- GPS weak indicator using accuracy filtering
- Stop & Save trip history (last 20 shown in a bottom sheet)
- Local trip persistence with Room (trip summary + capped sampled points)
- Unit tests for haversine distance and unit conversion

## Requirements

- Android Studio Iguana+ (or equivalent)
- Android SDK 34
- JDK 17

## Run in Android Studio

1. Open this folder in Android Studio.
2. Let Gradle sync.
3. Run the `app` configuration on an emulator/device (API 26+).
4. Grant location permission when prompted.

## Build APKs via Gradle

From project root:

```bash
./gradlew assembleDebug
```

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`

For release APK:

```bash
./gradlew assembleRelease
```

Release APK output:

`app/build/outputs/apk/release/app-release.apk`

> Note: signing configuration is not included in this starter project. Configure signing for production release.
