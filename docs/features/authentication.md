# 인증·권한과 업무폰 상태

로그인·채널·사건 접근·업무폰 상태를 바꿀 때 확인할 기록이다. 옛 S1-2 요구와 2026-09-21 정적 코드 대조 결과를 구분한다. 미구현 요구의 유효성과 범위는 후속 작업에서 합의하며, 이번 정리는 보안 검증 완료를 뜻하지 않는다.

## 계정과 인증 정보

- 제품의 계정·업무폰 구분과 개인·공유 계정 미정 사항은 [PRD](../prd.md#사건상황판)를 따른다. 옛 팀·순찰차 계정 seed를 현재 제품 정책으로 자동 채택하지 않는다. 계정 발급·중복 로그인 정책·실제 업무폰의 GMS/FCM 수신 가능성은 확인 대상이다.
- 기존 요구는 Keycloak 로그인, 서버의 JWT 검증, 앱의 서명된 업무폰 ID와 요청 헤더 일치, 일반 클라이언트의 `INTERNAL` 권한 획득 차단이다. 화면의 버튼 숨김과 서버 권한 검사는 별개다.
- **현재 차이**: [JWT 변환기](../../backend/src/main/java/com/surimap/account/security/KeycloakJwtAuthenticationConverter.java)는 계정 종류·소속을 JWT에서 읽지만 채널·업무폰 ID는 요청 헤더에서 가져온다. 역할도 두 계정 속성에서 파생한다. `INTERNAL` enum도 파싱하므로 JWT와 단말·채널의 결합, 계정 비활성화 반영, 내부 API 보호를 각 호출부와 함께 확인해야 한다.
- [보안 설정](../../backend/src/main/java/com/surimap/config/SecurityConfig.java)과 [인증 필터](../../backend/src/main/java/com/surimap/account/security/OidcBearerAuthenticationFilter.java)는 JWT decoder와 인증 객체를 사용한다. 서명 검증이 있다는 사실만으로 위 단말·사건별 권한 요구까지 충족됐다고 판단하지 않는다. SSE 요청의 인증 보관 수정은 [기존 문제 기록](../issues/local/3-authenticated-sse-access-denied-on-disconnect.md)을 참조한다.

## HTTPS 프록시와 브라우저 출처

- 같은 공개 웹 주소의 요청은 내부 HTTP 통신과 혼동하지 않고 같은 출처로 판단해야 한다. [Frontend nginx](../../frontend/nginx.conf)는 호스트 nginx가 확인한 외부 프로토콜·포트를 Backend로 전달하고, [Backend 설정](../../backend/src/main/resources/application.yml)의 `server.forward-headers-strategy: native`는 Tomcat이 이 정보로 요청 주소를 판정하도록 한다.
- 이 설정은 외부 호스트 nginx가 전달 헤더를 덮어쓰고 Frontend·Backend 포트를 외부에 직접 노출하지 않는 구성을 전제로 한다. Tomcat 기본값은 사설·루프백 프록시를 신뢰하며 특정 nginx 한 대만 제한한 설정은 아니다. Frontend는 별도 `Forwarded` 헤더를 제거한다. CORS 허용 목록·JWT·채널 권한은 이번 수정에서 바꾸지 않았다.
- 2026-09-28 로컬 프록시·실제 Tomcat 검증에서 같은 HTTPS 출처의 통과와 다른 출처의 거부를 확인했다. 실제 Hetzner 배포와 화면 저장·후속 갱신은 아직 검증하지 않았다. 원인·실행 결과·남은 확인은 [CORS 문제 기록](../issues/local/9-browser-post-rejected-by-cors.md)을 따른다.

## 권한 검사와 실패 순서

- 앱의 현장 기록과 웹의 구역·차수 지휘를 구분하되, 마커 수정·삭제와 인수인계 메모는 양쪽 채널의 세부 권한을 확인한다. 내부 원천 반영은 일반 앱·웹의 권한과 분리한다. 옛 경계표의 웹 지원 배정 허용은 같은 문서의 mock 112 반영 전용 설명과 충돌하므로 새 API의 근거로 삼지 않는다.
- 표시용 `displayName`은 권한 근거가 아니다. 기관 계정의 신원·소속, 사건별 배정·역할, 등록 업무폰을 구분한다. 옛 경계 문서는 업무폰을 요청 헤더에 바인딩한다고 설명하지만 S1-2는 서명된 단말 값과의 일치를 요구했다. 신뢰 근거가 다른 두 설명을 같은 인증 정책으로 취급하지 않는다.
- 기존 요구는 채널·역할·해당 사건의 배정·등록 단말·필요한 근무/차수 맥락을 각각 확인하고, 여러 조건이 틀려도 합의한 우선 오류 하나를 반환하는 것이다. 사건 가져오기·종료의 역할 차이는 [사건 기록](./incident-lifecycle.md)에 남겼다.
- **현재 차이**: [공용 인터셉터](../../backend/src/main/java/com/surimap/common/auth/guard/GuardInterceptor.java)는 채널 → 역할 → 사건 접근 → 단말 순서로 검사한다. 역할 검사는 `MEMBER`뿐인지 판단하며, 사건 접근은 사건 ID 없이 별도 포트에 위임한다. 원문의 세밀한 역할표·실패 순서가 모든 API에 구현됐다고 보지 않는다.
- [FCM 등록 Controller](../../backend/src/main/java/com/surimap/app/controller/policephone/FcmTokenController.java)는 앱 채널·단말 존재·등록을 검사하지만 원문에 있던 배정 검사는 붙어 있지 않다. 같은 헤더에서 만든 인증 객체와 다시 헤더를 비교하는 것만으로 서명된 단말 결합을 검증할 수는 없다.

## 통신 최신성과 알림 토큰

- 기존 요구는 낮거나 같은 heartbeat 순번으로 최신 상태를 되돌리지 않는 것, 마지막 서버 수신 시각으로 `ONLINE`·`STALE`·`LOST`를 계산하는 것, 앱의 `OFFLINE`·`RECOVERING`을 서버 저장 상태와 구분하는 것이다. 종료 사건의 상황판에는 남은 위치·최신성 표시를 숨겨야 한다.
- [업무폰 Service](../../backend/src/main/java/com/surimap/policephone/PolicePhonePersistenceService.java)는 서버 수신 시각을 저장하고 조회 때 60초·5분 기준을 적용한다. 이는 현재 구현·옛 요구의 값이며, 부하 시험이나 현장 허용 지연의 합의값은 아니다. 여러 사건 배정과 동시 heartbeat의 순번·이벤트 일치는 별도로 확인한다.
- **트랜잭션 확인 대상**: [heartbeat Service](../../backend/src/main/java/com/surimap/app/service/policephone/AppPolicePhoneHeartbeatService.java)는 저장 Service 호출이 끝난 뒤 이벤트를 발행한다. 저장과 발행이 항상 같은 트랜잭션이라는 기존 요구를 메서드 이름만으로 보장할 수 없다.
- **암호화 미충족**: 업무폰 Service의 `encryptToken()`은 `"cipher:" + token`을 반환한다. `token_ciphertext`라는 컬럼명과 달리 실제 암호화가 아니다. 원문의 암호화 저장·로그/API 비노출 요구를 보존하며, 키 관리와 전송 측 복호화를 함께 검토해야 한다.
- 토큰 재등록은 이전 활성 토큰을 폐기하고 새 행을 저장한다. 로그아웃 폐기 메서드는 있으나 운영 코드의 호출 연결은 이번 검색에서 찾지 못했다. 실제 로그아웃·동시 등록·FCM 수신과 실패 복구는 별도 검증 대상이다.

## 테스트 입력과 원문

- 계정 별칭·역할·업무폰 최신성 입력은 [공용 fixture](../../test-fixtures/common-fixtures.json)의 `accountAliases`·`incidentSeed`·`boardAssembly`와 기존 Java/DB seed를 그대로 사용한다. fixture 통과를 실제 Keycloak·단말 인증 성공으로 해석하지 않는다.
- 추가 DB 왕복 2회 이하·상황판 p95 2초는 옛 목표이며 측정 결과가 아니다. 로그인·권한 거부·heartbeat·활성 세션 계측과 개인정보 없는 실패 기록의 구현 여부도 후속 확인 대상이다.
- 권한표·입력·실패 시나리오·미정 사항: [S1-2 원문 (`5c801c3a`)](https://github.com/sonic8-8/suri-map/blob/5c801c3a5fb3879cbd39430ff16493653a8c55da/docs/spec/specs/S1-2.json). 삭제 전 원문과 Git 파일의 바이트 일치를 확인했다. 이름 정리는 [후보 목록](../refactoring/codebase-naming-candidates.md)에서 별도로 다룬다.
