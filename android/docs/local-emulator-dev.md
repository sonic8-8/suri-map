# Android Local Emulator Dev

로컬 Android 에뮬레이터에서 `SuriMap` 앱 UI만 수정하면서 백엔드, 타일 서버, 인증 서버는 배포 도메인을 사용하는 성공 조합이다. 저장소 공유 설정을 바꾸지 않고, 사용자 로컬 Gradle 설정만 맞춘다.

## Recommended UI Dev Setup

이 방식이 Android UI 작업의 기본 루틴이다.

| Component | Host URL |
|---|---|
| Android app API | `https://k14c106.p.ssafy.io` |
| Keycloak issuer | `https://k14c106.p.ssafy.io/keycloak/realms/suri-map` |
| Tiles | `https://k14c106.p.ssafy.io/tiles` |
| Debug PolicePhone | `00000000-0000-0000-0000-000000000101` |

Windows 사용자 로컬 Gradle 설정에만 둔다. 저장소의 `android/gradle.properties`에는 개인 실행값을 넣지 않는다.

File:

```text
C:\Users\SSAFY\.gradle\gradle.properties
```

Current working values:

```properties
suriMapDebugApiBaseUrl=https://k14c106.p.ssafy.io
suriMapKeycloakIssuerUrl=https://k14c106.p.ssafy.io/keycloak/realms/suri-map
suriMapDebugBootstrapPolicePhoneId=00000000-0000-0000-0000-000000000101
```

에뮬레이터가 실행된 상태에서 Android UI 변경을 반영한다.

```bash
cd android
./gradlew :app:installDebug && adb shell am start -n com.surimap/.MainActivity
```

Windows PowerShell에서는 다음처럼 실행한다.

```powershell
cd C:\Users\SSAFY\Desktop\S14P31C106\android
.\gradlew.bat :app:installDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.surimap/.MainActivity
```

`Device Manager`에서 에뮬레이터를 껐다 켜는 것만으로는 앱 코드가 반영되지 않는다. UI 수정 후에는 항상 debug APK를 다시 빌드/설치해야 한다.

`INSTALL_FAILED_INSUFFICIENT_STORAGE`가 나면 Gradle install 대신 streamed install로 대체한다.

```powershell
cd C:\Users\SSAFY\Desktop\S14P31C106\android
.\gradlew.bat :app:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.surimap/.MainActivity
```

만약 streamed install도 같은 저장공간 오류로 실패하면 기존 앱을 한 번 지운 뒤 설치한다. 이 경우 앱 데이터와 로그인 세션은 초기화된다.

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" uninstall com.surimap
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install app\build\outputs\apk\debug\app-debug.apk
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" shell am start -n com.surimap/.MainActivity
```

반복되는 `INSTALL_FAILED_INSUFFICIENT_STORAGE`는 코드 문제가 아니라 AVD `/data` 저장공간 부족이다. 현재 AVD가 `/data` 5.8GB 중 93% 이상 사용 중이면 덮어쓰기 설치가 실패할 수 있으므로, Android Studio Device Manager에서 Internal Storage가 더 큰 AVD를 새로 만들거나 기존 AVD 데이터를 정리한다.

## Optional Local Backend Setup

로컬 backend나 seed DB 자체를 검증해야 할 때만 아래 조합을 사용한다. Android UI만 수정하는 동안에는 위 원격 도메인 방식을 우선한다.

## Fixed Ports

| Component | Host URL |
|---|---|
| Android app API | `http://10.0.2.2:8081` |
| Windows backend | `http://127.0.0.1:8081` |
| Postgres | `localhost:5432` |
| Mock 112 | `http://localhost:18112` |
| Local Keycloak container | `http://localhost:18080/keycloak` |
| Android Keycloak issuer | `https://k14c106.p.ssafy.io/keycloak/realms/suri-map` |
| Local map tiles | backend fixture tile mode |

에뮬레이터에서 `127.0.0.1`은 PC가 아니라 에뮬레이터 자신이다. 로컬 backend에 붙일 때 앱의 backend URL은 반드시 `10.0.2.2`를 사용한다.

## User-Local Gradle

로컬 backend에 붙일 때도 저장소의 `android/gradle.properties`에 개인 실행값을 넣지 않는다. Windows 사용자 로컬 파일에 둔다.

File:

```text
C:\Users\SSAFY\.gradle\gradle.properties
```

Local backend values:

```properties
suriMapDebugApiBaseUrl=http://10.0.2.2:8081
suriMapKeycloakIssuerUrl=https://k14c106.p.ssafy.io/keycloak/realms/suri-map
suriMapDebugBootstrapPolicePhoneId=00000000-0000-0000-0000-000000000101
```

`suriMapDebugBootstrapPolicePhoneId`는 Knox/MDM managed config만 대체한다. 로그인은 여전히 Keycloak 계정으로 진행되며, 폴리폰의 account와 로그인 계정이 맞아야 한다.

## Login Pair

현재 안정 조합:

```text
PolicePhone: 00000000-0000-0000-0000-000000000101
Phone code: dev-precinct-phone-01
Login ID: acct-precinct-team
Password: fixture
```

다른 계정으로 로그인하면 `이 폴리폰으로 접속할 수 없습니다. IT 부서 문의`가 뜬다.

