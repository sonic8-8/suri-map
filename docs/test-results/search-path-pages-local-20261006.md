# 상황판 경로 페이지 API — 로컬 구현·검증

2026-10-06, 사용자 승인 범위는 두 Backend 조회 API와 로컬 검증이다.
웹 연결·실제 서버 migration/배포·본 부하·커밋·푸시는 하지 않았다.
기존 상황판은 여전히 전체 경로 조회를 사용한다.

## 구현한 동작

- [SearchPathBoardController](../../backend/src/main/java/com/surimap/api/controller/path/SearchPathBoardController.java): 웹 전용 구간/변경분 POST와 위치 조회 감사. 기본 비활성이다.
- [SearchPathBoardService](../../backend/src/main/java/com/surimap/api/service/path/SearchPathBoardService.java): 사건별 접근·종료/파기 제한, 요청 전체 검증, 경로 순환, 경로별 이력/변경분 진행을 계산한다.
- [SearchPathMapper](../../backend/src/main/resources/mapper/path/SearchPathMapper.xml): 도형·전체 GPS 없이 경로 메타데이터와 구간 후보를 읽고, 반환할 구간의 GPS만 조회한다. 기존 전체 경로 조회 SQL은 유지한다.
- 요청 한 번은 REPEATABLE READ로 경로 버전·구간·GPS를 같은 DB 시점에서 읽는다. 웹 요청 사이의 장기 트랜잭션이나 과거 도형 스냅샷 저장은 하지 않는다.
- 최초 구간 응답에서만 baselineVersion을 제공한다. 이후 이력 응답에는 생략해 최초 기준을 바꾸지 않는다. 변경분에서 발견한 새 경로는 baselineVersion과 changesProgress가 null이다.
- 형식 오류·미래 버전·중복/범위 밖 경로·실제 구간 시작 순번이 아닌 위치는 요청 전체 오류다. 숫자 버전을 문자열로 바꾸거나 소수 순번을 정수로 보정하지 않는다.
- 준비되지 않은 구간과 한도보다 큰 구간은 명시적으로 거부한다. 구간을 잘라 보내거나 누락한 빈 페이지를 반복하지 않는다.
- 구간 시작 순번 인덱스를 추가했다. 서버에 migration을 적용하지 않았다.

