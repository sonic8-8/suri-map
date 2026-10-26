# [Android] 앱을 다시 실행하면 로그인이 풀리고 다시 로그인할 수 없는 문제

# 본문

## 문제 배경

GPS 수집 주기 비교 테스트를 준비하면서 Galaxy A21s(`SM_A217N`)에 새 디버그 APK를 설치했다. 기존 앱 데이터와 로그인 상태를 남겨 두려고 `adb install -r` 명령으로 APK만 덮어썼다. 이후 앱을 강제 종료하고 다시 실행하자 `SSO 계정 인증` 버튼이 나타났다.

앱 안에는 OIDC 세션이 그대로 남아 있었다. OIDC 세션은 인증 서버에서 받은 로그인 상태로, 앱은 이 값을 저장했다가 다음 실행 때 다시 읽는다. APK를 덮어쓰거나 앱을 강제 종료해도 사용자가 로그아웃한 것은 아니므로 앱은 저장된 세션으로 먼저 인증을 이어가야 한다. 세션을 갱신하지 못했을 때만 다시 로그인을 요청하면 된다. 그러나 실제로는 저장된 세션을 사용하지 않고 곧바로 로그인을 다시 요구했다.

다시 로그인하려고 `SSO 계정 인증` 버튼을 눌렀지만 앱은 로그인 화면 대신 현재 사용하지 않는 `https://k14c106.p.ssafy.io/keycloak/realms/suri-map` 주소를 열었다. 브라우저에는 `DNS_PROBE_FINISHED_BAD_CONFIG` 오류가 나타났다. 현재 인증 주소는 `https://suri-map.sonic8-8.com/keycloak/realms/suri-map`이다.

이 때문에 저장된 로그인을 이어갈 수도 없고 새로 로그인할 수도 없어 앱 사용이 막혔다. 현장에서 같은 문제가 발생하면 사용자는 수색 화면으로 돌아가지 못한다.

이번에는 이 기기에 설치한 디버그 APK를 현재 배포 서버에 연결해 문제를 재현했다. 모든 기기와 배포용 APK에서 같은 문제가 발생하는지는 확인하지 않았다. 다만 저장된 세션을 인증 화면에 넘기지 않는 코드와 이전 SSAFY 인증 주소가 남은 빌드 설정은 함께 확인했다.

## 원인 분석과 선택지

앱은 시작할 때 `OidcSessionStateStore`에서 OIDC 세션을 읽어 `persistedOidcSession`으로 복원했다. 하지만 인증 화면을 여는 `AuthBootstrapRoute`는 복원된 세션을 전달받지 못한 채 로그인 상태를 `null`로 초기화했다. `null`은 로그인 정보가 없다는 뜻이므로 인증 화면은 사용자가 로그인하지 않았다고 판단했다.

`AuthBootstrapRoute`가 세션 저장소를 다시 읽게 만들 수도 있다. 그러나 앱이 이미 같은 세션을 읽고 있으므로 저장소를 두 번 확인할 이유가 없다. 그래서 앱이 복원한 세션을 인증 화면에 바로 넘기는 방법을 선택했다. 저장된 세션이 없거나 갱신에 실패하면 기존 로그인 화면을 보여준다.

새 로그인에 실패한 원인은 빌드 기본값에 이전 SSAFY Keycloak 주소가 남아 있었기 때문이다. Keycloak은 로그인과 접근 권한을 확인하는 인증 서버다. 각 빌드 환경에 `suriMapKeycloakIssuerUrl`을 반드시 입력하게 만들거나, 별도 값이 없을 때 현재 API 주소로 인증 주소를 만들 수 있다.

현재 배포 환경에서는 API와 Keycloak이 같은 서버를 사용한다. 같은 서버 주소를 두 설정에 반복할 이유가 없으므로 별도 값이 없으면 API 주소에 `/keycloak/realms/suri-map` 경로를 붙여 인증 주소를 만들도록 정했다. 다만 API와 Keycloak이 다른 서버를 사용하거나 경로가 바뀌면 `suriMapKeycloakIssuerUrl`로 인증 주소를 직접 지정해야 한다.

# 댓글

## 변경 내용과 트레이드오프

앱이 복원한 로그인 세션을 인증 화면에도 전달하도록 수정했다. 이제 앱을 강제 종료한 뒤 다시 실행해도 세션이 남아 있으면 로그인 화면으로 보내지 않고 기존 로그인 상태를 이어서 사용한다.

인증 화면은 로그인 세션을 갱신한 뒤 heartbeat를 보낸다. 이 요청에는 갱신한 인증 토큰을 사용한다. 세션이 없거나 갱신에 실패할 때만 로그인 화면을 보여준다.

빌드 기본값에 남아 있던 이전 SSAFY 로그인 주소도 제거했다. `suriMapKeycloakIssuerUrl`을 지정하지 않으면 API 주소 뒤에 `/keycloak/realms/suri-map`을 붙여 로그인 주소를 만든다. API와 Keycloak이 서로 다른 서버를 쓰거나 경로가 바뀌면 이 값을 직접 지정해야 한다.

## 검증 결과

수정 전에는 로그인 세션이 남아 있던 5초 시험 앱을 강제 종료한 뒤 다시 실행했다. 서버는 health check에 `200`으로 응답했지만, 만료된 인증 토큰으로 보낸 heartbeat에는 `401 unauthorized`로 응답했다.

```text
health check succeeded status=200
heartbeat bootstrap failed status=401 error=unauthorized
```

수정한 시험 APK 네 개를 `adb install -r`로 Galaxy A21s(`SM_A217N`)에 다시 설치했다. 아래 명령에서 패키지 이름만 바꾸어 네 앱을 각각 강제 종료하고 다시 실행했다.

```bash
adb -P 16038 shell am force-stop com.surimap.gps5000
adb -P 16038 shell am start -n com.surimap.gps5000/com.surimap.MainActivity
```

| 시험 앱 | 실행 전 상태 | 확인 결과 |
|---|---|---|
| `com.surimap.gps2500` | 저장된 세션 없음 | 수리맵 로그인 화면에서 SSO 로그인한 뒤 다시 실행하자 사건 선택 화면으로 이동 |
| `com.surimap.gps5000` | 기존 세션 있음 | 세션 갱신 후 heartbeat `200`, 사건 선택 화면으로 이동 |
| `com.surimap.gps10000` | 기존 세션 있음 | 강제 종료 후 다시 실행하자 사건 선택 화면으로 이동 |
| `com.surimap.gps30000` | 기존 세션 있음 | 세션 갱신 후 heartbeat `200`, 사건 선택 화면으로 이동 |

수정 후 실기기에서는 갱신한 인증 토큰으로 보낸 heartbeat가 성공했다.

```text
health check succeeded status=200
heartbeat bootstrap succeeded status=200
```

저장된 세션을 heartbeat보다 먼저 갱신하는지, Keycloak 주소를 따로 지정하지 않으면 API 주소를 사용하는지 테스트했다. 이후 Android 전체 테스트를 실행했다.

```bash
./gradlew test
```

전체 테스트는 `BUILD SUCCESSFUL in 5m 5s`로 끝났다.

Galaxy A21s에 설치한 디버그 APK를 배포 서버에 연결해 확인했다. 배포용 APK에서는 아직 확인하지 않았으므로 Issue는 열어 둔다.
