# 수색 경로 레이어 분리 기록

리팩토링에서 선택한 구조와 이유를 보존한다. 반복된 코딩 규칙과 완료된 작업 목록은 덜었다. 새 작업의 규칙은 [Backend AGENTS.md](../../backend/AGENTS.md), 기능 요구와 현재 구현의 차이는 [수색 경로 문서](../features/search-path.md)에서 확인한다.

## 무엇을 나눴고 왜 나눴는가

| 선택 | 이유 |
|---|---|
| Controller DTO와 Service DTO 분리 | HTTP 입력·응답 형식과 업무 실행에 필요한 값을 구분한다. Controller에서 `toServiceRequest(...)`·`from(...)`으로 변환한다. |
| 쓰기의 멱등성 처리를 Service에 배치 | 오프라인 기록이 다시 도착해도 업무 변경과 최초 응답 재사용을 함께 처리하기 위해서다. Controller에 별도 응답 저장소를 두지 않는다. |
| 세그먼트 수정 API를 `SearchPathController`에 통합 | 세그먼트는 수색 경로에 속하며, 별도 Controller로 분리할 독립된 업무가 없었다. |
| 앱의 시작·상태 변경을 `AppSearchPathController`·`AppSearchPathService`에 배치 | 채널별 업무를 구분한다. `App` 접두사는 같은 이름의 Spring Bean 충돌도 피한다. |
| 도메인 객체·MyBatis Mapper 공유 | 같은 저장·계산 처리를 복제하지 않는다. 직접 Mapper를 호출하며 별도 Repository나 Query/Command Service를 추가하지 않았다. |

이름과 내부 배치를 정리하면서 `/api/search-paths/batch`, `APPEND_PATH_BATCH`, `PATH_APPENDED` 등 외부 URL·입력·이벤트의 호환성은 유지하는 범위로 진행했다. 이 기록만으로 모든 소비자의 호환성 검증이 끝났다고 판단하지 않는다.

## 현재 코드와 옛 설명의 차이

2026-09-21 코드 대조 기준이다. 아래 배치를 원하는 최종 설계로 새로 확정한 것은 아니다.

| 확인 지점 | 현재 연결 |
|---|---|
| [SearchPathController](../../backend/src/main/java/com/surimap/api/controller/path/SearchPathController.java) → [SearchPathService](../../backend/src/main/java/com/surimap/api/service/path/SearchPathService.java) | 경로 조회·세그먼트 수정뿐 아니라 **APP 전용 좌표 묶음 추가**도 여기에 있다. `api` 아래의 모든 기능이 웹 전용이라는 옛 설명은 부정확하다. |
| [AppSearchPathController](../../backend/src/main/java/com/surimap/app/controller/path/AppSearchPathController.java) → [AppSearchPathService](../../backend/src/main/java/com/surimap/app/service/path/AppSearchPathService.java) | APP 전용 경로 시작·상태 변경을 처리한다. |
| APP 요청의 업무폰·계정 | Controller가 업무폰과 인증 정보의 일치를 확인하고 계정 ID를 Service Request에 넣는다. 옛 문서와 달리 업무폰 ID 자체를 함께 전달하지 않는다. |
| [IdempotentResponseCache](../../backend/src/main/java/com/surimap/sync/idempotency/IdempotentResponseCache.java) | Service Request를 JSON으로 직렬화한 뒤 SHA-256 해시를 만든다. DTO의 기본 `toString()`을 쓰지 않는다. |
| [SearchPathMapper](../../backend/src/main/java/com/surimap/domain/path/SearchPathMapper.java) | 경로·좌표·구간·제외 좌표·생명주기 이벤트를 저장·조회한다. GPS 저장과 조회 시 도형 조립은 [후속 최적화](../features/search-path.md#좌표-저장과-도형-조립)에 해당한다. |
| [SearchPathEventPublisher](../../backend/src/main/java/com/surimap/global/event/SearchPathEventPublisher.java) | 두 Service가 사용하는 이벤트 발행 객체다. 옛 유지 목록의 `PathEventPublisher`·`PathServiceConfig`는 현재 존재하지 않는다. |

패키지 이동이나 공개 계약 변경은 이 차이를 근거로 자동 수행하지 않고, 해당 기능을 정리할 때 범위와 소비자를 함께 확인한다.

## 레이어별 검증 위치

테스트가 존재한다는 사실과 실제 통과 결과는 구분한다. 이번 문서 정리에서는 아래 테스트의 선언·연결을 확인했으며 수색 경로 테스트를 다시 실행하지 않았다.

| 대상 | 확인할 테스트와 범위 |
|---|---|
| Controller | [SearchPathControllerTest](../../backend/src/test/java/com/surimap/api/controller/path/SearchPathControllerTest.java), [AppSearchPathControllerTest](../../backend/src/test/java/com/surimap/app/controller/path/AppSearchPathControllerTest.java): `@WebMvcTest`로 요청·응답을 확인한다. Service는 대체하며 실제 DB 흐름의 검증은 아니다. |
| Service | [SearchPathServiceTest](../../backend/src/test/java/com/surimap/api/service/path/SearchPathServiceTest.java), [AppSearchPathServiceTest](../../backend/src/test/java/com/surimap/app/service/path/AppSearchPathServiceTest.java): PostgreSQL/PostGIS를 사용하는 Spring Boot 테스트다. |
| Mapper | [SearchPathMapperTest](../../backend/src/test/java/com/surimap/domain/path/SearchPathMapperTest.java): SQL 실행과 결과 매핑을 확인한다. Service 테스트와 같은 [PostGIS 지원 설정](../../backend/src/test/java/com/surimap/maparea/support/PostGisIntegrationTestSupport.java)을 사용하며 Docker가 없으면 건너뛸 수 있다. |
| 도메인 | [SearchPathTest](../../backend/src/test/java/com/surimap/domain/path/SearchPathTest.java), [SearchPathMetricsCalculatorTest](../../backend/src/test/java/com/surimap/domain/path/SearchPathMetricsCalculatorTest.java): 경로 상태·계산 동작을 확인할 위치다. |

## 과거 계획과 실제 결과

[정리 전 문서](https://github.com/sonic8-8/suri-map/blob/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/refactoring/search-path-layer-separation.md)에 당시 디렉터리 예시·삭제 목록·완료 기준을 보존했다. 이는 현재 작업 목록이나 실행 결과가 아니다.

문제의 발견 과정·병목 측정·수정 효과는 해당 Issue와 댓글의 실제 결과로 확인한다. 이 기록의 설계 의도나 테스트 목록을 측정 결과로 대신하지 않는다.
