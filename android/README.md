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
