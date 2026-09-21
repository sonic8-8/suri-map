# 상황판 조회와 갱신

상황판의 데이터 조립·실시간 갱신·수색 차수 비교·사건 종료 표시를 바꿀 때 확인한다. 옛 S3-2 요구와 2026-09-21 정적 코드 대조 결과다. 테스트용 조립 결과와 실제 HTTP·SSE·브라우저 동작을 구분한다.

## 원본을 바꾸지 않는 조회

- 상황판은 사건·구역·경로·마커·업무폰·오프라인 패키지·차수·인수인계 정보를 모아 보여준다. 차수 비교, 필터, 단순 지도, 현재 계정·이동 유형 강조는 표시만 바꾼다. 원본 좌표·상태·버전을 수정하거나 다음 수색 구역·누락·위험도를 자동 판단하지 않는다. 마커 수정·삭제는 해당 기능의 API를 호출한다.
- [Controller](../../backend/src/main/java/com/surimap/board/IncidentBoardController.java)는 `/api/incidents/{incidentId}/board`에서 웹 채널·사건 접근·위치 조회 기록을 요구한다. [조회 서비스](../../backend/src/main/java/com/surimap/board/IncidentBoardQueryService.java) → [데이터 수집](../../backend/src/main/java/com/surimap/board/DefaultIncidentBoardSourceRowCollector.java) → [응답 조립](../../backend/src/main/java/com/surimap/board/BoardAssembler.java) 순서다. 애노테이션 선언만으로 사건별 권한 검사까지 완료됐다고 판단하지 않으며 [인증의 차이](authentication.md)를 함께 확인한다.
- `opIds`·`includeSlots`로 조회 범위를 지정한다. `sinceVersion`은 상황판 전체 응답의 이전 버전이지 각 원본 행의 증분 조회 기준이 아니다. 현재 수집기는 이 값으로 원본 행을 걸러내지 않는다. 서로 다른 원본의 버전을 하나의 순번처럼 비교하지 않는다.

## 이벤트 수신과 최신 데이터 재조회

- **기존 요구**: 동일한 `eventId`는 중복 적용하지 않고 오래된 이벤트가 최신 화면을 되돌리지 않게 한다. 재전송이 불가능하거나 순번 누락을 확인하면 전체 조회로 복구한다. SSE `id`는 사건별 재전송 순번이며 `data.eventId`와 다르다.
- [현재 화면 구독](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.ts)은 연결 성공·새 이벤트·재조회 요구 시 React Query 캐시를 무효화해 REST 재조회를 요청한다. 이벤트 payload를 곧바로 모든 지도 행에 덮어쓰는 구조가 아니다. `eventId` 중복은 Set으로 막지만 순번 간격을 직접 검사하지는 않는다. 재시작·놓친 알림 복구 범위는 [이벤트 전달](event-delivery.md)에서 확인한다.
- `BoardRefetchGuard`의 중복·오래된 버전·누락 판정은 기존 테스트에서 호출한다. `src/main`의 실제 호출부는 찾지 못했다. 이 대역 기반 검증이 실제 이벤트 전달·화면 갱신까지 보장한다고 설명하지 않는다.
- **확인할 값의 의미**: 현재 여러 행의 `latestEventId`는 실제 발행 UUID 조회가 아니라 ID·버전으로 만든 문자열이다. `geometryHash`도 좌표 자체가 아닌 ID·상태·버전 해시를 합친 값이다. 원문의 ‘REST·SSE의 동일 이벤트 추적’·‘좌표 원본 불변 검증’이 이 필드만으로 충족됐다고 판단하지 않는다. 공개 필드는 이번 정리에서 바꾸지 않았다.

## 사건 종료와 빈 응답의 구분

