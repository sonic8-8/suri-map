# 제목

[BE/Infra] 공개 웹 주소에서 보낸 브라우저 쓰기 요청이 CORS 검사에서 거부되는 현상

# 본문

## 문제 배경

2026-09-28, `e2399225`의 Jenkins #36 배포 후 SSE 순서를 검증하던 중 발견했습니다. `https://suri-map.sonic8-8.com`에서 로그인한 브라우저가 같은 주소의 `/api/markers`로 POST하면 `Invalid CORS request`를 받았습니다. 이 시도에서는 마커와 전송 작업이 저장되지 않았습니다.

당시 검증 요청은 `X-Client-Channel: APP`을 사용했습니다. 브라우저 출처를 보내지 않는 앱 채널 HTTP 요청은 이후 201로 성공했습니다. 이 결과만으로 웹 명령의 실패까지 확정하지 않고, SSE 발행 순서 수정과 분리해 아래 추가 진단을 진행했습니다.

### 문제 해결: 체크리스트

- [x] 브라우저 POST 거부와 데이터 미저장을 확인했는가?
- [x] 데이터를 쓰지 않는 OPTIONS 요청으로 HTTP 403을 재현했는가?
- [x] 공개 웹 주소와 CORS 설정·실행 환경 변수를 대조했는가?
- [x] 로그인한 브라우저의 WEB 수정·삭제 요청에서도 같은 문제가 발생하는지 확인했는가?
- [x] 배포 프록시의 전달 설정과 서버의 출처 판정 결과를 대조했는가?
- [x] 같은 출처를 정확히 판정하도록 수정하고 로컬에서 허용·거부 조건을 함께 검증했는가?
- [ ] 수정 후 실제 화면의 위치 보정·저장과 후속 갱신까지 확인했는가?

## 확인한 현상

아래 읽기 전용 사전 요청은 HTTP 403과 `Invalid CORS request`를 반환했습니다. 인증 정보나 실제 마커 입력 없이 재현할 수 있습니다.

```bash
curl --silent --show-error --max-time 20 --request OPTIONS \
  --header 'Origin: https://suri-map.sonic8-8.com' \
  --header 'Access-Control-Request-Method: POST' \
  --header 'Access-Control-Request-Headers: authorization,content-type,x-client-channel,x-policephone-id,idempotency-key' \
  --write-out '\nhttp=%{http_code}\n' \
  https://suri-map.sonic8-8.com/api/markers
```

20초는 이 진단 명령의 대기 한도이며 서비스의 응답 시간 목표가 아닙니다. OPTIONS 실패 자체를 모든 실제 웹 동작의 실패로 확대하지 않습니다.

## 최초 원인 가설

[SecurityConfig](../../../backend/src/main/java/com/surimap/config/SecurityConfig.java)는 `/api/**`에 공통 CORS 설정을 적용합니다. 배포 커밋의 [기본 허용 목록](../../../backend/src/main/resources/application.yml)에는 로컬 주소와 과거 `https://k14c106.p.ssafy.io`만 있고 현재 공개 주소는 없습니다. 실행 컨테이너의 `SURI_MAP_CORS_ALLOWED_ORIGIN_PATTERNS`도 미설정이었습니다.

허용 출처 누락이 유력하지만, 같은 공개 주소로 보낸 요청이 서버에서는 다른 출처로 판단되는 프록시 경로도 확인해야 합니다. [프런트 nginx](../../../frontend/nginx.conf)의 전달 헤더와 실제 호스트 설정, 서버의 forwarded-header 처리를 함께 대조할 대상입니다. 모든 출처를 허용하거나 CORS 검사를 끄는 방식으로 해결하지 않습니다.

최초 검증 로그는 로컬 `_workspace/sse-order-verify-20260928.a9Jz2s/browser-cors-attempt.log`에 보관했습니다. 별도 [SSE 순서 검증](8-marker-events-dispatched-out-of-publication-order.md)은 정상적인 비브라우저 앱 요청으로 4조건 모두 통과했습니다.

## 추가 진단: WEB 수정·삭제도 CORS에서 거부됩니다

2026-09-28, 같은 배포에서 실제 Keycloak 로그인 후 브라우저 `fetch`로 WEB `PATCH`·`DELETE /api/markers/{markerId}`를 보냈습니다. [웹 API 호출부](../../../frontend/src/features/marker/api/markerCommandApi.ts)와 같은 인증·채널·멱등성 헤더를 사용했습니다.

대상은 기존 `MOCK_SEED` 기준 마커 `55555555-5555-5555-5555-555555550001`입니다. 데이터 변경을 막으려고 실제 버전 1 대신 일치하지 않는 버전 `2147483647`을 보냈습니다. CORS를 통과하면 정상 저장이 아니라 `409 write_conflict`가 나와야 합니다.