## Local DB Assignment

위 폴리폰 계정이 여러 사건을 볼 수 있게 로컬 DB에서 열린 사건을 `acct-precinct-team`에 배정했다.

Reapply if the Postgres volume is reset:

```sql
INSERT INTO incident_assignment (
    id,
    incident_id,
    account_id,
    incident_role,
    assigned_at,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    i.id,
    '11111111-1111-1111-1111-111111110003'::uuid,
    'MEMBER',
    now(),
    now(),
    now()
FROM incident i
WHERE i.status = 'OPEN'
  AND NOT EXISTS (
      SELECT 1
      FROM incident_assignment ia
      WHERE ia.incident_id = i.id
        AND ia.account_id = '11111111-1111-1111-1111-111111110003'::uuid
        AND ia.revoked_at IS NULL
  );
```

Confirm:

```bash
docker exec surimap-postgres psql -U surimap -d surimap -c "SELECT i.title, a.login_id FROM incident i JOIN incident_assignment ia ON ia.incident_id = i.id AND ia.revoked_at IS NULL JOIN account a ON a.id = ia.account_id WHERE ia.account_id = '11111111-1111-1111-1111-111111110003'::uuid ORDER BY i.created_at DESC;"
```

Expected: `acct-precinct-team` sees the open incident list, not only `종로구 인왕산 실종 신고`.

## Backend

Start infrastructure:

```bash
docker compose -f infra/docker-compose.yml up -d postgres mock-112 keycloak-postgres keycloak
```

Start backend from Windows PowerShell with Keycloak JWT validation enabled:

```powershell
$env:SERVER_PORT="8081"
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/surimap"
$env:SPRING_DATASOURCE_USERNAME="surimap"
$env:SPRING_DATASOURCE_PASSWORD="surimap"
$env:MOCK_112_ENABLED="true"
$env:MOCK_112_BASE_URL="http://localhost:18112"
$env:SURI_MAP_KEYCLOAK_ISSUER_URI="https://k14c106.p.ssafy.io/keycloak/realms/suri-map"
$env:SURI_MAP_KEYCLOAK_JWK_SET_URI="https://k14c106.p.ssafy.io/keycloak/realms/suri-map/protocol/openid-connect/certs"
$env:TILESERVER_MODE="fixture"
.\gradlew.bat --no-daemon --project-cache-dir "$env:USERPROFILE\surimap-backend-project-cache" bootRun
```

`TILESERVER_MODE=fixture` is required for this local emulator setup because real `.mbtiles` files and `tileserver-gl` are not part of the normal local Docker startup. Without it, the app can login but MapLibre receives `503` from `/tiles/styles/osm-local.json` and the map stays blank.

Health check:

```powershell
curl http://127.0.0.1:8081/api/health
```

Expected:

```json
{"status":"UP","service":"suri-map-api"}
```

## Build And Install

From Windows PowerShell:

```powershell
cd C:\Users\SSAFY\Desktop\S14P31C106\android
.\gradlew.bat :app:assembleDebug
```

Install manually if Gradle install reports emulator storage issues:

```powershell
& "C:\Users\SSAFY\AppData\Local\Android\Sdk\platform-tools\adb.exe" uninstall com.surimap
& "C:\Users\SSAFY\AppData\Local\Android\Sdk\platform-tools\adb.exe" install "C:\Users\SSAFY\Desktop\S14P31C106\android\app\build\outputs\apk\debug\app-debug.apk"
& "C:\Users\SSAFY\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.surimap/.MainActivity
```

## Retry Login

If Keycloak keeps using the previous account, clear Chrome in the emulator:

```powershell
& "C:\Users\SSAFY\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell pm clear com.android.chrome
```

Then open the app and login with:

```text
acct-precinct-team / fixture
```

## Troubleshooting

`내부망 연결 확인` means the app cannot reach backend.

- Check backend health on Windows: `curl http://127.0.0.1:8081/api/health`
- Check app API URL in generated build config: `SURI_MAP_API_BASE_URL` must be `http://10.0.2.2:8081`
- `Device Manager` restart alone does not update the app. Rebuild and install the APK.

Blank map with `loading style failed: HTTP status code 503` means backend is trying to proxy to `tileserver-gl`.

- Restart backend with `TILESERVER_MODE=fixture`
- The normal local Docker startup does not start `surimap-tileserver-gl`
- Real tiles require prepared `.mbtiles`; fixture mode is enough for local Android UI work

`이 폴리폰으로 접속할 수 없습니다. IT 부서 문의` means authentication reached backend, but PolicePhone binding failed.

- Confirm login account is `acct-precinct-team`
- Confirm generated build config has `SURI_MAP_DEBUG_BOOTSTRAP_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101"`
- Confirm backend was started with `SURI_MAP_KEYCLOAK_ISSUER_URI` and `SURI_MAP_KEYCLOAK_JWK_SET_URI`
- Clear Chrome session if a previous Keycloak account is reused

`SuriMap keeps stopping` after login usually means AppAuth tried to use an HTTP token endpoint.

- `suriMapKeycloakIssuerUrl` must be HTTPS: `https://k14c106.p.ssafy.io/keycloak/realms/suri-map`
