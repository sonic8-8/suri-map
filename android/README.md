# Suri-Map Android

Android client for field phones.

## Stack

- Kotlin
- Jetpack Compose
- Room
- WorkManager
- MapLibre Native Android

## Local Build

This machine is configured with:

- Android SDK: `/home/seaung13/Android/Sdk`
- Gradle Wrapper: `./gradlew`

Build a debug APK:

```bash
./gradlew :app:assembleDebug
```

The app targets Android 12+ (`minSdk = 31`) to match the PRD's Galaxy S22 / Android 12 baseline.

## L4-B01 Baseline

- Room schema export: `app/schemas`
- Unit test baseline: `./gradlew :app:testDebugUnitTest`
- WorkManager deterministic harness: `OutboxWorkerTest`
- Mock network fixtures: `app/src/test/java/com/surimap/testing/NetworkStateFixtures.kt`
- Real-device smoke checklist: [docs/real-device-smoke-checklist.md](docs/real-device-smoke-checklist.md)