| 요청 | 결과 | 확인한 범위 |
|---|---|---|
| 로그인한 브라우저의 상황판 GET | 200 | 같은 계정의 조회는 성공 |
| 브라우저의 WEB PATCH·DELETE | 모두 403, `Invalid CORS request` | 수정·삭제 모두 CORS 단계에서 거부 |
| 동일한 HTTP 요청에 현재 HTTPS 출처 지정 | 모두 403, `Invalid CORS request` | 브라우저 밖에서도 같은 거부 재현 |
| 출처만 기존 허용 주소로 변경 | 모두 409, `write_conflict` | CORS를 통과해 실제 서버 버전 검사에 도달 |
| 출처 헤더만 생략 | 모두 409, `write_conflict` | 요청 방식·로그인·WEB 채널 자체가 CORS 거부 원인은 아님 |

PATCH·DELETE의 OPTIONS도 현재 출처에서는 403, 기존 허용 출처에서는 200이었습니다. **409는 수정 성공이 아니라 데이터를 보존하기 위한 의도된 거부입니다.** 출처 변경·생략은 원인 비교용 HTTP 검사이며 브라우저 보안을 우회하는 해결책이 아닙니다.

검증 전후 마커 9개·전송 작업 608개, 기준 마커 버전 1이 유지됐습니다. 전체 마커 행의 내용 해시도 `eaa5466977f333ee31b0d4a883ef9eec`로 같았습니다. 마커를 생성·수정·삭제하지 않았습니다.

## 확인한 원인: HTTPS 정보가 보존·반영되지 않습니다

1. 실제 호스트 nginx는 HTTPS를 처리하고 Frontend에 `X-Forwarded-Proto: https`, `X-Forwarded-Port: 443`을 전달하도록 설정돼 있습니다.
2. Frontend nginx의 `/api/`는 이 값을 보존하지 않고 내부 통신의 `$scheme`·`$server_port`, 즉 `http`·`80`으로 덮어씁니다. [저장소 설정](../../../frontend/nginx.conf)과 실행 컨테이너의 설정이 같습니다.
3. Backend의 전달 헤더 설정은 소스에 없고 `SERVER_FORWARD_HEADERS_STRATEGY`도 미설정입니다. 서버 내부에서 Backend에 올바른 HTTPS 전달 헤더를 직접 넣어도 현재 HTTPS 출처는 403이었습니다. 원래 HTTPS 요청 정보를 CORS 판정에 반영하지 않는 결과입니다.
4. 공개 `/api/health`에 출처만 `http://suri-map.sonic8-8.com`으로 바꾸면 200입니다. 같은 도메인의 `https://`는 403이고, 허용 목록의 과거 HTTPS 주소는 200입니다. 로컬 Spring Web 6.2.18의 `CorsUtils`·`DefaultCorsProcessor`와 대조하면 내부 HTTP 요청과 브라우저의 HTTPS 출처를 서로 다르게 판단하는 흐름에 부합합니다. 요청 객체에 진단 로그를 넣어 직접 관측한 것은 아닙니다.

즉, 같은 웹 주소에서 보낸 요청도 Backend에서는 다른 출처로 분류되고, 현재 HTTPS 주소가 허용 목록에도 없어 거부됩니다. 별도 미허용 출처 `https://untrusted.invalid`도 403으로 거부됐습니다. 이 검사를 전체 보안 감사로 확대하지 않습니다.

