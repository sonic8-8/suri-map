# 제목

[BE/Infra] 공개 웹 주소에서 보낸 브라우저 쓰기 요청이 CORS 검사에서 거부되는 현상

# 본문

## 문제 배경

수리맵 상황판은 `https://suri-map.sonic8-8.com`에서 같은 주소의 `/api`로 요청을 보냅니다. 브라우저의 출처는 프로토콜·호스트·포트의 조합입니다. 같은 출처의 요청이라면 다른 출처의 접근을 제한하는 CORS 검사 때문에 거부돼서는 안 됩니다. 로그인·채널·업무 권한은 그와 별도로 검사해야 합니다.

2026-09-28 `e2399225`의 Jenkins #36 배포 후 SSE 순서를 검증하던 중, 로그인한 브라우저의 `POST /api/markers`가 `Invalid CORS request`로 거부됐습니다. 마커와 전송 작업은 저장되지 않았습니다. 이 문제가 WEB 요청에도 적용된다면 상황판의 저장·수정도 막힙니다.

다만 최초 요청은 `X-Client-Channel: APP`을 사용한 진단 요청이었습니다. 브라우저 출처를 보내지 않는 앱 채널 HTTP 요청은 201로 성공했습니다. 이 결과만으로 실제 WEB 명령까지 실패한다고 확정하지 않고, [SSE 발행 순서 문제](8-marker-events-dispatched-out-of-publication-order.md)와 분리해 조사했습니다.

### 문제 해결: 체크리스트

- [x] 배포 환경에서 같은 공개 HTTPS 출처의 요청이 CORS 거부 없이 인증·업무 검사를 받는가?
- [x] 로그인한 상황판에서 위치를 보정하면 실제 좌표가 저장되는가?
- [x] 다른 상황판 탭이 SSE를 받고 수동 새로고침 없이 새 좌표로 지도를 갱신하는가?
- [x] 검사한 미허용 출처·위조 전달 헤더와 비인증 쓰기 요청은 계속 거부되는가?

## 원인 분석과 선택지

최초 가설은 CORS 허용 출처 누락이었습니다. 당시 [설정](../../../backend/src/main/resources/application.yml)에는 로컬 주소와 과거 `https://k14c106.p.ssafy.io`만 있었고, 실행 환경의 `SURI_MAP_CORS_ALLOWED_ORIGIN_PATTERNS`도 미설정이었습니다. [SecurityConfig](../../../backend/src/main/java/com/surimap/config/SecurityConfig.java)는 이 설정을 `/api/**`에 적용했습니다.

그러나 원래 같은 출처인 요청이 왜 허용 목록을 필요로 하는지도 설명해야 했습니다. 주소만 목록에 추가하기 전에, [Frontend nginx](../../../frontend/nginx.conf)와 Backend가 외부 HTTPS 요청 정보를 어떻게 전달·인식하는지 확인하기로 했습니다. 모든 출처 허용이나 CORS 검사 해제는 해결책으로 사용하지 않습니다.

아래 OPTIONS 요청은 인증 정보나 데이터 입력 없이도 403과 `Invalid CORS request`를 반환했습니다. 실제 WEB 명령의 실패 여부는 별도로 확인했습니다.

```bash
curl --silent --show-error --max-time 20 --request OPTIONS \
  --header 'Origin: https://suri-map.sonic8-8.com' \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: authorization,content-type,x-client-channel,x-policephone-id,idempotency-key' \
  --write-out '\nhttp=%{http_code}\n' \
  https://suri-map.sonic8-8.com/api/markers
```

20초는 명령의 대기 한도이지 서비스 응답 시간 목표가 아닙니다. [최초 브라우저 거부 로그](../../../_workspace/sse-order-verify-20260928.a9Jz2s/browser-cors-attempt.log)는 로컬에 보존했습니다.

# 댓글

## 변경 내용과 트레이드오프

- 변경: 프록시가 외부 HTTPS 정보를 보존하고 Backend가 이를 반영해 같은 출처를 정확히 판정하도록 수정했습니다.
- 유지: CORS 허용 목록·인증·채널 권한은 유지했습니다. 현재 도메인을 허용 목록에 추가하거나 검사를 끄지 않았습니다.
- 운영 조건: 호스트 nginx가 외부 요청의 전달 헤더를 덮어쓰고, 내부 Frontend·Backend 포트는 외부에 직접 노출되지 않아야 합니다.

