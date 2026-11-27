# 인증·권한과 업무폰 상태

로그인·채널·사건 접근·업무폰 상태를 바꿀 때 확인할 기록이다. 옛 S1-2 요구와 2026-09-21 정적 코드 대조 결과를 구분한다. 미구현 요구의 유효성과 범위는 후속 작업에서 합의하며, 이번 정리는 보안 검증 완료를 뜻하지 않는다.

## 계정과 인증 정보

- 제품의 계정·업무폰 구분과 개인·공유 계정 미정 사항은 [PRD](../prd.md#사건상황판)를 따른다. 옛 팀·순찰차 계정 seed를 현재 제품 정책으로 자동 채택하지 않는다. 계정 발급·중복 로그인 정책·실제 업무폰의 GMS/FCM 수신 가능성은 확인 대상이다.
- 기존 요구는 Keycloak 로그인, 서버의 JWT 검증, 앱의 서명된 업무폰 ID와 요청 헤더 일치, 일반 클라이언트의 `INTERNAL` 권한 획득 차단이다. 화면의 버튼 숨김과 서버 권한 검사는 별개다.
- **현재 차이**: [JWT 변환기](../../backend/src/main/java/com/surimap/account/security/KeycloakJwtAuthenticationConverter.java)는 계정 종류·소속을 JWT에서 읽지만 채널·업무폰 ID는 요청 헤더에서 가져온다. 역할도 두 계정 속성에서 파생한다. `INTERNAL` enum도 파싱하므로 JWT와 단말·채널의 결합, 계정 비활성화 반영, 내부 API 보호를 각 호출부와 함께 확인해야 한다.
- [보안 설정](../../backend/src/main/java/com/surimap/config/SecurityConfig.java)과 [인증 필터](../../backend/src/main/java/com/surimap/account/security/OidcBearerAuthenticationFilter.java)는 JWT decoder와 인증 객체를 사용한다. 서명 검증이 있다는 사실만으로 위 단말, 사건별 권한 요구까지 충족됐다고 판단하지 않는다. SSE 요청의 인증 보관 수정은 [기존 문제 기록](../problem-solving-records/19-authenticated-sse-access-denied-on-disconnect.md)을 참조한다.

## HTTPS 프록시와 브라우저 출처

- 같은 공개 웹 주소의 요청은 내부 HTTP 통신과 혼동하지 않고 같은 출처로 판단해야 한다. [Frontend nginx](../../frontend/nginx.conf)는 호스트 nginx가 확인한 외부 프로토콜·포트를 Backend로 전달하고, [Backend 설정](../../backend/src/main/resources/application.yml)의 `server.forward-headers-strategy: native`는 Tomcat이 이 정보로 요청 주소를 판정하도록 한다.
- 이 설정은 외부 호스트 nginx가 전달 헤더를 덮어쓰고 Frontend·Backend 포트를 외부에 직접 노출하지 않는 구성을 전제로 한다. Tomcat 기본값은 사설·루프백 프록시를 신뢰하며 특정 nginx 한 대만 제한한 설정은 아니다. Frontend는 별도 `Forwarded` 헤더를 제거한다. CORS 허용 목록·JWT·채널 권한은 이번 수정에서 바꾸지 않았다.
- 2026-09-28 `ffbec82b`을 Jenkins #37로 Hetzner에 배포했다. 같은 HTTPS 출처의 허용, 다른 출처와 위조 전달 헤더의 거부, 실제 로그인, 위치 보정 저장, 다른 탭의 SSE 수신과 지도 갱신을 확인했다. DB 이력 재전송, 재시작 복구 검증은 아니다. 원인, 실행 결과, 검증 한계는 [CORS 문제 기록](../problem-solving-records/25-browser-post-rejected-by-cors.md)을 따른다.

## 인증 서버 장애와 토큰 거부

- JWT 서명·만료 검증에서 거부된 토큰과, 인증 키를 가져오지 못해 검증할 수 없는 상태를 구분한다. 어느 경우에도 검증되지 않은 요청을 보호 API로 통과시키지 않는다.
- 2026-09-30 로컬 수정: 인증 필터는 잘못된 토큰의 `BadJwtException`을 기존 401로 처리하고, 나머지 `JwtException`은 503 `authentication_unavailable`로 응답한다. 장애 로그에는 예외 종류만 남기며 토큰·예외 메시지는 기록하지 않는다. [SecurityConfigTest](../../backend/src/test/java/com/surimap/SecurityConfigTest.java)는 실제 서명 토큰·로컬 HTTP 키 서버·Nimbus decoder·보안 필터를 연결해 키 조회 장애, 같은 토큰의 복구 후 통과, 만료·잘못된 서명의 거부를 대조한다.
- 웹은 기존 구현으로 SSE 503에서 로그인을 유지하고 재연결하며, 401에서는 세션을 지우고 로그인 화면으로 이동한다. 브라우저 응답 주입 대조 후, 2026-10-01 `f2169f5d`을 Jenkins #48로 배포해 열린 상황판의 로그인 유지, 재연결을 확인했다. 별도 실제 Keycloak 일시정지, Backend 재시작에서도 SSE 503과 복구 후 같은 토큰의 200 재연결을 확인했고, 마커 시험에서는 DB 이력 재전송, 지도 반영, 원상복원을 확인했다. 중단 중 502, 504도 발생했으며 무중단이나 부하 검증은 아니다. 상세 조건과 실패 시도는 [인증 오류 분류 검증](../problem-solving-records/32-sse-authentication-failure-during-deployment.md#실제-배포와-인증-서버-장애-복구)에 보존한다. Android 실기기는 검증하지 않았다.
- Jenkins #47 배포 중 SSE 401은 Keycloak 기동 완료 2초 전에 발생했다. 로컬에서는 키 조회 실패가 401로 바뀌는 결함을 재현했지만, 당시 예외와 탭 이동 경로가 없어 그 한 건의 원인까지 확정하지 않는다. 후속 시험에서 발견한 프록시의 옛 IP 사용은 별도 결함으로 [문제 해결 기록 33](../problem-solving-records/33-nginx-stale-container-address.md)에 기록했다. 주소 갱신 수정은 Jenkins #49로 배포해 실제 Backend IP 변경 후 API, SSE 자동 복구를 확인했다.

- Android 미전송 기록의 전송기는 5xx를 재시도 대상으로 분류한다. 위 웹 복구 결과를 Android 실기기의 재전송 성공으로 대신하지 않는다.

## 권한 검사와 실패 순서

- 앱의 현장 기록과 웹의 구역·차수 지휘를 구분하되, 마커 수정·삭제와 인수인계 메모는 양쪽 채널의 세부 권한을 확인한다. 내부 원천 반영은 일반 앱·웹의 권한과 분리한다. 옛 경계표의 웹 지원 배정 허용은 같은 문서의 mock 112 반영 전용 설명과 충돌하므로 새 API의 근거로 삼지 않는다.
- 표시용 `displayName`은 권한 근거가 아니다. 기존 요구에서는 기관 계정의 신원과 소속, 사건별 배정과 역할, 등록 업무폰을 구분했다. 2026-11-26 #61부터 서버에는 업무폰 등록 기록이 없다. 옛 경계 문서는 업무폰을 요청 헤더에 바인딩한다고 설명하지만 S1-2는 서명된 단말 값과의 일치를 요구했다. 신뢰 근거가 다른 두 설명을 같은 인증 정책으로 취급하지 않는다.
- 기존 요구는 채널·역할·해당 사건의 배정·등록 단말·필요한 근무/차수 맥락을 각각 확인하고, 여러 조건이 틀려도 합의한 우선 오류 하나를 반환하는 것이다. 사건 가져오기·종료의 역할 차이는 [사건 기록](./incident-lifecycle.md)에 남겼다.
- **현재 차이**: [공용 인터셉터](../../backend/src/main/java/com/surimap/common/auth/guard/GuardInterceptor.java)는 채널 → 역할 → 사건 접근 → 단말 순서로 검사한다. 역할 검사는 `MEMBER`뿐인지 판단하며, 사건 접근은 사건 ID 없이 별도 포트에 위임한다. 원문의 세밀한 역할표·실패 순서가 모든 API에 구현됐다고 보지 않는다.
- [FCM 등록 Controller](../../backend/src/main/java/com/surimap/app/controller/policephone/FcmTokenController.java)에서는 앱 채널과 업무폰 ID 형식이 검사된다. 헤더 값은 인증 객체의 값과 비교된다. 원문에 있던 배정 검사는 붙어 있지 않다. 같은 헤더에서 만든 인증 객체와 다시 헤더를 비교하는 것만으로 서명된 단말 결합을 검증할 수는 없다.
- **업무폰 기록 테이블 삭제 (2026-11-26 #61, Backend migration 테스트와 manifest API 테스트로 확인)**: 업무폰 인증은 MDM 같은 인프라 계층에서 맡는다([업무폰 인증 결정](../adr/0037-police-phone-authentication-at-infrastructure.md)). 그래서 작업자는 업무폰마다 두던 `police_phone` 테이블을 지웠다. `@RequirePolicePhone`과 각 Controller의 업무폰 ID 확인에서는 지금처럼 헤더 형식과 인증 객체의 값만 비교된다. 업무폰 등록 확인은 없다. 다른 테이블에 저장된 `police_phone_id` 값은 그대로 남는다.
  - 작업자는 쓰는 API가 없던 업무폰 배정 검사(`@RequirePolicePhoneAssigned`, `@RequireDeviceAssigned`)와 그 DB 구현을 지웠다. 이 검사에서만 나오던 `device_not_assigned` 오류 코드도 없어졌다. `police_phone_not_assigned`와 `police_phone_not_registered`는 다른 Service와 Controller에서 계속 응답된다.

## 통신 최신성과 알림 토큰

- 기존 요구는 낮거나 같은 heartbeat 순번으로 최신 상태를 되돌리지 않는 것, 마지막 서버 수신 시각으로 `ONLINE`·`STALE`·`LOST`를 계산하는 것, 앱의 `OFFLINE`·`RECOVERING`을 서버 저장 상태와 구분하는 것이다. 종료 사건의 상황판에는 남은 위치·최신성 표시를 숨겨야 한다.
- **계정 기준 통신 상태 (2026-11-20 #52, Backend API 테스트로 확인)**: heartbeat는 업무폰이 아니라 계정마다 하나씩 [heartbeat Service](../../backend/src/main/java/com/surimap/app/service/sync/AppSyncHeartbeatService.java)에서 저장된다. 저장하는 시각은 서버가 받은 시각이다. heartbeat 순번은 없앴다. 마지막 heartbeat 시각은 더 늦은 값만 남으므로, 늦게 도착한 요청이 최신 상태를 되돌리지 않는다. 조회할 때 60초와 5분 기준으로 [통신 상태](../../backend/src/main/java/com/surimap/domain/connectionstatus/ConnectionStatus.java)가 정해진다. 이 기준은 현재 구현과 옛 요구의 값이다. 부하 시험이나 현장 허용 지연으로 합의한 값은 아니다. 여러 사건에 배정된 계정의 heartbeat는 진행 중 사건마다 이벤트가 하나씩 남는다.
- **앱의 주기 heartbeat (2026-11-20 #53, Android JVM 테스트로 확인)**: 앱에서는 로그인해 있는 동안 30초마다 heartbeat를 보낸다. 30초는 정상 기준 60초의 절반이다. 앱의 Activity가 남아 있거나 수색 경로를 기록하는 [위치 서비스](../../android/app/src/main/java/com/surimap/feature/search/data/SearchPathLocationService.kt)가 동작하는 동안 [heartbeat 전송](../../android/app/src/main/java/com/surimap/core/sync/AccountHeartbeatScheduler.kt)이 이어진다. 화면이 꺼지거나 앱이 백그라운드로 가도 Activity가 남아 있으면 전송이 이어진다. Activity가 사라져도 수색 경로를 기록하는 중이면 전송이 멈추지 않는다. 전송이 실패해도 앱 사용은 막히지 않는다. 실패한 전송은 다음 주기에 다시 보낸다. 토큰을 받지 못한 주기에는 요청을 보내지 않는다. 토큰을 갱신하는 동안 로그아웃하거나 다른 계정으로 로그인했으면 갱신한 옛 세션을 저장하지 않는다. 이때는 그 주기의 요청도 보내지 않는다. 로그아웃하거나 세션이 만료되면 위치 서비스가 동작하고 있어도 전송이 멈춘다. 다시 로그인하면 남아 있는 Activity나 위치 서비스를 기준으로 전송이 다시 이어진다. 요청 본문의 `lastSyncAt`은 아직 보내지 않는다. 실제 업무폰에서 화면을 끈 채 전송 간격을 측정하지는 않았다.
- **트랜잭션**: 기록 저장과 이벤트 저장(`event_dispatch_job`)은 `AppSyncHeartbeatService.heartbeat`의 한 트랜잭션 안에서 일어난다. 이벤트 저장이 실패할 때 기록도 롤백되는지는 따로 테스트하지 않았다.
- **암호화 미충족**: 업무폰 Service의 `encryptToken()`은 `"cipher:" + token`을 반환한다. `token_ciphertext`라는 컬럼명과 달리 실제 암호화가 아니다. 원문의 암호화 저장·로그/API 비노출 요구를 보존하며, 키 관리와 전송 측 복호화를 함께 검토해야 한다.
- **계정 전환과 로그아웃 연결 해제 (2026-11-26 #59, Backend API 테스트와 Android JVM 테스트로 확인)**: 토큰을 다시 등록하면 같은 업무폰과 앱 설치의 이전 활성 토큰은 폐기되고 새 행이 저장된다. 앱에서는 업무폰 ID와 토큰이 같아도 로그인한 계정이 바뀌면 [토큰을 다시 등록](../../android/app/src/main/java/com/surimap/core/fcm/FcmRegistration.kt)한다. 그래서 같은 업무폰에서 계정 B로 다시 등록하면 계정 A의 배정 변경 알림은 그 업무폰으로 가지 않는다. 계정 B의 배정 변경 알림은 그 업무폰으로 간다.
- 앱에서 FCM 정리를 실행하는 경우는 두 가지다. 하나는 인증 화면에서 "다른 계정으로 로그인"을 누를 때다. 다른 하나는 세션이 만료돼 저장한 인증 정보를 지울 때다. 사용 중에 세션 갱신이 거절된 경우와 앱을 시작할 때 저장한 세션의 갱신이 거절된 경우가 여기에 든다. 두 경우 모두 앱에서는 [연결 해제 API](../api/api-spec.md#delete-apifcmtokens)를 요청한다. 요청이 성공하든 실패하든 취소되든 그다음 FCM 토큰과 앱에 남은 등록 기록을 지운다. 이 정리는 화면이 사라져도 앱 프로세스 범위에서 끝까지 실행된다. 다음 계정의 토큰 등록은 이전 정리가 끝난 뒤에 진행된다. 연결 해제 요청으로는 요청한 계정이 그 업무폰에 등록한 토큰만 폐기된다. 같은 업무폰에 다른 계정이 등록한 토큰은 그대로 남는다.
- 현재 앱에서 로그아웃하는 곳은 "다른 계정으로 로그인" 버튼뿐이다. 이 버튼은 세션이 만료돼 인증이 필요할 때만 보인다. 그래서 연결 해제 요청에는 만료된 access token이 실리거나 access token이 없다. 이 요청은 서버에서 보통 401로 거절된다. 이때 서버의 토큰은 다음 로그인 때 다시 등록될 때까지 이전 계정에 연결된 채로 남는다. 그 업무폰으로 이전 계정의 알림이 오지 않게 막는 것은 앱에서 FCM 토큰을 지우는 정리다. 정리 순서와 취소 처리는 Android JVM 테스트로 확인했다. 화면에서 정리를 시작하는 연결은 코드로만 확인했다. 실제 업무폰에서 FCM을 받는지와 로그아웃 뒤 FCM 토큰이 지워지는지는 확인하지 않았다.
- 통신이 끊겨 연결 해제 요청이 실패한 경우에도 서버의 토큰은 다음 로그인 때 다시 등록될 때까지 이전 계정에 연결된 채로 남는다. 이 한계는 #59에서 받아들였다. 마커 알림의 계정 전환은 #57에서 확인했다.
- **미구현 요구**: 로그인한 상태에서 로그아웃하는 버튼은 아직 없다. 이 버튼이 있어야 유효한 access token으로 연결 해제를 요청할 수 있다.

## 로그아웃한 사람의 offline token

현장 경찰관 여러 명이 업무폰 한 대를 각자 자기 계정으로 번갈아 쓴다. 로그아웃한 사람의 미전송 기록은 그 사람의 offline token으로 백그라운드에서 보낸다. 결정과 이유는 [ADR-0039](../adr/0039-logged-out-records-sent-with-offline-token.md)에 있다. 기록마다 어느 토큰으로 보내는지는 [오프라인 동기화 기능 정의 문서](./offline-sync.md#작성-계정별-토큰으로-전송)에 적었다.

- **offline token으로 로그인 (2026-11-27 #63, Android 소스 대조 테스트와 가짜 Keycloak 응답을 쓰는 Android JVM 테스트로 확인)**: 로그인할 때 [로그인 요청](../../android/app/src/main/java/com/surimap/feature/bootstrap/data/AndroidOidcLoginClient.kt)에 `offline_access` scope가 더해진다. 그래서 일반 refresh token 대신 offline token이 발급된다. 로그인해 있는 동안의 화면 세션도 이 offline token으로 갱신된다.
- 화면 세션은 지금처럼 [OidcSessionStateStore](../../android/app/src/main/java/com/surimap/core/auth/OidcSessionStateStore.kt)에 하나만 저장된다. 로그아웃한 계정의 offline token은 같은 파일의 `OfflineTokenStore`에 계정별로 저장된다. 두 저장소에서는 비교 후 저장과 비교 후 삭제가 같은 잠금 아래에서 실행된다. 그래서 늦게 도착한 갱신 응답이나 철회 응답으로 더 새로운 저장본이 덮어써지거나 지워지지 않는다.
- 화면 세션과 offline token은 같은 [Keycloak 요청 코드](../../android/app/src/main/java/com/surimap/core/auth/KeycloakTokenClient.kt)로 갱신된다. 갱신 요청은 AppAuth를 거치지 않고 token endpoint로 바로 간다. 이전에는 AppAuth의 갱신 함수를 썼다.
- 갱신 응답에 든 ID token은 앱에서 검증된다(2026-11-27 #63, 가짜 Keycloak 응답을 쓰는 Android JVM 테스트로 확인). 검증 기준은 AppAuth의 갱신 함수를 쓰던 이전 구현(fd134569)에서 실제로 실행되던 검증과 같다. 검증 조건은 다음과 같다.
  - `iss`, `sub`, `aud`, `exp`, `iat`가 모두 있어야 한다.
  - `aud`에 Android client ID가 있거나 `azp`가 Android client ID여야 한다.
  - 만료 시각(`exp`)이 지나지 않아야 한다.
  - 발급 시각(`iat`)과 업무폰 시각의 차이가 10분 이하여야 한다.
- issuer의 형식과 값은 검증 조건에 없다. 이전 구현에서는 Keycloak 주소가 discovery 문서 없이 설정돼 있었다. 그래서 AppAuth의 issuer 검사(https 주소, host, query와 fragment, discovery 문서의 issuer와 같은지)가 실행되지 않았다.
- 검증에 실패한 갱신 응답은 저장되지 않는다. 이 경우는 Keycloak의 갱신 거절과 같이 처리된다. 화면 세션이면 로그인 화면이 열린다. 로그아웃한 계정의 offline token이면 그 토큰이 업무폰에서 지워진다. 미전송 기록은 어느 경우에도 남는다.
- ID token의 서명은 이전 구현과 같이 검증되지 않는다. access token에서 읽는 값은 `accountId`와 `iat`뿐이다. access token의 서명은 지금처럼 Backend에서 검증된다.
- 앱 업데이트 전에 로그인한 화면 세션에는 offline token이 없다. 이 화면 세션은 앱을 켤 때 지워진다. 그래서 로그인 화면이 열린다. 앱을 다시 켜 저장된 수색 화면으로 바로 들어가는 경로에서도 로그인 화면이 열린다. 미전송 기록은 지워지지 않는다.

화면 세션을 지우는 곳은 세 군데다. 각 경우의 offline token 처리는 다음과 같다. 어느 경우에도 미전송 기록은 지워지지 않는다. 통신 오류로 갱신하지 못한 경우(`Unavailable`)에는 화면 세션이 유지된다.

| 화면 세션을 지우는 곳 | offline token 처리 |
|---|---|
| "다른 계정으로 로그인" 버튼 | 그 계정의 offline token을 계정별 저장소로 옮긴다. 그 계정의 미전송 기록이 모든 사건에서 0건이면 철회를 요청한다. |
| 사용 중 화면 세션 갱신이 거절됐을 때 | 지운다. 이 토큰은 이미 무효다. |
| 앱을 시작할 때 화면 세션 갱신이 거절됐을 때 | 지운다. 이 토큰은 이미 무효다. |

- **offline token 철회 (2026-11-27 #63, 가짜 Keycloak 응답을 쓰는 Android JVM 테스트로 확인)**: [철회 작업](../../android/app/src/main/java/com/surimap/core/auth/OfflineTokenRevocation.kt)의 철회 여부는 로그아웃한 계정마다 모든 사건의 미전송 기록 수로 정해진다. 미전송 기록에는 대기, `SENDING`, `FAILED_RETRYABLE`, `FAILED_FINAL` 기록이 든다. 미전송 기록이 0건인 계정의 offline token만 철회가 요청된다. 지금 로그인해 있는 계정의 offline token은 철회되지 않는다.
- 철회 요청은 realm의 `/protocol/openid-connect/revoke`로 간다. 본문에는 `client_id`, `token`, `token_type_hint=refresh_token`이 들어간다. Android client는 public client라서 client secret은 넣지 않는다.
- 철회 작업은 앱을 켤 때와 "다른 계정으로 로그인"을 누를 때, outbox를 보낸 뒤에 WorkManager로 예약된다. 로그아웃과 다음 사람의 로그인은 철회를 기다리지 않는다.

| Keycloak 응답 | 처리 |
|---|---|
| 성공(2xx) | 업무폰에서 offline token을 지운다. |
| 400 `invalid_token`, `invalid_grant` | 이미 무효인 토큰으로 보고 성공과 같이 지운다. |
| 통신 실패, 5xx | 토큰을 남긴다. 통신이 돌아오면 WorkManager 재시도로 다시 요청한다. |
| 그 밖의 거절 | 토큰을 남긴다. 다음에 앱을 켤 때 다시 요청한다. |

- 같은 계정이 다시 로그인하면 업무폰에 남은 그 계정의 이전 offline token이 지워진다. 그래서 아직 보내지 않은 철회가 취소된다. Keycloak에는 철회를 요청하지 않는다. 이미 보낸 철회 때문에 새 offline token이 갱신에서 거절되면 사용 중 갱신 거절과 같이 로그인 화면이 열린다. 철회 응답이 다시 로그인한 뒤에 도착해도 새 화면 세션은 지워지지 않는다.
- 마지막 갱신 뒤 30일이 지난 offline token은 철회 요청 없이 업무폰에서 지워진다. 마지막 갱신 시각은 저장된 access token의 발급 시각(`iat`)이다. 이 확인은 철회 작업에서 함께 실행된다. 그 계정의 미전송 기록은 지워지지 않는다.
- **Keycloak 설정 (2026-11-27 #63, 설정 파일의 diff만 확인)**: [realm 설정](../../infra/docker/keycloak/import/suri-map-realm.json)과 [Jenkins의 Keycloak 설정 단계](../../infra/Jenkinsfile)에 같은 값을 적었다. 실서버 realm은 이미 있어서 시작할 때 import를 건너뛰기 때문이다.
  - 모든 사용자에게 realm의 `offline_access` 역할을 준다. Jenkins 단계에는 기본 역할 `default-roles-suri-map`에 `offline_access`를 넣는 명령도 있다. 새로 만드는 사용자도 이 역할을 받게 하기 위해서다.
  - Android client에 `offline_access` 역할 scope와 optional client scope `offline_access`를 둔다. realm 설정에는 Android client의 default client scope와 optional client scope를 Keycloak 26 기본값 그대로 적었다. `offline_access`는 optional client scope 기본값에 들어 있다. Jenkins 단계에서는 기존 scope를 빼지 않고 `offline_access`만 더한다. 그래서 새로 import한 realm과 Jenkins 단계를 적용한 realm의 Android client scope가 같아야 한다.
  - realm에 `revokeRefreshToken=false`, `offlineSessionIdleTimeout=2592000`, `offlineSessionMaxLifespanEnabled=false`를 둔다.
  - Android client의 offline 세션 유휴 기간이 realm 값보다 짧으면 Jenkins 단계에서 배포가 멈춘다. 이 값이 비어 있거나 0이면 realm 값이 쓰인다.
- **MDM 전제로 생략한 조건**: 업무폰을 MDM으로 관리한다는 전제에서 토큰 교체, 토큰의 암호화 저장, offline token의 기간 제한을 두지 않았다. 이유와 위험은 [ADR-0039의 MDM 전제](../adr/0039-logged-out-records-sent-with-offline-token.md#mdm-전제로-생략한-보안-조건)에 있다.
- **로컬 Keycloak 확인 (2026-11-27 #63 검수, 로컬 Keycloak 26.6.1에서 kcadm과 curl로 확인)**: 새로 import한 realm과, 이전 realm 설정에 Jenkins 단계에서 #63에 더한 명령을 옮겨 두 번 실행한 realm에서 정책 값과 Android client scope 값이 같았다. 두 경우 모두 기존 사용자와 새로 만든 사용자가 offline token을 받았다. A가 로그아웃하고 B가 로그인한 뒤에도 A의 offline token으로 access token을 받을 수 있었다. A의 offline token을 철회한 뒤에는 A가 access token을 받지 못했다. B는 계속 받았다. 새로 import한 realm에서 같은 계정이 일반 세션으로 로그인하고 end-session 없이 offline 로그인을 두 번 했다. 그 뒤 첫 offline token을 철회하면 두 번째 offline token도 갱신에서 거절됐다. 이미 무효인 토큰을 철회하면 HTTP 200과 `invalid_token` 본문이 돌아왔다. 로그인은 업무폰 브라우저가 아니라 curl로 재현했다. A와 B는 쿠키 파일 하나를 같이 썼다. realm 설정에서 import한 사용자는 `lastName`이 비어 있어 로그인할 때 프로필 입력 화면이 먼저 열렸다. 이 확인에서는 로컬 사용자의 `lastName`을 채워 넘겼다.
- 앱 코드에서는 철회 응답이 2xx면 본문과 상관없이 철회 성공으로 처리된다. 그래서 위의 HTTP 200과 `invalid_token` 응답에도 업무폰의 토큰이 지워진다(2026-11-27 #63 검수, 검수자가 따로 쓴 JVM 테스트로 확인). 저장소의 JVM 테스트에서는 이미 무효인 토큰에 HTTP 400 `invalid_token`이 돌아오는 경우만 확인한다.
- **확인하지 않은 것**: 실기기에서 로그인, 로그아웃, 백그라운드 전송, 철회를 실행하지 않았다. 실서버 Keycloak의 Jenkins 단계 실행 결과는 develop에 병합한 뒤 배포할 때 확인한다. 실서버 realm에서 로그인할 때 프로필 입력 화면이 열리는지는 확인하지 않았다. 화면 콜백에서 세션 저장소를 부르는 연결은 코드로만 확인했다.

## 테스트 입력과 원문

- 계정 별칭, 역할, 통신 상태 입력은 [공용 fixture](../../test-fixtures/common-fixtures.json)의 `accountAliases`, `incidentSeed`, `boardAssembly`와 기존 Java/DB seed를 그대로 사용한다. fixture 통과를 실제 Keycloak 인증이나 업무폰 인증의 성공으로 해석하지 않는다.
- 추가 DB 왕복 2회 이하·상황판 p95 2초는 옛 목표이며 측정 결과가 아니다. 로그인·권한 거부·heartbeat·활성 세션 계측과 개인정보 없는 실패 기록의 구현 여부도 후속 확인 대상이다.
- 권한표·입력·실패 시나리오·미정 사항: [S1-2 원문 (`2c9515d7`)](https://github.com/sonic8-8/suri-map/blob/2c9515d7e0a3bd0c75fcbe6bca78e31a660159a1/docs/spec/specs/S1-2.json). 삭제 전 원문과 Git 파일의 바이트 일치를 확인했다. 이름 정리는 [후보 목록](../refactoring/codebase-naming-candidates.md)에서 별도로 다룬다.