[Spring Boot의 프록시 설정 안내](https://docs.spring.io/spring-boot/3.5/how-to/webserver.html#howto.webserver.use-behind-a-proxy-server)는 전달 헤더를 실제 요청 정보로 반영하는 설정을 설명합니다. 적용할 때는 [Spring의 신뢰 경계 지침](https://docs.spring.io/spring-framework/reference/6.2/web/webmvc/filters.html#filters-forwarded-headers)에 따라 외부 클라이언트가 넣은 전달 헤더와 신뢰할 프록시의 값을 구분해야 합니다.

## 진단 당시 정한 수정·검증 범위

- 프록시에서 외부 HTTPS 정보를 보존하고 Backend가 신뢰할 프록시의 정보만 반영하도록 수정합니다. 현재 주소를 허용 목록에 추가하는 것만으로 요청 주소 인식 문제까지 해결됐다고 보지 않습니다.
- 과거 주소·로컬 주소를 운영 CORS 허용 목록에 유지할지는 실제 교차 출처 호출 필요성과 함께 확인합니다. 모든 출처 허용·CORS 비활성화·브라우저 보안 해제는 하지 않습니다.
- [상황판의 수정 호출](../../../frontend/src/features/situationBoard/presentation/components/map/SearchMapCanvas.tsx)은 기준 마커 위치 보정에 연결돼 있습니다. 삭제는 API 함수·hook은 있지만 이번 검색에서는 화면 호출부를 찾지 못했습니다. 이번 결과는 실제 브라우저 HTTP 요청의 거부 확인이며 화면 버튼을 통한 저장·삭제 완료 시험이 아닙니다.
- 수정 뒤 재현 명령, 실제 위치 보정, 미허용 출처 거부, 로그인·SSE를 다시 검증합니다. 이 진단 단계에서는 제품 코드·서버 설정·배포를 변경하거나 전체 Backend·Frontend 테스트를 재실행하지 않았습니다.

로컬 재현 명령은 `node _workspace/cors-web-verify-20260928.oUuoni/verify-browser-cors.cjs`입니다. 진단 당시 두 브라우저 명령이 CORS에서 거부돼 종료 코드 1로 실패했습니다. 로그는 같은 디렉터리의 `browser.log`에 있으며 인증 정보·좌표 원문은 저장하지 않았습니다.

## 수정: 외부 HTTPS 정보를 서버까지 전달합니다

2026-09-28, 로컬 코드를 수정하고 검증했습니다. **아직 커밋·푸시·Hetzner 배포는 하지 않았습니다.**

- **프록시**: Frontend nginx의 `/api/`와 같은 Backend로 연결되는 `/tiles/`에서 외부 프로토콜·포트를 보존합니다. 전달 헤더가 없는 직접 HTTP 호출은 기존 내부 주소를 사용합니다. 외부에서 넣은 별도 `Forwarded` 헤더는 Backend로 넘기지 않습니다.
- **Backend**: `server.forward-headers-strategy: native`로 Tomcat의 기본 전달 헤더 처리를 사용합니다. 새 필터·라이브러리를 추가하거나 현재 도메인을 CORS 허용 목록에 넣지 않았습니다. 기존 허용 목록·인증·채널 권한은 유지했습니다.
- **신뢰 범위**: 호스트 nginx가 외부 요청의 프로토콜·포트를 덮어쓰고 Frontend·Backend 포트가 외부에 직접 노출되지 않아야 합니다. 확인한 runtime Compose의 두 포트는 `127.0.0.1`에 바인딩돼 있습니다. [Tomcat의 기본 내부 프록시 범위](https://tomcat.apache.org/tomcat-10.1-doc/config/valve.html#Remote_IP_Valve)는 사설·루프백 주소이며 특정 nginx 한 대로 제한한 설정은 아닙니다.

## 로컬 검증: 같은 출처는 통과하고 다른 출처는 거부합니다

수정 전 실제 Tomcat 회귀 테스트는 7개 중 HTTPS 관련 5개가 403으로 실패했습니다. Backend만 수정한 뒤에도 이전 Frontend 설정을 거치면 403이었고, 두 설정을 함께 바꾼 뒤 아래 검증이 통과했습니다.

| 검증 | 결과 |
|---|---|
| 실제 Tomcat 회귀 테스트 7개 + 기존 보안 테스트 21개 | 28개 통과. 같은 HTTPS 출처는 허용 목록 없이 200, 인증 없는 POST·PATCH·DELETE는 CORS가 아닌 401 인증 오류 |
| 로컬 TLS nginx → Frontend nginx → 수정한 Backend JAR | 11개 통과. 같은 출처·OPTIONS·직접 HTTP 통과, 미허용 출처·`null` 출처·다른 프로토콜/포트·위조 전달 헤더 거부 |
| Backend 전체 테스트 | 223개 클래스·1,305개 통과, 실패·오류·건너뜀 0. 실제 PostgreSQL/PostGIS 사용, 5분 12초. 기본 설정의 `performance` 태그 제외 |
| Frontend | nginx 설정 테스트 5개·`npm run typecheck`·`npm run build` 통과. 빌드의 기존 500 kB 초과 청크 경고는 남음 |
| 수정한 nginx 실행 설정 | `nginx -t` 통과 |

Backend는 Docker의 `gradle:8.14.3-jdk17`에서 `./gradlew --offline --no-daemon spotlessJavaApply test -PspotlessIdeHook=/workspace/backend/src/test/java/com/surimap/config/TomcatProxyTest.java --console=plain`으로 검증했습니다. 새 테스트 파일만 포맷했습니다. WSL에서는 새로 연 Java 루프백 소켓에 연결되지 않는 별도 환경 문제가 있어 이 실행을 통과로 계산하지 않고 Docker에서 실제 HTTP·DB 테스트를 수행했습니다.

로컬 프록시 검사 스크립트와 결과는 `_workspace/cors-fix-20260928.heWyQO/verify-local-proxy.cjs`, `proxy-check.log`에 보관했습니다. 이 검사는 격리된 검증 컨테이너에만 요청하며 컨테이너 재구성이 필요합니다. 실제 Keycloak 로그인·마커 저장·UI 갱신을 검증한 결과가 아닙니다.

## 남은 작업: 배포 후 실제 화면을 확인합니다

- 수정분을 커밋·푸시하고 Jenkins 배포 후 실행 설정과 배포 버전을 확인합니다.
- 실제 로그인한 상황판에서 기준 마커 위치 보정·저장·SSE 후속 갱신을 확인하고, 미허용 출처는 계속 거부되는지 재검증합니다. 삭제는 화면 연결이 확인되지 않았으므로 HTTP 검증과 구분합니다.
- 과거·로컬 출처의 CORS 허용 목록 정리는 실제 교차 출처 호출 필요성을 확인한 뒤 별도로 판단합니다. 이번 수정에서 임의로 확대·삭제하지 않았습니다.
