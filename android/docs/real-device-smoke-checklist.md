# Android Real-Device Smoke Checklist

`L4-B01` 범위에서는 feature behavior가 아니라 실행 기반만 확인한다.

Android UI 로컬 개발은 배포 도메인 backend/tile/auth에 연결하는 [local-emulator-dev.md](local-emulator-dev.md)의 Recommended UI Dev Setup을 따른다. 로컬 backend + Docker Postgres 조합은 같은 문서의 Optional Local Backend Setup을 사용한다.

## Preflight

1. `./gradlew :app:assembleDebug`
2. 디버그 APK를 Android 12+ 실기기에 설치한다.
3. 위치 권한, 백그라운드 위치 권한, 알림 권한을 허용한다.
4. Knox/managed configuration 검증 전에는 debug bootstrap을 사용한다.
   - 기본 debug API URL: `http://127.0.0.1:8080`
   - 기본 debug bootstrap police phone id: `00000000-0000-0000-0000-000000000101`
   - USB 연결 실기기는 `adb reverse tcp:8080 tcp:8080` 후 실행한다.
   - URL을 바꿔야 하면 `-PsuriMapDebugApiBaseUrl=http://<host>:<port>`로 빌드한다.
   - Keycloak issuer를 바꿔야 하면 `-PsuriMapKeycloakIssuerUrl=https://<host>/keycloak/realms/suri-map`로 빌드한다.
   - debug bootstrap은 Knox/MDM managed config만 대체한다. 사용자 인증은 앱에서 Keycloak 로그인 화면을 열어 진행한다.
   - 명시적 단말 값으로 빌드하려면 `-PsuriMapDebugBootstrapPolicePhoneId=...`를 사용한다.
5. 배포 서버 기준 검증은 로컬 backend/adb reverse 대신 다음 스크립트를 사용한다.
   - 기본 배포 서버: `https://k14c106.p.ssafy.io`
   - 에뮬레이터/실기기 설치: `bash infra/dev/android-deployed-device-smoke.sh --reset-app`
   - 특정 기기 지정: `bash infra/dev/android-deployed-device-smoke.sh --device emulator-5554 --reset-app`
   - 아직 배포 서버에 `Pretendard GOV` glyph PBF가 배치되지 않은 경우 앱 설치까지 강행하려면 `--allow-glyph-failure`를 붙인다.
   - 스크립트는 `/api/health`, Keycloak OIDC discovery, `/tiles/styles/osm-local.json`, vector tile, `Pretendard GOV` glyph PBF를 먼저 확인한 뒤 해당 서버 URL로 debug APK를 빌드한다.

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
