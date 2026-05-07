# Android Real-Device Smoke Checklist

`L4-B01` 범위에서는 feature behavior가 아니라 실행 기반만 확인한다.

## Preflight

1. `./gradlew :app:assembleDebug`
2. 디버그 APK를 Android 12+ 실기기에 설치한다.
3. 위치 권한, 백그라운드 위치 권한, 알림 권한을 허용한다.

## Smoke

1. 앱이 크래시 없이 실행되고 기본 Compose 화면이 열린다.
2. 앱 재실행 후에도 Room 초기화로 인한 크래시가 없다.
3. WorkManager 테스트 빌드가 `:app:testDebugUnitTest`에서 통과한다.
4. 네트워크를 끈 상태에서 앱 진입 후 즉시 크래시가 없다.
5. 네트워크를 다시 켠 뒤 앱 재진입 시 즉시 크래시가 없다.

## Evidence

- 실행 기기 모델명
- Android 버전
- APK 빌드 시각
- 실패 시 스택트레이스 또는 화면 캡처
