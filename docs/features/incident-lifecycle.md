# 사건 가져오기·배정·종료

사건을 가져오거나 배정을 반영하고 종료할 때 확인할 기록이다. 옛 S1-1의 필요한 요구와 구현 차이를 남겼다. **기존 요구**는 후속 작업에서 유효성과 범위를 다시 확인할 대상이며, 이번 정리로 새 구현 기준을 확정한 것은 아니다.

현재 구현 설명은 2026-09-21 정적 코드 대조 결과다. 제품 목적은 [PRD](../prd.md#사건상황판), 원천 시스템과 역할을 나눈 이유는 [사건·배정 결정](../adr/0030-external-incident-source.md), 요청 형식은 [API 참고](../api/api-spec.md#42-incident)에서 확인한다.

## 원천 사건 가져오기

- Suri-Map 사용자가 사건을 새로 만들거나 지원 부대를 배정하는 것이 아니라 mock 112의 결과를 가져온다. 원천 `sourceIncidentId`와 내부 `incident.id`를 구분한다. 실제 경찰 시스템 연동은 이 흐름의 검증 범위가 아니다.
- **기존 요구**: 웹의 실종팀 지휘 계정 또는 지구대·파출소 지휘 계정만 가져오기를 요청하며, 요청자는 본문이 아닌 인증 정보에서 결정한다. 지원 부대의 현장 지휘 권한만으로는 허용하지 않는다. [현재 권한 검사](../../backend/src/main/java/com/surimap/incident/service/IncidentImportAuthorizer.java)도 공개 요청에서 이 구분을 사용한다.
- **현재 차이**: [polling](../../backend/src/main/java/com/surimap/external/mock112/Mock112PollingJob.java)은 새 사건을 후보로 보관하지만, [웹훅 Service](../../backend/src/main/java/com/surimap/incident/service/Mock112WebhookService.java)는 `INCIDENT_READY`를 받으면 내부 가져오기를 실행한다. 수동 가져오기만을 설명한 원문과 구분한다. 내부 요청은 [Controller](../../backend/src/main/java/com/surimap/incident/controller/Mock112WebhookController.java)의 채널·서명 검사를 거친다.
- [가져오기 Service](../../backend/src/main/java/com/surimap/incident/service/IncidentImportService.java)는 사건·실종자·배정·OP1·초기 마커·이벤트 저장을 한 트랜잭션으로 묶는다. 중간 실패 시 일부 데이터만 남지 않아야 한다는 요구는 유지한다. DB 롤백과 외부 HTTP 요청의 취소는 별개이므로, 웹훅의 원천 `markImported` 호출까지 함께 되돌아간다고 가정하지 않는다.
- **재요청의 차이**: 같은 원천 사건이면 기존 사건을 재사용하고 하위 생성을 반복하지 않는다. 그러나 저장된 최초 응답을 그대로 반환하지 않고 현재 사건·배정으로 응답을 다시 만든다. 같은 멱등키의 다른 본문은 거부하며, 종료된 사건의 재가져오기도 거부한다. 최초 응답 재현·동시 요청 중복 방지는 각각 확인할 대상이다.

## 배정 반영과 사건 접근

- **기존 요구**: 실종팀 인계·지원 부대 추가 시 기존 참여 계정과 OP1의 경로·마커·메모를 보존한다. 외부 계정 코드는 내부 계정 UUID로 변환한다. 개인·공유 계정의 세부 선택은 [PRD의 계정·권한 미정 항목](../prd.md#사건상황판)과 함께 확인하며, 옛 팀 계정 가정을 현재 구현 기준으로 자동 채택하지 않는다.
- **현재 차이**: [배정 반영 Service](../../backend/src/main/java/com/surimap/incident/service/IncidentAssignmentImportService.java)와 [SQL](../../backend/src/main/resources/mapper/incident/IncidentMapper.xml)은 활성 배정이 없는 계정만 추가한다. 기존 배정의 역할 변경이나 철회는 처리하지 않으므로, 원문의 포괄적인 “배정 갱신”과 구분한다. 종료된 사건의 비어 있지 않은 배정 변경은 거부한다.
- **조회 범위의 차이**: [조회 Service](../../backend/src/main/java/com/surimap/incident/service/IncidentReadQueryService.java)는 일반 계정에는 계정별 배정, 웹 지휘 계정에는 조직 **종류** 기준 조회를 사용한다. 개별 조직 ID 기준이 아니다. [공용 접근 검사](../../backend/src/main/java/com/surimap/incident/service/IncidentAssignmentAccessGuard.java)는 해당 계정의 활성 배정이 하나라도 있는지만 확인하므로, 조회·종료 등 각 호출부의 사건별 권한 검사를 함께 확인한다. “배정된 사건만 접근”이라는 기존 요구의 충족 여부를 애너테이션 존재만으로 판단하지 않는다.
- 목록은 현재 `OPEN` 사건만 조회하며 종료된 상세는 별도로 제공한다. 원문 목록의 `OPEN/CLOSED` 필터 설명과 소비 화면의 기대를 후속 작업에서 맞춘다.

## 배정 알림과 수신자

- **기존 요구**: 지원 배정 알림은 신규 배정된 팀·순찰차, 지원 요청은 지휘 계정·현장 지휘관, 발견 알림은 사건 배정 계정·업무폰 전체를 대상으로 한다. 알림 전달과 원본 배정 저장의 성공을 구분한다.
- **현재 차이**: [수신자 조회](../../backend/src/main/java/com/surimap/incident/service/IncidentAssignmentView.java)의 `SUPPORT_ASSIGNMENT`는 활성 지원 부대 팀·순찰차를 고르지만 신규 여부는 받지 않는다. 실제 배정 반영은 이 조회 대신 [FCM 전송 Service](../../backend/src/main/java/com/surimap/incident/service/IncidentAssignmentFcmDispatchService.java)에 추가된 계정 전체를 넘긴다. 수신자 범위가 같은 경로라고 가정하지 않는다.
- 배정 반영 Service는 트랜잭션 메서드 안에서 FCM을 직접 호출한다. 전송 실패의 로그·저장 보존과 커밋 이후 전송·재시도는 별개다. 실제 전달 시점·누락 복구는 [이벤트 전달 기록](./event-delivery.md)과 함께 확인한다.

## 사건 종료와 개인정보 제거

- 되돌리지 않는 종료와 개인정보 제거의 목적은 [제품 요구](../prd.md#사건-종료정보-파기)와 [종료 결정](../adr/0022-terminal-incident-lifecycle.md)에 남아 있다. 웹에서는 삭제 대상·재오픈 불가를 알리고 사용자가 확인하기 전에는 종료 요청을 보내지 않아야 한다.
- [종료 Service](../../backend/src/main/java/com/surimap/incident/service/IncidentCloseService.java)는 공개 호출의 실종팀 지휘 권한·`confirmPersonalDataRemoval=true`를 확인한 뒤 사건을 닫고 `missing_person` 행과 사진 주소 포인터를 삭제하며 `INCIDENT_CLOSED`를 발행한다. 내부 웹훅의 원천 종료 반영은 별도 진입점으로 동작한다.
- 행·포인터 삭제는 사진 파일·단말 Room·오프라인 패키지 삭제 완료와 다르다. 이 Service는 파기 조율자를 직접 호출하지 않는다. 종료 이벤트의 실제 소비·쓰기 거부·단말 개인정보 제거는 [오프라인 종료 처리](./offline-sync.md#사건-종료-후-남은-기록)와 [패키지 파기](./offline-package.md#접근-권한과-사건-종료-후-정리)를 포함해 확인한다.
- 종료 응답·이벤트·상황판·로컬 요약은 개인정보 없이 종료 상태를 표시해야 한다. 사건 제목·마커·메모 등 다른 필드의 개인정보까지 `missing_person` 삭제로 제거됐다고 판단하지 않는다. 같은 멱등키의 종료 재요청과 새로운 종료 요청의 거부도 구분한다.

## 테스트 입력과 원문

- 원문은 구체적인 실행 입력을 [공용 fixture](../../test-fixtures/common-fixtures.json)의 `incidentSeed`·`mock112SourceContract` 등으로 분리해 두었다. 이번에는 값·필드·ID를 그대로 두고 출처 경로만 바꿨다. [mock 112 seed](../../mock-112/src/main/resources/seed/precinct-first-scenario.json)와 [시연 입력 기록](../guides/mock-112-demo.md)도 유지한다. 합성 자료만 사용하며 실제 개인정보를 fixture·로그에 넣지 않는 요구를 보존한다.
- [사건 시드 로더](../../backend/src/test/java/com/surimap/incident/fixture/IncidentSeedFixtureLoader.java)와 [종료 상태 로더](../../backend/src/test/java/com/surimap/incident/fixture/IncidentLifecycleFixtureLoader.java)는 S1-1이 아니라 공용 JSON을 읽는다. [기존 사건 흐름 시험](../../backend/src/test/java/com/surimap/incident/IncidentFlowHarnessMockContractTest.java)은 대역 간 상호작용을 확인하므로 실제 DB 롤백·FCM·SSE·상황판 검증과 구분한다. 원문 테스트 설명의 `mem-*` 배정 별칭을 현재 공용 `ia-*` 값에 덮어쓰지 않는다.
- 가져오기 p95 2초·100개 사건 목록 p95 500ms는 옛 하네스 목표다. 측정된 제품 기준이 아니다. 가져오기·OP1 실패·배정 변경·종료 요청 계측과 개인정보를 제외한 원천 ID·오류 코드 기록도 후속 검증 대상이다.
- 과거 요구·권한표·실패 시나리오·구현 계획: [S1-1 원문 (`3a67edb5`)](https://github.com/sonic8-8/suri-map/blob/3a67edb57144df34ced3540f36c0bbc28c8e5b15/docs/spec/specs/S1-1.json). 기존 주석·시험 기록의 S1-1 표기는 이 원문을 가리킨다. 모호한 테스트 이름은 [정리 후보](../refactoring/codebase-naming-candidates.md)에 등록하되 이번에 일괄 변경하지 않았다.