- **기존 요구**: 종료·파기된 사건은 허용된 종료 정보만 표시하고 실시간 위치·개인정보·패키지 재적재·재구독을 노출하지 않는다. 허용 정보는 사건 ID, 종료·파기 상태와 시각, 쓰기 불가 이유, 로컬 파기 상태다. 종료와 실제 데이터 삭제는 [데이터 파기](data-retention.md)에서 구분한다.
- **현재 차이**: 서버는 종료 정보의 필드를 제한하고 업무폰 최신성 행을 숨기지만, 데이터 수집기는 경로·마커 등 나머지 조회도 계속 호출한다. 종료 정보가 있다는 이유만으로 전체 응답의 위치·개인정보가 제거되는 것은 아니다.
- [화면의 이전 값 병합](../../frontend/src/features/board/model/incidentBoardMerge.ts)은 주요 슬롯이 비면 이전 값을 되살린다. 삭제·종료로 의도적으로 비운 결과와 일시 조회 누락을 구분하지 않으므로 별도 재현·검증이 필요하다. 구독 중단·종료 배너만으로 데이터 제거까지 완료됐다고 보지 않는다.

## 화면과 관측에서 남은 요구

- 슬롯 이름과 연결은 [서버 등록부](../../backend/src/main/java/com/surimap/board/BoardSlotRegistry.java)와 실제 화면 소비자를 확인한다. `marker`는 원본 마커 표시, `marker_notification`은 지원 요청·발견 알림 데이터다. 알림을 토스트·팝업 중 무엇으로 보여주는지는 별도 책임이며 옛 Lane 소유권 표로 현재 UI 수정 범위를 제한하지 않는다.
- 업무폰 최신성은 색뿐 아니라 외곽선·경과 시간으로 구분한다. 미완료 패키지 경고, 차수·인수인계 메모·수색 이력의 연결을 유지한다. 타일 실패 때 가능한 업무 레이어를 보존하고, 요약 실패가 차수 비교·메모 조회까지 막지 않게 하는 것이 기존 요구다. 최초 지도 범위는 전체 수색 범위 → 최근 활동 → 기본 지역 순으로 정했던 배경을 보존한다.
- 원문의 조회 p95 2초·온라인 반영 3초는 과거 시험 목표다. 이번에 측정하거나 운영 기준으로 확정하지 않았다. 조회 지연, 슬롯별 조립 실패, 재조회 이유·마지막 수신 순번·응답 버전의 관측 여부를 [Issue #8](../issues/8-gps-collection-transmission-basis.md)의 후속 시험에서 따로 확인한다.

## 테스트 입력과 원문

- [공용 JSON](../../test-fixtures/common-fixtures.json)의 `boardAssembly`를 기존 상황판 테스트가 직접 읽도록 바꿨다. 13개 슬롯 행과 응답 ID·버전·시각은 원문과 일치한다. 사건 ID는 같은 파일의 `incidentSeed`를 재사용한다. 기존 `sourceSpec`·fixture ID·값은 유지했다.
- 공용 JSON에 상세 값이 없던 중복·오래된 버전·선행 순번 누락·재전송 불가·재조회 지연 5개 입력은 [테스트 리소스](../../backend/src/test/resources/board-api-assembly-failures.json)에 원문 그대로 보존했다. 기존 테스트의 기대값·검증 범위는 바꾸지 않았다.
- 원문의 `latestEventId` UUID 형식 설명과 fixture의 `evt-*` 값, 종료 fixture에 남은 위치 행 등은 일치하지 않는다. 이를 문서 정리 과정에서 새로운 API 요구로 확정하거나 데이터를 자동 수정하지 않는다. 코드·입력의 모호한 이름은 [정리 후보](../refactoring/codebase-naming-candidates.md)에서 추적한다.
- 과거 응답 schema·슬롯별 요구·실패 사례·테스트 계획은 [S3-2 원문](https://github.com/sonic8-8/suri-map/blob/28f8d4dfc13096295142e3b807e1bc869ad268cf/docs/spec/specs/S3-2.json)에서 복원한다. 이 기록이 구현·브라우저 검증 완료를 뜻하지 않는다.