[HTTP 예시·설정·오류](../api/search-path-pages.md), [합의한 동작](../features/situation-board.md#경로-변경분-조회-설계)을 함께 확인한다.

## 정확성 검증

2026-10-07 커밋 준비 중 Backend 전체를 다시 실행했다. Docker 안의 `./gradlew --no-daemon test --console=plain`이 5분 44초에 완료됐으며, 222개 XML·1,413개 테스트에서 실패·오류·건너뜀은 0개다. 기본 performance 태그는 제외했다. 원본은 `_workspace/sse-nonblocking-impl-20260929.DOocJf/run.h9Ju1T/`에 있다. 실제 서버의 Flyway 적용·HTTP·브라우저 연결 검증은 아니다.

| 실행 | 결과 |
|---|---|
| 최초 빈 조회 테스트 | 미구현 예외로 실패한 뒤 접근 검사·빈 응답 구현 후 통과 |
| Backend 전체 최종 검사 | 222개 XML, 1,413개 통과. 실패·오류·건너뜀 0, 5분 21초. 기본 performance 태그 제외 |
| 마지막 관련 재검사 | 최소 좌표 한도의 120 리터럴을 기존 MAX_POINTS_PER_BATCH 상수로 바꾼 뒤 Service 17개·Controller 7개, 총 24개 다시 통과. 한도 값 자체는 동일 |
| 새 Service 검사 | 실제 Spring Boot·MyBatis·PostgreSQL/PostGIS 연결. 이력/변경분·동시 추가·페이지 사이 보정·상태만 변경·순환·잘못된 진행·접근·종료·파기·미보완/과대 구간 검증 |
| 새 Controller 검사 | WebMvcTest. 서비스·공통 배정 검사·감사 저장은 대역이며 실제 Servlet 네트워크나 JWT 서버를 연결한 검사는 아님. JSON·채널·감사 호출·입력 거부 검증 |
| 최종 비교 측정 | 동일한 최종 인덱스/조회 코드로 세 후보 각각 별도 DB에서 2회 예열 후 5회 측정, 세 실행 모두 통과 |
| 형식·문서 | 변경 Java 대상 Spotless 검사·git diff --check 통과. 관련 공유 문서 3개의 로컬 링크 대상 29개 존재 확인 |

첫 전체 실행은 1,413개 중 새 파기 테스트 1개가 실패했다.
파기 fixture의 필수 created_at/updated_at 누락을 수정한 뒤 전체를 다시 통과했다.
초기 이력 테스트에서도 fixture의 필수 시각·도형 누락을 보완했다.
이 준비 오류를 기존 서비스의 제품 결함이나 조회 성능 개선 근거로 취급하지 않는다.

## 응답 한도 후보 측정

격리한 로컬 PostGIS에 경로 468개 × 경로당 GPS 720개·구간 120개를 저장했다.
총 GPS 336,960개·구간 56,160개이며 좌표 시각은 2.5초 간격이다.
계정·근무는 공용 fixture를 재사용했고 경로는 ENDED 상태로 만들었다.
실제 업무폰 468대·서로 다른 계정의 동시 쓰기 부하가 아니라 **누적 조회량**을 맞춘 시험이다.

각 후보는 512MiB 테스트 JVM, JaCoCo 비활성, 다른 전체 테스트를 마친 뒤 순서대로 실행했다.
최초 페이지의 Service 호출 시간과 공개 HTTP DTO 변환·JSON 직렬화를 분리했다.
HTTP 네트워크, 압축, SSE, 브라우저 표시, 전체 이력 완료 시간은 포함하지 않는다.
로그에는 각 표본의 시간·응답 크기·경로/구간/좌표 수가 남아 있다.

| 시험 한도: 경로 / 구간 / 좌표 / 경로당 구간 | 실제 반환 | 조회 시간 중앙값 (범위) | 직렬화 중앙값 | JSON 크기 |
|---|---|---|---|---|
| 20 / 20 / 120 / 1 | 20경로·20구간·120좌표 | 127.948ms (111.307–148.564) | 1.533ms | 12,624 bytes |
| 80 / 80 / 480 / 1 | 80경로·80구간·480좌표 | 305.738ms (284.336–340.132) | 1.744ms | 50,244 bytes |
| 80 / 320 / 1,920 / 4 | 80경로·320구간·1,920좌표 | 682.963ms (612.519–768.099) | 1.657ms | 133,284 bytes |

20→80은 한 번에 보여줄 경로 수를 4배로, 마지막 후보는 경로당 이력량을 1→4구간으로 늘린 비교용 설정이다.
좌표 한도는 이 입력의 6좌표 구간 수에 맞춰 비교 변수를 명시한 것이며 구간을 고정 6좌표로 가정하는 제품 규칙이 아니다.
세 후보 모두 아직 받지 않은 이력이 있어 hasMore=true이고, 반환된 각 경로는 끝 순번 719와 baselineVersion 121을 포함했다.
요청/조회 범위 경로 수의 시험 한도는 1,000이었다. 이 값도 운영 기본값으로 채택하지 않았다.

한 응답을 작게 하면 첫 응답은 짧지만 전체 범위를 받는 HTTP 횟수는 늘 수 있다.
이번 다섯 표본으로 p95·서버 한계·현장 지연 목표를 판정하지 않는다.
기존 전체 경로 조회의 10초대 결과는 측정 범위가 다르므로 직접적인 개선율로 계산하지 않는다.
운영 기본값은 미설정이며, 다음 단계에서 시험 한도를 선택한 뒤 웹 표시와 전체 로딩을 검증해야 한다.

## 코드 리뷰 후 단일 좌표 응답 보완

2026-10-07 Q66 승인으로 원본 좌표가 하나인 구간을 두 새 API에서 검증했다. Service는 원본 한 좌표와 시작·끝 GPS 순번을 그대로 반환하지만, HTTP 응답의 한 좌표 LineString은 웹 형식 검사에서 거부됐다.

`SearchPathPageSegmentResponse.java`에서 표시용 좌표만 두 번 표현하도록 수정했다. DB·Service 원본, 시작·끝 순번, 버전과 기존 공용 API는 바꾸지 않는다. 응답 한도의 좌표 수는 원본 GPS 개수로 계산한다.

- `SearchPathBoardControllerTest.java`: 두 endpoint의 JSON 좌표 개수·값과 원본 순번 보존. 수정 전 기대 2개/실제 1개 실패 후 전체 클래스 8개 통과.
- `SearchPathBoardServiceTest.java`: 실제 MyBatis·PostGIS에서 두 조회의 원본 한 좌표·순번·시각 보존 확인.
- 최종 `./gradlew --no-daemon test --console=plain`: Docker에서 **222개 XML·1,415개 테스트 통과**, 실패·오류·건너뜀 0, 5분 59초. 기본 performance 태그 제외.
- 변경 Java 3개 파일의 Spotless 적용·검사 통과. 실제 서버 HTTP·브라우저 연결이나 서버 migration은 하지 않았다.

실행 원본은 `_workspace/sse-nonblocking-impl-20260929.DOocJf/` 아래의 `run.t2kWu2`(Controller 실패), `run.p6RvEa`(Controller 통과), `run.hEz04C`(Service 통과), `run.jOoYr1`(최종 전체)이다. 웹 소비와 캐시 보완은 [프런트엔드 검증 기록](search-path-pages-frontend-20261007.md#코드-리뷰에서-발견한-문제와-수정)에 구분했다.

## 이번 변경 파일

| 구분 | 파일 |
|---|---|
| Controller | `SearchPathBoardController.java` |
| HTTP 입력 | `SearchPathSegmentsQueryRequest.java`, `SearchPathChangesQueryRequest.java`, `SearchPathQueryDeserializers.java` |
| HTTP 출력 | `SearchPathSegmentsQueryResponse.java`, `SearchPathChangesQueryResponse.java`, `SearchPathPageSegmentResponse.java` |
| Service | `SearchPathBoardService.java`, `SearchPathPageServiceRequest.java`, `SearchPathPageServiceResponse.java` |
| 오류 | `ErrorCode.java`, `SearchPathQueryExceptionHandler.java` |
| DB | `SearchPathMapper.java`, `SearchPathMapper.xml`, `V20261006_003__index_search_path_segment_order.sql` |
| 테스트 | `SearchPathBoardControllerTest.java`, `SearchPathBoardServiceTest.java` |
| 공유 문서 | `docs/api/search-path-pages.md`, `docs/features/situation-board.md`, 이 결과 문서 |

HTTP 파일은 `api/controller/path`, Service 파일은 `api/service/path`, 오류는 `global/error` 하위다.
이전부터 있던 저장 단계·쓰기 경합 수정·문서 변경은 보존했으며 이번 작업의 신규 변경으로 계산하지 않는다.
로컬 작업 메모와 검증 원본은 Git 공유 대상이 아니다.

## 로컬 실행 원본

- 전체 최종: `_workspace/sse-nonblocking-impl-20260929.DOocJf/run.srzPyL/`
- 상수 재사용 후 관련 재검사: 같은 상위 경로의 `run.vdGvur/`
- 전체 최초 실패: 같은 상위 경로의 `run.F2QK6I/`
- 최초 빈 조회 실패/통과: `run.eogeQ9/`, `run.go12tE/`
- 최종 측정: `_workspace/path-pages-20261006.pZeTfR/measure.x7S9j0/`, `measure.8ooJ5f/`, `measure.HJqkJg/`
- 실행: `bash _workspace/sse-nonblocking-impl-20260929.DOocJf/verify.sh` → Docker 안의 `./gradlew test`.
- 측정: `bash _workspace/path-pages-20261006.pZeTfR/measure.sh` → performance 태그의 기존 Service 테스트 메서드를 실행한다.
- 인덱스 보강 전/도중 예비 측정은 최종 표에서 제외했다. 원본은 같은 디렉터리에 남아 있다.