### 추가 진단: WEB 수정·삭제 요청도 CORS에서 거부

2026-09-28 실제 Keycloak 로그인 후 브라우저에서 WEB `PATCH`·`DELETE /api/markers/{markerId}`를 보냈습니다. [웹 API 호출부](../../../frontend/src/features/marker/api/markerCommandApi.ts)와 같은 인증·채널·멱등성 헤더를 사용했습니다.

대상은 기존 시험용 기준 마커 `55555555-5555-5555-5555-555555550001`입니다. 데이터를 바꾸지 않으려고 실제 버전 1 대신 `2147483647`을 보냈습니다. CORS를 통과하더라도 버전 불일치로 `409 write_conflict`가 나와야 하는 조건입니다.

| 요청 | 결과 | 확인한 범위 |
|---|---|---|
| 로그인한 브라우저의 상황판 GET | 200 | 같은 계정의 조회는 성공 |
| 브라우저의 WEB PATCH·DELETE | 모두 403, `Invalid CORS request` | 수정·삭제 모두 CORS 단계에서 거부 |
| 동일한 HTTP 요청에 현재 HTTPS 출처 지정 | 모두 403, `Invalid CORS request` | 브라우저 밖에서도 같은 거부 재현 |
| 출처만 기존 허용 주소로 변경 | 모두 409, `write_conflict` | CORS를 통과해 실제 서버 버전 검사에 도달 |
| 출처 헤더만 생략 | 모두 409, `write_conflict` | 요청 방식·로그인·WEB 채널 자체가 CORS 거부 원인은 아님 |

PATCH·DELETE의 OPTIONS도 현재 출처에서는 403, 기존 허용 출처에서는 200이었습니다. 409는 저장 성공이 아니라 데이터 변경을 막는 의도된 거부입니다. 출처 변경·생략은 원인 비교용 HTTP 검사이며 브라우저 보안을 우회하는 해결책이 아닙니다.

진단 전후 마커 9개·전송 작업 608개·대상 버전 1과 전체 마커 내용 해시가 같았습니다. [재현 스크립트](../../../_workspace/cors-web-verify-20260928.oUuoni/verify-browser-cors.cjs)는 당시 CORS 거부 때문에 종료 코드 1로 실패했습니다. 인증 정보·좌표 원문 없이 남긴 [진단 로그](../../../_workspace/cors-web-verify-20260928.oUuoni/browser.log)와 함께 보존했습니다.

### 확인한 원인: 외부 HTTPS 요청을 내부 HTTP 요청으로 판정

요청은 호스트 nginx에서 HTTPS를 처리한 뒤 Frontend nginx와 Backend로 전달됩니다. 각 단계의 설정과 실제 응답을 대조해 두 문제를 확인했습니다.

1. 호스트 nginx가 `https`·`443`을 전달해도 Frontend nginx가 내부 통신 값인 `http`·`80`으로 덮어썼습니다. 저장소와 실행 컨테이너 설정이 같았습니다.
2. Backend에는 전달 헤더를 요청 정보에 반영하는 설정이 없었습니다. 올바른 HTTPS 전달 헤더를 Backend에 직접 넣어도 현재 HTTPS 출처는 403이었습니다.

공개 `/api/health`도 같은 호스트의 `https://` 출처는 403, `http://` 출처는 200이었습니다. Spring Web 6.2.18의 `CorsUtils`·`DefaultCorsProcessor`와 대조하면 외부 HTTPS와 내부 HTTP를 다른 출처로 판단하는 흐름에 부합합니다. 요청 객체에 진단 로그를 넣어 직접 관측한 것은 아닙니다.

따라서 허용 목록에 현재 주소를 넣는 것만으로는 잘못된 요청 주소 인식을 고치지 못합니다. [Spring Boot의 프록시 설정](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)을 사용하되, [Spring의 신뢰 경계 지침](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html#filters-forwarded-headers)에 맞춰 외부 클라이언트가 임의로 넣은 전달 헤더는 신뢰하지 않도록 했습니다.

### 적용한 수정: 프록시에서 보존하고 Tomcat에서 반영

Frontend nginx의 `/api/`와 같은 Backend로 연결되는 `/tiles/`에서 외부 프로토콜·포트를 보존하도록 수정했습니다. 전달 헤더가 없는 직접 HTTP 호출은 기존 내부 주소를 사용하고, 외부의 별도 `Forwarded` 헤더는 Backend로 넘기지 않습니다.

Backend에는 `server.forward-headers-strategy: native`를 설정해 Tomcat의 기본 기능을 사용했습니다. 새 필터나 라이브러리는 추가하지 않았습니다. [Tomcat의 기본 내부 프록시 범위](https://tomcat.apache.org/tomcat-10.1-doc/config/valve.html#Remote_IP_Valve)는 사설·루프백 주소이며 특정 nginx 한 대로 제한한 설정은 아닙니다. 따라서 호스트의 헤더 덮어쓰기와 내부 포트 비공개 조건을 함께 확인했습니다.

## 검증 결과

### 로컬 회귀 검사: 프록시·서버를 함께 수정한 뒤 통과

수정 전 실제 Tomcat 회귀 테스트는 7개 중 HTTPS 관련 5개가 403으로 실패했습니다. Backend만 바꿔도 이전 Frontend 설정을 거치면 403이었습니다. 두 설정을 함께 수정한 뒤 아래 검증이 통과했습니다.

| 검증 | 결과 |
|---|---|
| 실제 Tomcat 회귀 테스트 7개 + 기존 보안 테스트 21개 | 28개 통과. 같은 HTTPS 출처는 허용 목록 없이 200, 인증 없는 POST·PATCH·DELETE는 CORS가 아닌 401 인증 오류 |
| 로컬 TLS nginx → Frontend nginx → 수정한 Backend JAR | 11개 통과. 같은 출처·OPTIONS·직접 HTTP 통과, 미허용 출처·`null` 출처·다른 프로토콜/포트·위조 전달 헤더 거부 |
| Backend 전체 테스트 | 223개 클래스·1,305개 통과, 실패·오류·건너뜀 0. 실제 PostgreSQL/PostGIS 사용, 5분 12초. 기본 설정의 `performance` 태그 제외 |
| Frontend | nginx 설정 테스트 5개·`npm run typecheck`·`npm run build` 통과. 빌드의 기존 500 kB 초과 청크 경고는 남음 |
| 수정한 nginx 실행 설정 | `nginx -t` 통과 |

Backend는 Docker의 `gradle:8.14.3-jdk17`에서 `./gradlew --offline --no-daemon spotlessJavaApply test -PspotlessIdeHook=/workspace/backend/src/test/java/com/surimap/config/TomcatProxyTest.java --console=plain`으로 검증했습니다. 새 테스트 파일만 포맷했습니다. WSL의 Java 루프백 소켓 연결 실패는 통과로 계산하지 않고 Docker에서 실제 HTTP·DB 검사를 수행했습니다.

[프록시 검사 스크립트](../../../_workspace/cors-fix-20260928.heWyQO/verify-local-proxy.cjs)와 [결과](../../../_workspace/cors-fix-20260928.heWyQO/proxy-check.log)는 로컬 전용입니다. 격리 컨테이너를 재구성해야 하며 실제 로그인·마커 저장·화면 갱신 시험과 구분합니다.

### 배포 후 화면 검사: 위치 보정과 다른 탭의 지도 갱신 성공

2026-09-28 수정분 6개 파일을 [`070dfc90`](https://github.com/sonic8-8/suri-map/commit/070dfc90f4f82be4773d573617c787e542831f66)으로 커밋·푸시했고, Jenkins #37이 같은 커밋의 배포에 성공했습니다(4분 34초). 미커밋 FCM·관측 설정은 제외했습니다. 실행 Frontend 설정의 SHA-256이 저장소와 같았고 `nginx -t`도 통과했습니다. 호스트의 헤더 덮어쓰기와 Frontend `127.0.0.1:18081`·Backend `127.0.0.1:18091` 바인딩을 확인했습니다.

기존 브라우저 재현을 먼저 반복한 뒤 [상황판의 위치 보정](../../../frontend/src/features/situationBoard/presentation/components/map/SearchMapCanvas.tsx)을 실제로 조작했습니다. 삭제는 API 함수·hook만 확인됐고 화면 호출부는 찾지 못했으므로, 아래 DELETE 검사를 삭제 완료 시험으로 해석하지 않습니다.

| 실제 요청·화면 | 결과 |
|---|---|
| 기존 브라우저 재현 명령 | 통과. 로그인한 WEB PATCH·DELETE가 CORS 403 대신 의도한 `409 write_conflict`에 도달. 이 검사에서는 데이터 변경 없음 |
| 공개 HTTPS 출처·미허용 출처·위조 전달 헤더·비인증 쓰기 8조건 | 통과. 같은 출처 200, 미허용·`null`·다른 포트·위조 출처 403, 인증 없는 POST·PATCH·DELETE 401 |
| 화면에서 기준 마커 선택 → 위치 보정 → 지도 드래그 → 저장 | PATCH 200·`저장 완료` 표시. DB에 저장된 좌표가 실제로 달라졌는지 소수점 6자리 반올림 후에도 확인 |
| 조작하지 않은 다른 상황판 탭 | `MARKER_UPDATED` 순번 23·버전 8 수신 → 자동 재조회 → DB와 같은 새 좌표 수신 → 지도 마커 위치 변경 확인. 수동 새로고침하지 않음 |
| 원래 좌표 복원 | 정상 WEB API로 복원. 순번 24·버전 9의 SSE 수신과 자동 재조회도 확인 |

같은 출처 판정·위치 저장·다른 탭 갱신·거부 조건 보존을 확인해 위 해결 기준을 충족했습니다. 최종 브라우저 실행의 타일 HTTP 실패·JavaScript 오류와 Backend ERROR는 0건입니다. 다만 닫힌 SSE 연결을 다음 전송 때 제거하는 WARN은 24건 남았습니다. [기존 연결 정리 이슈](5-sse-initial-send-disconnect-cleanup.md)는 별도이며 이번 CORS 완료에 포함하지 않습니다.

### 검증 이력: 도구 실패와 실제 데이터 변경을 구분

초기 도구 실행은 뒤쪽 탭의 지도 로딩·화면 렌더링 대기를 빠뜨리거나, 지도 위에 겹친 왼쪽 패널을 드래그해 실패했습니다. 저장 요청이 성공해도 반올림한 좌표는 그대로였으므로 위치 변경 성공으로 계산하지 않았습니다. 이후 `elementFromPoint`로 노출된 지도에 입력되는지 확인하고, 실제 좌표 변경과 다른 탭의 마커 이동·원래 좌표 복원을 검증했습니다. 이 과정에서 제품 코드는 추가로 바꾸지 않았습니다.

마커는 9개를 유지했고 대상의 원래 좌표·메모와 나머지 마커는 보존했습니다. 다만 정상 수정 API를 거쳤으므로 대상 버전은 1→9, 상태는 `ACTIVE`→`UPDATED`로 바뀌었고 수정 시각도 남았습니다. 같은 좌표 저장·복원 6개와 최종 위치 보정·복원 2개로 전송 작업이 608→616개가 됐으며, 새 순번 17–24는 모두 `COMPLETED`입니다. 기존 작업 608개의 내용 해시는 같고 migration은 41개를 유지했습니다. 이력을 삭제하거나 DB를 직접 되돌리지는 않았습니다.

배포 전 백업 `/srv/apps/suri-map/backups/suri-map-before-load-20260927T213452Z.dump`는 `pg_restore --list`만 확인했으며 실제 복원은 시험하지 않았습니다. [배포·실행 명령·로그·저장 화면과 다른 탭의 갱신 화면](../../../_workspace/cors-deploy-20260928.N6c4HK/RESULTS.md)은 로컬 전용 자료입니다. 최종 성공 로그는 `position-correction-verified.log`이며 앞선 실패 로그도 보존했습니다. `verify-position-correction.cjs`를 다시 실행하면 같은 마커를 수정·복원하고 이력을 추가하므로 읽기 전용 검사가 아닙니다.

이번 CORS 문제의 배포·화면 검증은 마쳤습니다. 과거 출처 허용 목록 정리, 실제 삭제 완료·Android·FCM·부하, DB 이력 재전송·서버 재시작 복구는 별도 범위입니다. 배포 후 기록은 아직 추가 커밋하지 않았으며 이 이슈를 GitHub에 등록하지도 않았습니다.
