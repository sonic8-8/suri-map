# Suri-Map Harness Scenarios

## 0. 문서 성격

하네스 작업 단위 정의. Spec 계약 카탈로그(`boundaries.md`)는 소유·계약을 정의하고, 이 문서는 **하네스가 실제로 구현하고 검증할 시나리오 단위**를 정의한다.

- PRD §5.1의 12단계 시범 시나리오를 기준으로 한다.
- 한 시나리오 = 한 하네스 작업 단위다.
- 상황판이 걸리는 시나리오는 S3-2 shell merge 경로를 거친다.
- 현재 유효한 ADR은 `adr.md`만 참조한다. 폐기·대체된 결정은 `adr-archive.md`에만 남기고 신규 하네스 기준으로 쓰지 않는다.

## 0.1 운영 원칙

- Spec 오너십은 정적이다. 예: 지도 기준 범위와 구역은 S2, OP와 인수인계는 S8.
- 시나리오 작업은 동적으로 배정할 수 있다.
- 6명이 각자 하나의 Lane 하네스를 운영한다. 각자 맡은 Lane의 red test, fixture, mock adapter를 먼저 만들고, 다른 Lane 구현은 `boundaries.md`의 `provides` 계약 mock으로 대체할 수 있어야 한다.
- 개별 Lane 하네스 PASS는 자기 계약의 PASS다. SC 최종 PASS는 `boundaries.md` §10 Harness Mapping에 걸린 모든 Lane 계약과 S4 event 수렴, S3-2 board snapshot 수렴까지 통과해야 한다.
- 다른 Spec 엔티티를 변경할 때는 해당 Spec 오너 LGTM이 필요하다.
- S4 이벤트 허브 실구현은 L2가 주도한다. 계약 변경은 전 Lane LGTM이지만 SSE 엔드포인트, `EventHub.publish`, `sse_event_log` 개발·유지보수·버그 수정은 L2 담당이다.
- 상황판 웹 UI 기여 규칙:
  - S3-2가 상황판 shell(페이지 라우트, layout, slot mounting, shared state wiring, final merge)을 단독 소유한다.
  - 시나리오 하네스는 `boundaries.md`의 `board_shell_slots` 계약에 정의된 slot-scoped renderer/component만 납품한다.
  - 직접 상황판 shell 파일 편집은 금지한다.
  - 페이지 레이아웃·라우팅과 얽힌 UI(예: marker detail/edit/delete panel)는 S3-2 소유다.

## 0.2 시나리오 블록 고정 필드

1. `given` - 전제 조건
2. `when` - 행동
3. `then` - 관찰 가능한 결과
4. `involved_specs` - 관여 Spec 목록
5. `involved_apis` - 관여 API·이벤트
6. `e2e_red_test` - 하네스 Tester가 작성할 red test 시나리오
7. `board_merge` - 상황판 shell merge 필요 여부 + 납품 slot
8. `notes` - 한계·의존·주의사항

`e2e_red_test`는 API·DB 결과만 확인하지 않는다. 사용자가 실제 화면에서 관찰할 수 있는 상태(로딩, 비활성화, 성공 피드백, 실패 사유, 재시도, 오프라인/미전송 표시, OP·단말·알림 구분)를 함께 검증한다.

`involved_specs`는 해당 시나리오가 직접 검증하는 계약·데이터·이벤트 오너를 적는다. 상황판 slot 통합은 `board_merge`에 별도로 적고, S3-2 shell·projection·snapshot 자체가 시나리오의 주요 검증 대상일 때만 S3-2를 `involved_specs`에도 포함한다. 단순 slot mount 경유는 `board_merge`와 `boundaries.md` §10 Harness Mapping에서 추적한다.

### 0.3 write 이벤트·상황판 수렴 공통 red test

SC-05, SC-06, SC-08, SC-10, SC-11, SC-12의 write 성공 red test는 아래 대조를 공통으로 포함한다.

- REST 응답의 `id`, `status`, `version`이 mock event outbox row, SSE payload, board snapshot row와 같은 사건·OP·Device·entity 상태를 말해야 한다.
- FCM을 보내는 시나리오는 FCM payload도 같은 `eventId`, recipient, entity `id`, `status`, `version`을 말해야 한다. 실제 외부 FCM 전송은 검증하지 않고 mock FCM dispatcher 수신 기록만 검증한다.
- write 성공 후 board snapshot projector는 REST 응답 `version` 이상으로 수렴해야 한다. snapshot row가 없거나 낮은 `version`의 stale snapshot을 반환하면 red test 실패다.
- 실패 주입 red test는 REST DB commit 성공 + outbox 미적재, outbox 적재 + SSE 미송신, FCM 대상 시나리오의 mock FCM dispatcher 미수신, board snapshot projector 미갱신을 각각 실패로 판정해야 한다.
- 실제 112 연동이나 외부 푸시 인프라는 하네스 전제가 아니다. 모든 payload·recipient·eventId·version 검증은 하네스 fixture 안에서 수행한다.

---

## 1. 시나리오 목록

| ID | 시나리오 이름 | 초기 오너 | 상황판 merge |
|---|---|---:|---|
| SC-01 | 배정 사건 가져오기·초동 활성화 | 1 | - |
| SC-02 | 실종팀 인계·지원 부대 배정 | 1 | ○ |
| SC-03 | 사건 오프라인 패키지 사전 적재 | 6 | 배지만 |
| SC-04 | 지도 기준 범위·구역 분할·할당 | 3 | ○ |
| SC-05 | 수색 세션·Device GPS 경로 | 4 | ○ |
| SC-06 | 현장 마커 생성 | 5 | ○ |
| SC-07 | 통신 단절 중 로컬 기록 | 4 | - |
| SC-08 | 지원 요청·실종자 발견 알림 | 5 | ○ |
| SC-09 | 통신 복구·동기화 | 4 | ○ |
| SC-10 | 구역 완료·새 OP 열기 | 3 | ○ |
| SC-11 | 인수인계·OP 비교·AI 요약 | 6 | ○ |
| SC-12 | 사건 종료·캐시 파기 | 1 | ○ |

**단일 하네스 closure 가능**: SC-01, SC-07

**S3-2 merge 경유**: SC-02, SC-03, SC-04, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12

### 1.1 지구대/파출소 초동 대응 관통 흐름

PRD v3의 지구대/파출소 반영은 단순 권한 추가가 아니라 **초동 대응과 실종팀 인계 흐름**이다. 하네스는 아래 흐름을 12단계 안에서 검증한다.

1. 지구대/파출소 팀장 또는 당직자가 mock·seed 배정 사건을 먼저 가져와 OP1을 연다. (SC-01)
2. 지구대/파출소 팀 계정·순찰차 계정이 초동 수색 패키지를 받고, 순찰차/도보 경로와 마커를 기록한다. (SC-03, SC-05, SC-06)
3. 지구대/파출소 초동 사건이 실종팀으로 인계되면 실종팀 지휘 계정·팀 계정이 사건 접근 권한을 얻고, 기존 지구대/파출소 OP1 기록은 보존된다. (SC-02)
4. 실종팀 지휘 계정이 같은 사건에 지원 부대를 배정하면 지원 부대 계정·단말이 합류하고, OP1 초동 기록과 현재 OP 상태를 이어받는다. (SC-02)
5. 지구대/파출소 팀장 또는 당직자가 무전 보고를 수신하고, 현장 지휘관이 완료 또는 OP 전환 필요성을 판단한 뒤 구역 완료·OP 전환·인수인계 메모 저장을 수행한다. (SC-10)
6. SC-10에서 OP2 개시 판단과 인수인계 저장이 끝난 뒤, 실종팀 지휘 계정이 OP1의 경로·마커·인수인계 메모와 AI 요약을 사후 확인·검토한다. (SC-11)

---

## 2. 시나리오 상세

### SC-01 · 배정 사건 가져오기·초동 활성화

- **given**: 실종팀 지휘 계정 또는 지구대/파출소 지휘 계정으로 웹 로그인, mock·seed 원천에 배정 사건과 실종자 기본 정보 존재
- **when**: 지휘 계정이 웹에서 배정 사건 가져오기를 실행
- **then**:
  1. 사건이 `BOOTSTRAPPING`을 거쳐 `OPEN` 상태로 등록된다.
  2. 실종자 기본 정보 운영 캐시가 채워진다.
  3. 사건 참여 계정, 현장 지휘관 역할 후보, 초기 기준 마커가 시드 기준으로 구성된다.
  4. `INCIDENT_CREATED` 발행 후 S8이 OP1을 자동 생성하고 `OP_TRANSITIONED(from=null, to=OP1)`을 발행한다.
  5. 지구대/파출소 팀장 또는 당직자가 가져온 사건은 OP1을 생성하고, 배정된 지구대/파출소 팀 계정·순찰차 계정이 즉시 사건을 조회할 수 있다.
  6. 가져오기 진행 중에는 버튼이 중복 실행되지 않도록 비활성화되고, 완료 후 OP1 생성과 배정 계정 활성화 상태가 화면에 표시된다.
- **involved_specs**: S1-1, S1-2, S4, S5, S8
- **involved_apis**:
  - `POST /incidents/import-from-seed`
  - `INCIDENT_CREATED`
  - `OP_TRANSITIONED(from=null)`
- **e2e_red_test**:
  - "배정 사건 가져오기 호출 시 사건이 `OPEN` 상태로 전이되고 OP1이 생성된다"
  - "지구대/파출소 지휘 계정이 가져온 사건은 지구대/파출소 팀 계정·순찰차 계정에 배정되고 OP1이 생성된다"
  - "가져오기 진행 중 버튼은 로딩·비활성 상태가 되고 중복 클릭해도 사건과 OP가 중복 생성되지 않는다"
  - "가져오기 완료 화면은 OP1 생성 완료와 접근 가능한 팀 계정·순찰차 계정을 사용자가 확인할 수 있게 표시한다"
  - "가져오기 실패 시 화면은 실패 사유와 재시도 가능 여부를 표시하고 진행 중 상태를 닫는다"
  - "`OPEN` 전이 전 구역·마커·세션 쓰기 요청은 `409 incident_bootstrapping` 응답하고 화면은 사용자 실패 상태로 닫힌다"
- **board_merge**: 사건 목록 갱신은 독립 페이지. 상황판 진입은 SC-04 이후 검증
- **notes**: 실제 경찰 시스템 직접 연동은 MVP 범위 밖이다. 시연은 mock·seed 원천으로 검증한다. 초동 대응 사건은 실종팀이 만든 사건과 같은 domain model을 쓰되, 최초 현장 지휘관 후보와 배정 계정이 지구대/파출소 시드에서 온다는 점을 red test로 고정한다.

---

### SC-02 · 실종팀 인계·지원 부대 배정

- **given**: 사건 `OPEN`. 실종팀 단독 사건은 실종팀 지휘 계정이 이미 배정되어 있고, 지구대/파출소 초동 사건은 실종팀 인계가 필요한 상태. 지구대/파출소 OP1에는 순찰차 경로, 도보 경로, 마커, 인수인계 메모가 seed 또는 선행 SC 결과로 존재
- **when**:
  1. system/mock seed 또는 하네스 fixture가 실종팀 인계 배정을 반영해 실종팀 지휘·팀 계정을 사건에 추가한다.
  2. 실종팀 지휘 계정이 웹에서 지원 부대를 선택해 같은 사건에 배정한다.
- **then**:
  1. 지구대/파출소 초동 사건에서는 실종팀 지휘 계정·팀 계정이 사건 접근 권한을 얻는다.
  2. 기존 지구대/파출소 계정과 OP1 기록은 유지된다.
  3. 배정 부대의 팀 계정·순찰차 계정·지휘 계정이 사건 접근 권한을 얻는다.
  4. 시드 직책 기준으로 현장 지휘관 역할 후보가 자동 부여된다.
  5. `INCIDENT_MEMBERSHIP_CHANGED` 발행 후 앱·웹 권한 캐시가 갱신된다.
  6. 배정 계정의 등록 단말에 사건 배정 알림이 도달한다.
  7. 상황판은 실종팀 인계 완료 상태와 기존 지구대/파출소 OP1 기록 보존 상태를 같은 사건 안에서 구분해 표시한다.
- **involved_specs**: S1-1, S1-2, S3-1, S3-2, S4, S5, S8
- **involved_apis**:
  - `POST /incidents/{incidentId}/memberships`
  - `POST /incidents/{incidentId}/commanders`
  - system/mock seed 인계 fixture 처리
  - `INCIDENT_MEMBERSHIP_CHANGED`
  - `FcmDispatcher.send`
- **e2e_red_test**:
  - "지구대/파출소 초동 사건 인계 시 실종팀 지휘 계정이 OP1 경로·마커·메모를 조회할 수 있다"
  - "인계 전 지구대/파출소 OP1에 생성된 경로·마커·메모의 ID가 인계 후 실종팀 상황판에서도 같은 사건·같은 OP1 소속으로 조회된다"
  - "실종팀 인계 후에도 지구대/파출소 팀 계정·순찰차 계정의 기존 사건 접근 권한은 유지된다"
  - "실종팀 지휘 계정 상황판에는 `초동 OP1`과 `실종팀 인계 완료` 상태가 표시되고 같은 사건이 중복 카드로 보이지 않는다"
  - "지원 부대 배정 시 해당 부대 계정이 사건 목록에서 사건을 볼 수 있다"
  - "지원 부대 배정 완료 후 배정 대상과 완료 피드백을 웹 화면에서 확인할 수 있다"
  - "실종팀 인계와 지원 부대 배정 진행 중에는 CTA가 로딩·비활성 상태가 되고 중복 클릭해도 membership이 중복 생성되지 않는다"
  - "실종팀 인계 또는 지원 부대 배정 실패 시 웹 화면은 실패 사유와 `다시 시도` CTA를 표시한다"
  - "시드 직책이 지휘관 후보인 계정은 구역·OP 관리 권한을 얻는다"
  - "미배정 팀 계정 또는 타 팀 계정이 사건 상세를 조회하면 `403 team_not_assigned`를 응답하고 화면은 사건 내용을 렌더링하지 않는다"
  - "미배정 팀 계정 또는 타 팀 계정이 인계 상세를 조회하면 `403 team_not_assigned`를 응답하고 화면은 인수인계 메모·OP 기록을 표시하지 않는다"
  - "미배정 팀 계정 또는 타 팀 계정이 지원 배정 화면에 진입하거나 배정 write를 호출하면 `403 team_not_assigned`를 응답하고 배정 후보 목록을 노출하지 않는다"
  - "사건에 배정된 팀 계정 또는 순찰차 계정이 지원 부대 배정 write를 호출하면 `403 role_denied`를 응답하고 지휘 계정 전용 CTA가 노출되지 않는다"
  - "웹 또는 앱 채널이 system/mock seed 인계 처리 API를 직접 호출하면 `403 channel_not_allowed`"
  - "system/mock seed 인계 fixture 반영 없이 웹 지원 부대 배정만 호출해도 실종팀 인계 완료 상태로 표시되지 않는다"
- **board_merge**: `path` slot + `marker` slot + `handover_status` slot + `op_history` slot
- **notes**: 부대·팀 편제 관리 UI는 MVP 범위가 아니다. 실종팀 인계와 지원 부대 배정 모두 시드 데이터 기반 membership 갱신으로 검증한다.

---

### SC-03 · 사건 오프라인 패키지 사전 적재

- **given**: 사건 `OPEN`, 사건 배정 계정이 앱에서 사건 진입. 초동 대응 케이스에서는 지구대/파출소 팀 계정 또는 순찰차 계정이 사건에 진입
- **when**: 앱에서 사건 오프라인 패키지 다운로드 시작
- **then**:
  1. 사건 메타, 실종자 정보, OP, 담당 구역, 초기 마커, 지도 기준 범위, 지도 타일이 단일 흐름으로 다운로드된다.
  2. 항목별 진행률이 표시되고 실패 항목은 재시도 가능하다.
  3. 적재 상태가 서버에 보고되고 `PACKAGE_STATUS_CHANGED`가 발행된다.
  4. 적재 미완료 단말은 상황판에 경고 배지로 표시된다.
  5. 지구대/파출소 초동 대응 단말은 담당 구역이 아직 없더라도 지도 기준 범위와 초기 기준 마커를 받아 순찰차/도보 수색을 시작할 수 있다.
  6. 앱은 실패한 항목명과 재시도 대상만 표시하고, 지도 타일 미다운로드 상태에서는 현장 진입 전 경고를 표시한다.
- **involved_specs**: S7, S1-1, S1-2, S2, S5, S8, S4, S3-2
- **involved_apis**:
  - `GET /incidents/{incidentId}/offline-package/manifest`
  - `POST /incidents/{incidentId}/offline-package/status`
  - `PACKAGE_STATUS_CHANGED`
- **e2e_red_test**:
  - "manifest에 사건 메타·실종자·OP·담당 구역·초기 마커·지도 기준 범위·타일 목록이 포함된다"
  - "지구대/파출소 순찰차 계정 manifest는 담당 구역이 없어도 OP1, 지도 기준 범위, 초기 기준 마커, 단말 식별 정보를 포함한다"
  - "다운로드 중 앱 화면은 사건 메타·실종자·OP·담당 구역·마커·지도 범위·타일의 항목별 진행률을 표시한다"
  - "다운로드 중 네트워크 실패 후 재시도 시 이미 받은 항목은 건너뛴다"
  - "일부 항목 다운로드 실패 시 앱은 실패 항목명과 `실패 항목 재시도` CTA를 표시하고 성공 항목을 다시 받지 않는다"
  - "실패 항목이 남은 패키지는 오프라인 사용 준비 완료, 수색 시작 가능, 서버 반영 완료 상태로 전환되지 않는다"
  - "만료된 manifest로 패키지 적재 상태 보고를 시도하면 앱은 만료/재다운로드 필요 상태를 표시하고 오프라인 사용 준비 완료, 수색 시작 가능, 서버 반영 완료 상태로 전환하지 않는다"
  - "지도 기준 범위 변경 후 기존 패키지는 stale package로 표시되고 오프라인 사용 준비 완료, 수색 시작 가능, 서버 반영 완료 상태로 유지되지 않는다"
  - "전체 오프라인 패키지 다운로드 성공 후 앱은 오프라인 사용 준비 완료 상태와 마지막 적재 시각/완료 항목을 표시하고, 상황판 미완료 경고 배지는 해제된다"
  - "웹에서 오프라인 패키지 적재 상태 보고 API를 호출하면 `403 channel_not_allowed`"
  - "미등록 Device가 manifest 조회 또는 패키지 적재 상태 보고를 호출하면 `403 device_not_registered`를 응답하고 앱은 사건 패키지를 저장하지 않는다"
  - "등록됐지만 해당 사건/OP에 배정되지 않은 Device가 manifest 조회 또는 패키지 적재 상태 보고를 호출하면 `403 device_not_assigned`를 응답하고 앱은 오프라인 사용 준비 완료로 표시하지 않는다"
  - "지도 타일 미완료 상태에서 사건 진입 시 앱은 지도 사용 제한 경고를 표시하고 상황판 경고 배지도 유지한다"
  - "상황판 경고 배지가 미완료 단말을 표시한다"
- **board_merge**: `package_badge` slot
- **notes**: 지도 기준 범위는 도심·외곽·논밭·하천·산악을 모두 포괄하는 범용 범위 개념이다.

---

### SC-04 · 지도 기준 범위·구역 분할·할당

- **given**: 사건 `OPEN`, 현장 지휘관 역할을 가진 지휘 계정으로 웹 상황판 접속
- **when**: 지휘 계정이 지도 기준 범위를 확인·조정하고 팀별 담당 구역을 분할·할당
- **then**:
  1. 사건의 `map_boundary`가 생성 또는 갱신되고 `MAP_BOUNDARY_CHANGED`가 발행된다.
  2. `search_area` 폴리곤이 생성되고 `AREA_CREATED`가 발행된다.
  3. `op_assignment`가 현재 OP 기준으로 추가되고 `OP_ASSIGNMENT_CHANGED`가 발행된다.
  4. 배정 계정과 단말에 담당 구역이 공유된다.
- **involved_specs**: S2, S8, S1-1, S1-2, S4, S7
- **involved_apis**:
  - `POST /incidents/{incidentId}/map-boundary`
  - `PATCH /incidents/{incidentId}/map-boundary`
  - `POST /search-areas`
  - `POST /search-areas/{areaId}/split`
  - `POST /operational-periods/{opId}/assignments`
- **e2e_red_test**:
  - "지도 기준 범위 조정 후 오프라인 패키지 manifest의 타일 범위가 갱신된다"
  - "`map_boundary` 변경은 타일/manifest stale만 갱신하고 `search_area` row, state, `op_assignment`를 생성하거나 변경하지 않는다"
  - "`search_area` 생성·분할·배정은 기존 `map_boundary`와 오프라인 패키지 manifest의 범위를 변경하지 않는다"
  - "지도 기준 범위·구역 저장 중에는 저장 버튼이 로딩·비활성 상태가 되고 중복 저장되지 않는다"
  - "지도 기준 범위와 수색 구역은 유효한 Polygon으로만 저장되고 자기 교차·빈 geometry는 거부된다"
  - "`map_boundary` 저장 payload의 geometry type이 Polygon이 아니면 `400 invalid_geometry`를 응답하고 boundary row, outbox, board snapshot을 변경하지 않는다"
  - "`map_boundary` 저장 성공 시 좌표는 EPSG:4326 `[lon, lat]`, ring 폐합, 연속 중복점 제거, 소수 6자리 precision으로 정규화되어 REST 응답·DB row·board snapshot이 같은 canonical geometry를 가진다"
  - "`map_boundary` 좌표가 하네스 기준 지도 envelope 밖이거나 경도/위도 범위를 이탈하면 `400 invalid_geometry`를 응답하고 draft만 유지한다"
  - "`map_boundary` Polygon이 0면적 또는 하네스 최소 면적 미만, 미폐합 ring, 자기교차, 빈 ring이면 `400 invalid_geometry`를 응답하고 기존 active boundary를 유지한다"
  - "`map_boundary` geometry의 CRS가 EPSG:4326이 아니거나 허용 precision을 초과하는 좌표가 정규화 후에도 기준을 만족하지 못하면 `400 invalid_geometry`를 응답한다"
  - "`search_area` 생성·분할 payload의 geometry type이 Polygon이 아니면 `400 invalid_geometry`를 응답하고 area row, assignment, outbox, board snapshot을 변경하지 않는다"
  - "`search_area` 저장 성공 시 좌표는 EPSG:4326 `[lon, lat]`, ring 폐합, 연속 중복점 제거, 소수 6자리 precision으로 정규화되어 REST 응답·DB row·board snapshot이 같은 canonical geometry를 가진다"
  - "`search_area` 좌표 또는 bbox가 현재 active `map_boundary` 밖이거나 경도/위도 범위를 이탈하면 `400 invalid_geometry`를 응답하고 구역·배정 draft만 유지한다"
  - "`search_area` Polygon이 0면적 또는 하네스 최소 면적 미만, 미폐합 ring, 자기교차, 빈 ring이면 `400 invalid_geometry`를 응답하고 기존 active area를 유지한다"
  - "`search_area` geometry의 CRS가 EPSG:4326이 아니거나 허용 precision을 초과하는 좌표가 정규화 후에도 기준을 만족하지 못하면 `400 invalid_geometry`를 응답한다"
  - "유효하지 않은 Polygon 저장 실패 시 오류 사유를 표시하고 사용자가 그리던 draft를 유지한다"
  - "구역 저장 실패 후 재시도하면 같은 draft로 다시 저장을 시도할 수 있다"
  - "구역 분할 결과가 기존 활성 구역과 겹치지 않는다"
  - "현장 지휘관 역할이 없는 계정의 구역 변경 요청은 `403 role_denied`"
  - "앱에서 지도 기준 범위 변경 API를 호출하면 `403 channel_not_allowed`"
  - "앱에서 구역 생성·분할 API를 호출하면 `403 channel_not_allowed`"
  - "앱에서 OP 담당 구역 배정 API를 호출하면 `403 channel_not_allowed`"
  - "지도 기준 범위·구역 변경 후 상황판은 변경된 영역을 즉시 강조하고 담당 단말에는 새 구역 배정 상태를 표시한다"
- **board_merge**: `map_boundary` slot + `area` slot
- **notes**: 시스템은 수색 누락을 자동 확정하거나 다음 수색 구역을 지시하지 않는다. 구역 판단은 현장 지휘관이 한다.

---

### SC-05 · 수색 세션·Device GPS 경로

- **given**: 사건 `OPEN`, 사건 배정 계정이 앱에서 현재 OP에 진입
- **when**:
  1. 앱 흐름: 앱에서 `수색 시작`을 누르고 5초 주기 GPS 수집, 10초 배치 전송을 수행한다.
  2. 웹 흐름: 상황판에서 이미 저장된 경로의 차량·도보 구간을 확인하고 필요 시 수동 보정한다.
- **then**:
  1. 앱 흐름에서 `search_session`이 현재 OP와 현재 Device 기준으로 생성된다.
  2. 앱 흐름에서 경로 포인트가 `search_path`와 `path_segment`에 누적되고 GPS 속도 기반으로 차량·도보 구간이 자동 분리된다.
  3. 앱 흐름에서 `PATH_APPENDED` 발행 후 상황판이 경로와 단말 최신성을 갱신한다.
  4. 앱 흐름에서 운용 중인 업무폰 궤도는 앱·웹 모두에서 별도 스타일로 표시된다.
  5. 앱 흐름에서 지구대/파출소 초동 대응 순찰차 업무폰 경로는 차량 구간 중심으로, 팀 업무폰 경로는 도보 구간 중심으로 OP1에 남는다.
  6. 앱 흐름에서 앱은 수색 세션의 시작·일시정지·재개·종료 상태와 현재 기록 중인 Device를 명확히 표시한다.
  7. 웹 흐름에서 현장 지휘관은 상황판에서 차량·도보 구간을 수동 보정할 수 있고, `PATH_SEGMENT_UPDATED` 발행 후 앱·웹의 구간 스타일이 갱신된다.
- **involved_specs**: S3-1, S1-2, S6, S8, S4, S2
- **involved_apis**:
  - `POST /search-sessions`
  - `POST /search-paths/batch`
  - `PATCH /path-segments/{segmentId}`
  - `PATH_APPENDED`
  - `PATH_SEGMENT_UPDATED`
- **e2e_red_test**:
  - "1시간 수집 중 REST 경로 전송 호출이 10초 배치 기준을 넘지 않는다"
  - "동일 batch 재전송 시 중복 포인트가 생성되지 않는다"
  - "상황판에서 현재 Device 경로가 다른 Device 경로와 구분된다"
  - "경로는 LineString 레이어로만 표시되고 완료 구역 Polygon 상태와 섞여 해석되지 않는다"
  - "`search_path` batch payload의 geometry type이 LineString이 아니면 `400 invalid_geometry`를 응답하고 search_path, path_segment, outbox, board snapshot을 변경하지 않는다"
  - "`search_path` 저장 성공 시 좌표는 EPSG:4326 `[lon, lat]`, 연속 중복점 제거, 소수 6자리 precision으로 정규화되어 REST 응답·DB row·board snapshot이 같은 canonical LineString을 가진다"
  - "`search_path` 포인트가 현재 `map_boundary` 또는 하네스 기준 지도 envelope 밖이거나 경도/위도 범위를 이탈하면 `400 invalid_geometry`로 해당 batch 전체를 거부하고 partial path write를 남기지 않는다"
  - "`search_path` LineString이 2개 미만 포인트, 0 길이, 하네스 최대 포인트 수 초과 중 하나이거나 포인트 `client_ts`가 수집 순서대로 증가하지 않으면 `400 invalid_geometry`를 응답한다"
  - "`search_path` geometry의 CRS가 EPSG:4326이 아니거나 허용 precision을 초과하는 좌표가 정규화 후에도 기준을 만족하지 못하면 `400 invalid_geometry`를 응답한다"
  - "수색 시작 후 앱의 기본 CTA는 `일시정지`·`종료`로 전환되고 기록 중 배지가 현재 Device 이름과 함께 표시된다"
  - "수색 시작 요청 중 앱은 시작 CTA를 로딩·비활성 상태로 표시하고 중복 탭해도 search_session이 중복 생성되지 않는다"
  - "수색 시작 실패 시 앱은 실패 사유와 재시도 CTA를 표시하고 시작 전 상태를 유지한다"
  - "일시정지 중에는 경로 선이 이어붙지 않고 앱·상황판 모두 일시정지 상태를 표시한다"
  - "네트워크 단절로 경로 배치 전송이 pending으로 전환되면 앱 화면에 미전송 상태와 큐 수량이 표시된다"
  - "GPS 권한 거부 또는 위치 품질 저하 시 앱은 기록 불가/품질 저하 경고를 표시한다"
  - "지구대/파출소 순찰차 업무폰으로 시작한 OP1 세션은 `PATROL_CAR` Device 경로로 저장되고 차량 구간 스타일로 표시된다"
  - "지구대/파출소 팀 업무폰으로 이어서 시작한 OP1 세션은 같은 사건·OP 아래 별도 Device 경로로 저장된다"
  - "앱에서 `PATCH /path-segments/{segmentId}`를 호출하면 `403 channel_not_allowed`"
  - "웹에서 `POST /search-sessions` 또는 `POST /search-paths/batch`를 호출하면 `403 channel_not_allowed`"
  - "미등록 Device가 `POST /search-sessions` 또는 `POST /search-paths/batch`를 호출하면 `403 device_not_registered`를 응답하고 경로·세션이 생성되지 않는다"
  - "등록됐지만 해당 사건/OP에 배정되지 않은 Device가 `POST /search-sessions` 또는 `POST /search-paths/batch`를 호출하면 `403 device_not_assigned`를 응답하고 앱은 기록 중 상태로 전환하지 않는다"
  - "사건에 배정된 팀 계정 또는 순찰차 계정이라도 현재 Device가 해당 사건/OP에 배정되지 않았으면 경로 write는 `403 device_not_assigned`를 응답한다"
  - "상황판에서 차량·도보 구간 수동 보정 중에는 저장 CTA가 로딩·비활성 상태가 되고 실패 시 기존 구간 스타일을 유지한다"
  - "수색 세션 생성, 경로 배치 추가, 구간 수동 보정 write는 §0.3 공통 red test에 따라 REST 응답 id/status/version, outbox, SSE payload, board snapshot path row가 같은 세션·경로·구간 상태를 말하고 snapshot version이 수렴해야 한다"
- **board_merge**: `path` slot + `device_freshness` slot
- **notes**: 경로 기록 주체는 팀 업무폰 또는 순찰차 업무폰이다. 근무 교대와 지구대/파출소 초동 수색은 OP·세션·Device 단위로 구분한다.

---

### SC-06 · 현장 마커 생성

- **given**: 사건 `OPEN`, 사건 배정 계정이 앱에서 현장 수색 중
- **when**: 앱 바텀시트에서 마커 유형을 선택하고 메모·사진을 선택 입력
- **then**:
  1. `marker`가 현재 OP와 현재 Device, 로그인 계정 기준으로 생성된다.
  2. 위치·시간·작성 계정은 자동 입력되고 필요 시 위치 수동 조정이 가능하다.
  3. `MARKER_CREATED` 발행 후 상황판과 다른 단말에 반영된다.
  4. 사진 첨부 시 presign, S3 업로드, finalize 흐름으로 저장된다.
  5. 오프라인 상태에서는 Outbox에 저장되고 복구 후 전송된다.
  6. 앱은 유형 선택만으로 즉시 저장 가능한 최소 입력 흐름을 제공하고, 오프라인 저장 항목은 pending 상태로 지도에 표시한다.
- **involved_specs**: S5, S1-2, S6, S8, S4, S2
- **involved_apis**:
  - `POST /markers`
  - `POST /markers/{markerId}/photos/presign`
  - `POST /markers/{markerId}/photos/{photoId}/finalize`
  - `MARKER_CREATED`
- **e2e_red_test**:
  - "앱에서 마커 생성 후 온라인 기준 3초 안에 상황판에 렌더링된다"
  - "마커 생성 시 board snapshot에는 marker slot/Point 표시만 추가되고 `search_path`, `search_area`, `op_toggle` row는 생성·변경되지 않는다"
  - "`marker` 생성 payload의 geometry type이 Point가 아니면 `400 invalid_geometry`를 응답하고 marker row, photo row, outbox, board snapshot을 변경하지 않는다"
  - "`marker` 저장 성공 시 좌표는 EPSG:4326 `[lon, lat]`, 소수 6자리 precision으로 정규화되어 REST 응답·DB row·board snapshot이 같은 canonical Point를 가진다"
  - "`marker` Point가 현재 `map_boundary` 또는 하네스 기준 지도 envelope 밖이거나 경도/위도 범위를 이탈하면 온라인 서버는 `400 invalid_geometry`로 거부하고, 오프라인 앱은 pending 마커를 서버 반영 완료로 바꾸지 않으며 로컬 경고를 표시한다"
  - "`marker` Point가 단일 `[lon, lat]` 좌표쌍이 아니거나 null, NaN, 빈 좌표, 추가 point 배열을 포함하면 `400 invalid_geometry`를 응답한다"
  - "`marker` geometry의 CRS가 EPSG:4326이 아니거나 허용 precision을 초과하는 좌표가 정규화 후에도 기준을 만족하지 못하면 `400 invalid_geometry`를 응답한다"
  - "지구대/파출소 팀 계정 또는 순찰차 계정이 OP1 초동 대응 중 단서·재확인 필요 마커를 생성할 수 있다"
  - "바텀시트에서 유형만 선택해 저장하면 메모·사진 없이 현재 위치·시간·작성 계정이 자동 입력된 마커가 생성된다"
  - "온라인 마커 저장 중 앱은 저장 중 상태를 표시하고 저장 CTA를 비활성화한다"
  - "온라인 마커 저장 성공 후 생성한 앱은 저장 완료 피드백을 표시하고 바텀시트를 닫거나 생성된 마커 상세로 전환한다"
  - "사진 없는 기본 마커 저장 실패 시 앱은 실패 사유와 마커 저장 재시도 CTA를 표시하고 입력 draft를 유지한다"
  - "오프라인 상태에서 만든 마커는 앱 지도에 pending 배지로 즉시 표시되고 복구 후 pending이 해제된다"
  - "사진 presign, upload, finalize 단계 실패 시 앱은 실패 단계와 사유를 표시하고 업로드 재시도 CTA를 제공한다"
  - "위치 수동 조정은 선택 동작이며 기본 저장 흐름을 막지 않는다"
  - "웹에서 현장 마커 생성 API를 호출하면 `403 channel_not_allowed`"
  - "웹에서 `POST /markers/{markerId}/photos/presign` 또는 `POST /markers/{markerId}/photos/{photoId}/finalize`를 직접 호출하면 `403 channel_not_allowed`"
  - "미등록 Device가 `POST /markers`, 사진 presign, 사진 finalize를 호출하면 `403 device_not_registered`를 응답하고 마커·사진이 생성되지 않는다"
  - "등록됐지만 해당 사건/OP에 배정되지 않은 Device가 `POST /markers`, 사진 presign, 사진 finalize를 호출하면 `403 device_not_assigned`를 응답하고 앱은 pending 마커를 서버 반영 완료로 바꾸지 않는다"
  - "사건에 배정된 팀 계정 또는 순찰차 계정이라도 현재 Device가 해당 사건/OP에 배정되지 않았으면 마커·사진 write는 `403 device_not_assigned`를 응답한다"
  - "사진 10장 또는 10MB 초과 업로드는 거부된다"
  - "마커 생성과 사진 finalize write는 §0.3 공통 red test에 따라 REST 응답 id/status/version, outbox, SSE payload, board snapshot marker/photo row가 같은 마커·사진 상태를 말하고 snapshot version이 수렴해야 한다"
- **board_merge**: `marker` slot
- **notes**: 현장 마커 생성은 앱 전용이다. 웹은 초기 기준 마커와 마커 조회·수정 UI를 별도 권한으로 다룬다.

---

### SC-07 · 통신 단절 중 로컬 기록

- **given**: 사건 `OPEN`, SC-03에서 오프라인 패키지 준비 완료 상태로 앱 수색 중 네트워크가 30분 이상 단절. SC-03 패키지 미완료·만료·stale package 단말은 오프라인 지도/사건 사용 가능 상태와 구분된다.
- **when**: 단절 상태에서 경로 수집과 마커 생성을 계속 수행
- **then**:
  1. 경로·마커·사진 임시 파일이 단말 로컬 DB와 Outbox에 저장된다.
  2. 앱은 "오프라인 / 기록 중" 상태와 미전송 큐 수량을 표시한다.
  3. GPS 중단, 배터리 저하, 지도 미다운로드 경고는 서버 의존 없이 로컬에서 표시된다.
  4. 서버 이벤트는 발행되지 않는다.
  5. 패키지 미완료·만료·stale package 상태에서는 오프라인 지도/사건 사용 가능 상태로 표시하지 않고 로컬 경고만 유지한다.
- **involved_specs**: S6, S1-2, S3-1, S5, S7
- **involved_apis**:
  - Android Room/SQLite Outbox
  - WorkManager retry policy
  - local warning detector
- **e2e_red_test**:
  - "30분 오프라인 동안 경로 포인트와 마커가 로컬에 남는다"
  - "SC-03 패키지 준비 완료 후 오프라인 전환 시 앱은 로컬 사건 정보와 오프라인 지도를 사용해 경로·마커 기록을 계속한다"
  - "오프라인 기록 중 앱 화면은 `오프라인 / 기록 중` 상태와 미전송 큐 수량을 표시한다"
  - "오프라인 중 경로 포인트 또는 마커가 로컬 저장되면 앱 지도/큐 화면에 pending 항목이 즉시 증가해 표시된다"
  - "패키지 준비 완료 상태에서 오프라인 지도/사건 사용 중 로컬 경고가 발생하면 서버 호출 없이 앱 경고와 Outbox 적재 상태가 함께 유지된다"
  - "패키지 미완료·만료·stale package 상태에서 네트워크가 단절되면 앱은 오프라인 지도/사건 사용 가능 상태로 전환하지 않고 미완료/재적재 필요 경고를 표시한다"
  - "패키지 준비 -> 오프라인 지도/사건 사용 -> 로컬 경고/Outbox 적재 -> SC-09 복구 동기화 순서의 상태 전이가 끊기지 않는다"
  - "로컬 저장 실패 시 앱은 기록 불가 사유와 재시도 또는 계속 기록 불가 상태를 표시한다"
  - "앱 재시작 후에도 미전송 큐가 유지된다"
  - "지도 미다운로드 상태에서 현장 진입 시 로컬 경고가 표시된다"
- **board_merge**: -
- **notes**: 이 시나리오는 서버 반영 전까지의 로컬 생존성을 검증한다. 서버 반영은 SC-09에서 검증한다.

---

### SC-08 · 지원 요청·실종자 발견 알림

- **given**: 사건 `OPEN`, 사건 배정 계정이 앱에서 지원 요청 또는 실종자 발견 마커를 생성
- **when**: `드론 필요`, `경찰견 필요`, `진입 불가`, `추가 인력`, `실종자 발견` 유형을 선택
- **then**:
  1. 지원 요청은 `SUPPORT_REQUEST_CREATED`, 실종자 발견은 `PERSON_FOUND`를 발행한다.
  2. 지원 요청은 실종팀 지휘 계정과 현장 지휘관 역할 계정에 우선 알림을 보낸다.
  3. 실종자 발견은 사건 배정 계정·단말 전체에 강조 알림을 보낸다.
  4. 웹 토스트, 앱 인앱 배너, 앱 백그라운드 OS notification은 같은 FCM data message를 기반으로 동작한다.
  5. 지원 요청과 실종자 발견은 색·문구·우선순위가 구분되고, 같은 이벤트의 반복 알림은 중복 노출되지 않는다.
- **involved_specs**: S5, S1-1, S1-2, S4, S6, S8
- **involved_apis**:
  - `POST /markers`
  - `SUPPORT_REQUEST_CREATED`
  - `PERSON_FOUND`
  - `FcmDispatcher.send`
- **e2e_red_test**:
  - "지원 요청 마커 생성 시 실종팀 지휘 계정과 현장 지휘관 역할 계정에 알림이 도달한다"
  - "실종자 발견 마커 생성 시 사건 배정 단말 전체에 강조 알림이 도달한다"
  - "지원 요청·실종자 발견 마커는 marker 엔티티와 toast/FCM payload가 같은 id/status/version을 참조하되, marker 표시와 알림 표시는 서로 다른 UI 책임으로 분리해 검증한다"
  - "지원 요청 또는 실종자 발견 마커 생성 중 앱은 로딩·CTA 비활성 상태를 표시한다"
  - "지원 요청 또는 실종자 발견 마커 저장 성공 후 생성한 앱은 전파 완료 피드백을 표시하고 pending 상태가 남지 않는다"
  - "지원 요청 또는 실종자 발견 마커 생성 실패 시 앱은 실패 사유와 재시도 CTA를 표시한다"
  - "오프라인 생성 항목은 수신 알림으로 표시하지 않고, 생성 단말 화면에서 `알림 전송 대기/pending` 상태로 표시한다"
  - "복구 후 전파 완료 시 pending 배지가 해제되고 전파 완료 피드백으로 바뀐다"
  - "지원 요청 알림은 웹 토스트·앱 배너·FCM data message 기반 백그라운드 OS notification에서 생성 계정/팀, Device 이름 또는 유형, OP, 마커 유형, 위치 요약을 함께 표시한다"
  - "실종자 발견 알림은 지원 요청과 다른 중요도·문구뿐 아니라 생성 출처 계정/Device/OP를 표시한다"
  - "지구대/파출소 순찰차 업무폰과 팀 업무폰이 각각 생성한 알림은 같은 사건·OP 안에서도 서로 다른 출처로 구분된다"
  - "동일 이벤트를 재수신해도 같은 화면에 중복 토스트·배너가 쌓이지 않는다"
  - "웹에서 지원 요청 또는 실종자 발견 마커 생성 API를 호출하면 `403 channel_not_allowed`"
  - "앱 백그라운드에서도 FCM data message가 도달하고 Android가 OS notification을 로컬 생성한다"
  - "지원 요청·실종자 발견 마커 write는 §0.3 공통 red test에 따라 REST 응답 id/status/version, outbox, SSE payload, FCM payload, board snapshot marker/toast row가 같은 알림 상태를 말하고 snapshot version이 수렴해야 한다"
- **board_merge**: `marker` slot + `toast` slot
- **notes**: 드론·경찰견은 실제 출동 요청·승인·장비 연동이 아니라 요청 위치 기록 마커에 한정한다.

---

### SC-09 · 통신 복구·동기화

- **given**: SC-07 상태에서 네트워크 복구
- **when**: WorkManager가 Outbox를 순차 전송하고 서버가 idempotency key를 검증
- **then**:
  1. 오프라인 동안 기록된 경로·마커가 누락 없이 서버에 반영된다.
  2. 동일 Outbox 항목 재전송은 기존 응답 재생 또는 중복 제거로 처리된다.
  3. 미전송 큐 대시보드가 큐 소진 과정을 표시한다.
  4. `PATH_APPENDED`, `MARKER_CREATED`, `PACKAGE_STATUS_CHANGED` 등 필요한 이벤트가 서버 수신 기준으로 발행된다.
  5. heartbeat 재개 후 상황판 위치 점이 stale에서 normal 상태로 돌아온다.
  6. 복구 성공 후 앱은 `미전송 0`, pending 해제, 복구 완료 상태를 표시하고 상황판도 동일한 최종 상태를 표시한다.
  7. 패키지 상태는 정상과 재적재 필요를 구분해 표시하고, 남은 실패·재시도·stale package 항목은 별도 상태로 남긴다.
- **involved_specs**: S6, S3-1, S5, S1-2, S3-2, S4, S7
- **involved_apis**:
  - `POST /sync/clock`
  - `POST /devices/{deviceId}/heartbeat`
  - Outbox flush/requeue
  - 기존 domain write API 재사용
- **e2e_red_test**:
  - "30분 오프라인 후 복구 시 경로 포인트·마커·사진 첨부·지원 요청·실종자 발견 알림 유실 0건"
  - "동일 idempotency key Outbox 항목 재전송 시 마커·경로·사진 finalize·알림 이벤트 중복 생성 0건"
  - "복구 동기화 진행 중 앱 화면은 미전송 큐 수량이 줄어드는 과정을 표시한다"
  - "복구 성공 후 앱과 상황판은 `미전송 0`, pending 해제, 복구 완료 상태를 표시한다"
  - "복구 성공 후 패키지 manifest가 최신이면 패키지 상태 정상으로 표시되고, manifest 만료 또는 지도 기준 범위 변경이 있으면 재적재 필요 상태로 구분된다"
  - "복구 중 일부 Outbox 항목만 실패하면 성공 항목은 완료 처리되고 실패 항목만 미전송 큐에 남아 재시도 상태로 표시된다"
  - "복구 후에도 남은 실패·재시도 항목과 stale package는 복구 완료 상태와 섞이지 않고 별도 경고/재적재 필요 상태로 남는다"
  - "복구 후 상황판 위치 점 최신성 표시가 normal로 돌아온다"
  - "mock event stream fixture가 SSE 재연결 `Last-Event-ID` replay, out-of-order stream, duplicate delivery를 주입한다"
  - "동일 `eventId` 재수신 시 marker/path/toast/OP row와 화면 요소가 중복 생성되지 않는다"
  - "`sequence` 또는 entity `version`이 더 낮은 stale event는 최신 board snapshot과 화면 상태를 덮지 못하고, 이벤트 순서 역전이 발생해도 최신 상태가 과거 상태로 회귀하지 않는다"
  - "웹에서 `POST /sync/clock`, `POST /devices/{deviceId}/heartbeat`, Outbox flush/requeue write를 직접 호출하면 `403 channel_not_allowed`"
  - "미등록 Device가 `POST /devices/{deviceId}/heartbeat` 또는 Outbox의 package/status·path·marker/photo write를 전송하면 `403 device_not_registered`를 응답하고 큐 항목은 완료 처리되지 않는다"
  - "등록됐지만 해당 사건/OP에 배정되지 않은 Device가 heartbeat 또는 Outbox의 package/status·path·marker/photo write를 전송하면 `403 device_not_assigned`를 응답하고 앱은 권한 실패 항목을 재시도 대기와 구분해 표시한다"
- **board_merge**: `marker` slot + `path` slot + `device_freshness` slot 이벤트 재수신
- **notes**: `client_ts`, `server_ts`, `clock_offset_ms`를 함께 저장한다. 충돌 판정은 서버 수신 시각 기준이다.

---

### SC-10 · 구역 완료·새 OP 열기

- **given**: 사건 `OPEN`, 현장 지휘관 역할 계정으로 웹 상황판 접속. 초동 케이스에서는 SC-02 실종팀 인계·지원 부대 배정 완료
- **when**: 무전 보고를 수신하고 현장 지휘관이 완료 또는 OP 전환 필요성을 판단한 뒤 구역 완료, OP 전환을 수행하고, 필요 시 앱 또는 웹에서 인수인계 메모를 저장
- **then**:
  1. 구역 상태가 `COMPLETED`로 변경되고 `AREA_STATE_CHANGED`가 발행된다.
  2. `search_area_history`에 처리 계정, 시각, 현재 OP, 메모가 기록된다.
  3. 앱과 웹에 완료 상태가 반영된다.
  4. 새 OP 생성 시 사유(`SHIFT_CHANGE`, `RESEARCH`, `NEW_AREA`, `OTHER`)가 기록되고 `OP_TRANSITIONED`가 발행된다.
  5. 지구대/파출소 팀장 또는 당직자는 초동 대응 OP1에서 수색 완료 보고를 반영하고, 근무 교대 시 `SHIFT_CHANGE`, 실종팀 인계 등 별도 사유가 필요할 때 `OTHER`와 인수인계 메모를 남기며 OP2를 열 수 있다.
  6. 상황판은 현재 OP와 완료된 이전 OP를 배지·레이어 토글·완료 상태 색상으로 구분한다.
- **involved_specs**: S2, S8, S1-2, S4, S1-1
- **involved_apis**:
  - `PATCH /search-areas/{areaId}/state`
  - `POST /operational-periods`
  - `POST /handover-memos`
  - `AREA_STATE_CHANGED`
  - `OP_TRANSITIONED`
- **e2e_red_test**:
  - "앱에서 구역 완료 API를 호출하면 `403 channel_not_allowed`"
  - "앱에서 새 OP 생성 API를 호출하면 `403 channel_not_allowed`"
  - "앱에서 현재 OP 인수인계 메모 저장 API를 호출하면 저장되고 웹 상황판 인수인계 뷰에 반영된다"
  - "웹에서 구역 완료 처리 시 모든 단말과 상황판에 완료 상태가 반영된다"
  - "구역 완료 저장 중에는 CTA가 로딩·비활성 상태가 되고 중복 클릭해도 상태 이력이 중복 생성되지 않는다"
  - "구역 완료 저장 실패 시 웹 화면은 실패 사유와 재시도 CTA를 표시하고 기존 구역 상태를 유지한다"
  - "새 OP 생성 시 사유가 필수이며 OP별 레이어 토글에 나타난다"
  - "OP2 생성 저장 중에는 CTA가 로딩·비활성 상태가 되고 실패 시 실패 사유와 재시도 CTA를 표시하며 기존 OP 상태를 유지한다"
  - "지구대/파출소 지휘 계정이 OP1 구역 완료와 인수인계용 OP2 생성을 수행할 수 있다"
  - "`OTHER` 사유로 OP2를 열 때 인수인계 메모가 함께 저장된다"
  - "지구대/파출소 OP1 초동 기록이 존재하고 실종팀 인계·지원 부대 배정이 끝난 같은 사건에서 OP2를 열면, OP1 기록·실종팀·지원 부대 참여 상태가 끊기지 않고 현재 OP만 OP2로 전환된다"
  - "지원 부대 배정 완료 후 OP2가 생성되면 지원 부대 계정/단말은 같은 사건의 OP2를 현재 OP로 보고, OP1 인수인계 기록과 OP2 시작 상태를 함께 확인할 수 있다"
  - "OP2 생성 후 상황판은 `현재 OP`와 `완료된 OP1`을 배지와 레이어 토글에서 명확히 구분한다"
  - "무전 보고 seed가 있어도 현장 지휘관 판단/확인 없이 구역 완료 또는 OP2 생성 요청은 처리되지 않는다"
  - "SC-10 실행 로그가 radio_report_received → commander_decision_recorded → area_completed/op_transitioned/handover_saved 순서로 남는다"
  - "완료 구역 상태 변경은 지도 색상, 구역 목록, 최근 변경 피드백에 동시에 반영된다"
  - "구역 완료, OP 전환, 인수인계 메모 write는 §0.3 공통 red test에 따라 REST 응답 id/status/version, outbox, SSE payload, board snapshot area/op/handover row가 같은 완료·현재 OP·메모 상태를 말하고 snapshot version이 수렴해야 한다"
- **board_merge**: `area` slot + `op_toggle` slot + `op_history` slot + `handover_memo` slot + `handover_status` slot
- **notes**: 구역 완료는 공식 수색 기록 확정이 아니라 상황 공유용 운영 상태다.

---

### SC-11 · 인수인계·OP 비교·AI 요약

- **given**: SC-10으로 OP1 구역 완료, OP2 전환 또는 인수인계 메모 저장이 끝난 사건. 초동 대응 케이스에서는 SC-02로 실종팀 인계가 끝났고, OP1에 지구대/파출소 순찰차·팀 업무폰 경로와 메모가 존재
- **when**: 현장 지휘관이 상황판에서 이미 저장된 OP별 레이어와 인수인계 메모를 비교하고 AI 수색 이력 요약을 생성·확인
- **then**:
  1. OP별 업무폰·순찰차 경로, 차량·도보 구간, 완료 구역, 재확인 필요 마커가 겹쳐 보인다.
  2. 인수인계 메모가 OP·구역·경로 맥락과 함께 표시된다.
  3. AI 요약은 이미 저장된 OP1 경로·마커·인수인계 메모를 바탕으로 "어디 일대를 수색했는지"를 사후 요약하고, 누락 확정·다음 구역 지시는 하지 않는다.
  4. AI 실패 시에도 수동 인수인계 메모와 OP 비교 화면은 동작한다.
  5. 실종팀 지휘 계정은 지구대/파출소가 남긴 OP1 경로·마커·메모와 AI 요약을 SC-10에서 완료된 OP2 전환 판단의 사후 검토 자료로 확인할 수 있다.
  6. AI 요약과 인수인계 메모에서 원본 OP·경로·마커로 되돌아갈 수 있어야 하며, 선택 중인 OP가 화면에서 항상 식별 가능해야 한다.
- **involved_specs**: S3-2, S1-2, S8, S3-1, S2, S5, S4, S1-1
- **involved_apis**:
  - `GET /incidents/{incidentId}/board/snapshot`
  - `POST /handover-memos`
  - `POST /operational-periods/{opId}/ai-summary`
  - `AI_SUMMARY_READY`
- **e2e_red_test**:
  - "OP1과 OP2 경로를 동시에 표시하고 토글할 수 있다"
  - "지구대/파출소 OP1 초동 수색 경로와 메모가 실종팀 지휘 계정의 상황판 인수인계 뷰에 표시된다"
  - "OP 비교 화면은 선택된 OP, 현재 OP, 완료 OP를 배지·범례·레이어 토글에서 구분한다"
  - "OP 비교 전후 `map_boundary`, `search_area`, `search_path`, `marker` 원본 geometry row와 board snapshot geometry hash가 동일하며 geometry write API, outbox row, SSE event가 새로 발생하지 않는다"
  - "OP 비교에서 적용한 스타일·필터·하이라이트는 board 표시 계층에만 반영되고 REST 조회 결과와 snapshot의 원본 geometry, status, version은 변경되지 않는다"
  - "AI 요약은 OP1 초동 대응의 순찰차 경로, 도보 경로, 주요 마커, 인수인계 메모를 요약한다"
  - "AI 요약 생성 중 상황판은 로딩 상태와 생성 CTA 비활성을 표시한다"
  - "AI 요약 생성 성공 시 상황판은 성공 피드백과 최신 요약 시각을 표시한다"
  - "AI 요약의 주요 문장은 원본 경로·마커·메모를 열 수 있는 근거 링크 또는 하이라이트를 제공한다"
  - "AI 요약은 다음 구역 추천 문장을 생성하지 않는다"
  - "OP1 구역 완료, OP2 전환, 인수인계 메모 저장 전에는 AI 요약 생성 CTA/API가 열리지 않는다"
  - "AI 요약 생성 후에도 OP 전환 판단 기록은 변경되지 않는다"
  - "앱에서 저장한 인수인계 메모도 OP 비교 화면과 AI 요약 입력 근거에 포함된다"
  - "앱에서 AI 요약 생성 API를 호출하면 `403 channel_not_allowed`"
  - "타 팀 지휘 계정 또는 사건 미배정 지휘 계정이 `GET /incidents/{incidentId}/board/snapshot`을 호출하면 `403 team_not_assigned`를 응답하고 상황판 shell은 OP 상세·경로·마커·메모를 렌더링하지 않는다"
  - "사건에 배정됐지만 현장 지휘관 역할이 없는 팀 계정 또는 순찰차 계정이 OP 상세의 AI 요약 생성 API를 호출하면 `403 role_denied`를 응답하고 생성 CTA가 노출되지 않는다"
  - "SC-10 완료 산출물 없이 SC-11 AI 요약 시나리오를 실행하면 선행 조건 실패로 처리된다"
  - "AI 요약 실패 시 실패 사유와 재시도 버튼을 표시하고 기존 OP 비교와 인수인계 메모가 계속 표시된다"
  - "인수인계 메모 저장과 AI 요약 생성 write는 §0.3 공통 red test에 따라 REST 응답 id/status/version, outbox, SSE payload, board snapshot handover/ai_summary row가 같은 메모·요약 상태를 말하고 snapshot version이 수렴해야 한다"
- **board_merge**: `op_toggle` slot + `handover_memo` slot + `ai_summary` slot
- **notes**: 이 시나리오는 상황판 shell 통합 검증의 중심이다. 특히 초동 대응 OP1을 실종팀이 이어받는 인수인계 흐름을 대표 red test로 둔다. 자동 판단 기능으로 확장하지 않는다.

---

### SC-12 · 사건 종료·캐시 파기

- **given**: 사건 `OPEN`, 실종팀 지휘 계정으로 웹 로그인
- **when**: 사건 종료 확인 다이얼로그를 승인
- **then**:
  1. 사건이 terminal 상태로 전이되고 재오픈되지 않는다.
  2. 실종자 개인정보·사진 운영 캐시가 즉시 제거된다.
  3. 단말은 동기화 완료 확인 후 사건 패키지, 지도 타일/manifest, 로컬 경로, 로컬 마커, 임시 사진, 개인정보 캐시를 삭제한다.
  4. SSAFY 시연·개발 데이터는 복구 확인을 위해 24시간 soft delete 후 파기된다.
  5. 종료 후 domain write API는 `409 incident_closed`를 반환한다.
- **involved_specs**: S1-1, S1-2, S1-3, S6, S7, S4, S3-1, S3-2, S5
- **involved_apis**:
  - `POST /incidents/{incidentId}/close`
  - `INCIDENT_CLOSED`
  - `INCIDENT_PURGED`
  - local package purge
  - Outbox flush/requeue closed guard
  - FCM incident topic unsubscribe
  - web SSE incident stream unsubscribe
- **e2e_red_test**:
  - "사건 종료 확인 다이얼로그는 삭제 대상과 재오픈 불가를 표시하고 승인 전에는 사건을 닫지 않는다"
  - "사건 종료 승인 후 종료 처리 중에는 CTA가 로딩·비활성 상태가 되고 중복 승인해도 종료 요청이 중복 처리되지 않는다"
  - "실종팀 지휘 권한이 없는 계정의 사건 종료 요청은 `403 role_denied`"
  - "앱에서 사건 종료 API를 호출하면 `403 channel_not_allowed`"
  - "사건 종료 성공 후 웹 화면은 종료 완료 상태와 후속 쓰기 불가 상태를 표시한다"
  - "사건 종료 실패 시 웹 화면은 실패 사유와 재시도 CTA를 표시한다"
  - "사건 종료 후 실종자 운영 캐시 조회는 `404` 또는 마스킹 응답"
  - "종료 후 세션 시작·재개, 경로 batch, 마커 사진 finalize, package status, Outbox 재전송은 `409 incident_closed` 또는 명시된 closed error를 응답하고 서버 상태와 Outbox를 변경하지 않는다"
  - "종료 후 domain write API와 sync write API를 직접 호출해도 경로·마커·사진·패키지 상태·세션 상태가 새로 생성되거나 갱신되지 않는다"
  - "종료 후 heartbeat 성공·실패 여부와 무관하게 사건 상세·상황판·tombstone 응답은 단말 최신 위치와 freshness 점을 재노출하지 않는다"
  - "`INCIDENT_CLOSED` 수신 후 앱은 사건을 `closed` 또는 `purging` 상태로 고정하고 Outbox flush/requeue를 거부한다"
  - "`INCIDENT_CLOSED` 수신 후 실패 주입으로 Outbox에 미전송 항목이 남아 있어도 앱은 재전송을 시작하지 않고 closed error 상태로 표시한다"
  - "`INCIDENT_CLOSED` 수신 후 오프라인 패키지, 지도 타일, manifest는 삭제되거나 무효화되어 오프라인 지도로 재진입할 수 없다"
  - "`INCIDENT_CLOSED` 수신 후 앱은 FCM incident topic 구독을 해제하고, 웹 상황판은 SSE incident stream을 닫아 이후 같은 사건의 실시간 이벤트를 수신·렌더링하지 않는다"
  - "단말 동기화 완료 후 로컬 사건 패키지가 제거된다"
  - "단말 동기화 완료 후 지도 타일과 manifest가 제거되거나 만료 상태로 무효화된다"
  - "단말 동기화 완료 후 로컬 경로가 제거된다"
  - "단말 동기화 완료 후 로컬 마커가 제거된다"
  - "단말 동기화 완료 후 임시 사진 파일이 제거된다"
  - "단말 동기화 완료 후 실종자 개인정보·사진 캐시와 위치 캐시가 제거된다"
  - "단말 로컬 파기 실패 또는 오프라인 단말은 앱 화면에 미파기·대기 상태로 표시된다"
  - "종료 후 사건 상세·상황판·앱 조회는 read-only/tombstone 응답으로 고정되고 실종자 개인정보, 사진, 실시간 위치, 단말 최신 위치를 재노출하지 않는다"
  - "종료 후 tombstone 조회는 사건 ID, 종료 상태, 종료 시각, 쓰기 불가 사유만 반환하고 오프라인 패키지 재다운로드 또는 실시간 스트림 재구독 링크를 포함하지 않는다"
  - "위치정보 접근 기록과 운영 기록 같은 내부 보존 기록은 사용자 UI, 앱 캐시, 오프라인 패키지, 상황판 조회 응답에 노출되지 않는다"
  - "사용자 삭제 대상인 개인정보·위치·사진·오프라인 패키지와 내부 보존 대상인 운영 기록은 서로 다른 저장소/fixture로 검증되고 사용자 삭제 처리로 내부 보존 기록이 사용자 조회 가능 상태가 되지 않는다"
  - "사건 종료 write는 §0.3 공통 red test에 따라 REST 응답 id/status/version, outbox, SSE payload, board snapshot incident row가 같은 terminal/closed 상태를 말하고 snapshot version이 수렴해야 한다"
- **board_merge**: `incident_terminal` slot + `package_badge` slot + tombstone snapshot 조회
- **notes**: 운영 로그·접속기록은 사용자 화면 기능이 아니다. 내부 보존은 UI·export 요구가 아니라 사용자 UI, 앱 캐시, 오프라인 패키지, 상황판 조회에 노출되지 않는다는 검증 대상으로만 다룬다. 사건 종료 시나리오의 검증 대상은 개인정보 캐시와 단말 로컬 데이터 파기다.

---

## 3. 시나리오 의존 그래프

일반 경로:

```
          SC-01 배정 사건 가져오기·초동 활성화
  ├─ SC-03 사건 오프라인 패키지 사전 적재
  │   └─ SC-07 통신 단절 중 로컬 기록
  │       └─ SC-09 통신 복구·동기화
  ├─ SC-04 지도 기준 범위·구역 분할·할당
  │   ├─ SC-05 수색 세션·Device GPS 경로
  │   ├─ SC-10 구역 완료·새 OP 열기
  │   └─ SC-11 인수인계·OP 비교·AI 요약
  ├─ SC-06 현장 마커 생성
  │   └─ SC-08 지원 요청·실종자 발견 알림
  └─ SC-12 사건 종료·캐시 파기

SC-05 수색 세션·Device GPS 경로
  ├─ SC-07 통신 단절 중 로컬 기록
  ├─ SC-09 통신 복구·동기화
  └─ SC-11 인수인계·OP 비교·AI 요약

SC-06 현장 마커 생성
  ├─ SC-07 통신 단절 중 로컬 기록
  ├─ SC-09 통신 복구·동기화
  └─ SC-11 인수인계·OP 비교·AI 요약

SC-07 통신 단절 중 로컬 기록
  └─ SC-09 통신 복구·동기화

SC-10 구역 완료·새 OP 열기
  └─ SC-11 인수인계·OP 비교·AI 요약

오프라인 패키지·복구 흐름:
SC-03 → SC-07 → SC-09
```

초동 조건부 경로:

```
SC-01 → SC-03/05/06 → SC-02 → SC-10 → SC-11

SC-02 실종팀 인계·지원 부대 배정
  └─ SC-10 구역 완료·새 OP 열기
      └─ SC-11 인수인계·OP 비교·AI 요약
```

SC-11은 SC-10의 산출물인 OP 전환 판단, 인수인계 메모 저장, 지원 합류 완료 상태가 필요하다. SC-11을 독립 실행할 때는 이 산출물을 seed fixture로 materialize해야 하며, seed가 없으면 선행 조건 실패로 처리한다.

공통 기반:

- S4 이벤트 허브는 SC-01 이후 대부분의 온라인 시나리오가 공유한다.
- S1-2 계정·Device·권한은 모든 API 시나리오의 공통 전제다.
- S6 Outbox는 SC-05, SC-06, SC-07, SC-09의 공통 전제다.
- S3-2 shell은 SC-02, SC-03, SC-04, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12의 최종 통합 지점이다.

---

## 4. 1주차 동결 후보

1. S1-2: team/account/device fixture와 channel/role matrix
2. S4: event payload envelope, `event_outbox`, SSE replay 최소 계약
3. S3-2: board shell slots, slot props, snapshot 수렴 기준
4. S6: Outbox row shape, idempotency key, replay/requeue 상태
5. S2: geometry fixture, `MapBoundaryQuery.of(incidentId)`, map/search area 최소 계약
6. SC-01: 사건 bootstrap + OP1 자동 생성
7. SC-05: Device 기준 search session/path schema
8. SC-06: marker schema + 앱 전용 생성 정책

동결 대상에서 제외하는 과거 개념:

- 사람 단위 로그인 전제
- 수색 누락 자동 확정
- 다음 수색 구역 자동 추천
- 사용자에게 노출되는 장기 조회 로그 기능

---

## 5. 잔여 통합 리스크

| 리스크 | 영향 | 우선 대응 |
|---|---|---|
| S4 이벤트 허브 지연 | 온라인 상황판 검증 지연 | L2가 1주차에 최소 SSE + publish contract 먼저 제공 |
| S3-2 shell slot 충돌 | 각 하네스 UI 납품물 merge 지연 | slot 이름·props·렌더 책임을 `boundaries.md`에서 먼저 동결 |
| 6개 Lane 하네스의 fixture 불일치 | 각자 PASS했지만 통합 SC가 실패 | seed account/device/incident ID와 eventId/version/snapshot 기대값을 1주차 동결 후보로 고정 |
| OP1 bootstrap 누락 | 대부분의 write API가 현재 OP를 찾지 못함 | SC-01 red test를 전 Lane 공통 선행 조건으로 둠 |
| 지구대/파출소 초동 흐름 누락 | PRD v3 주요 사용자와 초동 대응 가치가 하네스에서 검증되지 않음 | SC-01/02/03/05/10/11에 초동 OP1 → 실종팀 인계 흐름 red test 포함 |
| Device 기준 경로와 계정 권한 혼동 | 경로·마커 주체 불일치 | `accountId`, `deviceId`, `opId`를 모든 write payload에 명시 |
| 오프라인 패키지 manifest 범위 과다 | 현장 다운로드 실패 | 지도 기준 범위와 담당 구역 기반으로 타일 목록을 제한 |
| AI 요약이 자동 판단처럼 보임 | PRD 범위 이탈 | SC-11 red test에 "추천·누락 확정 문장 금지" 포함 |

---

## 6. 하네스 Fixture 기준

red test는 아래 fixture를 조합해 작성할 수 있어야 한다.

| fixture | 사용 시나리오 | 기준 |
|---|---|---|
| mock·seed 배정 사건 | SC-01, SC-02, SC-10, SC-12 | 실종팀 단독 사건과 지구대/파출소 초동 사건을 모두 포함한다. 실종팀 단독 사건은 `incidentId=inc-missing-solo-001`, `opId=op-solo-001-op1`, `teamId=team-missing-alpha`, `accountId=acct-cmd-alpha/acct-team-alpha`, `deviceId=dev-alpha-cmd-phone-01/dev-alpha-phone-01`, seed `markerId=mk-solo-ref-001`, `pathId=path-solo-foot-001`, `memoId=memo-solo-op1-001`를 쓴다. 지구대/파출소 초동 사건은 `incidentId=inc-precinct-first-001`, `opId=op-precinct-001-op1`, `teamId=team-precinct-jongno`, `accountId=acct-precinct-cmd/acct-precinct-car/acct-precinct-team`, `deviceId=dev-precinct-cmd-phone-01/dev-precinct-car-01/dev-precinct-phone-01`, seed `markerId=mk-precinct-clue-001`, `pathId=path-precinct-car-001/path-precinct-foot-001`, `memoId=memo-precinct-handover-001`를 쓴다. 지원 부대 fixture는 `teamId=team-support-bravo`, `accountId=acct-support-cmd/acct-support-car/acct-support-team`, `deviceId=dev-support-cmd-phone-01/dev-support-car-01/dev-support-phone-01`이고, role은 각각 `COMMANDER`, `PATROL`, `TEAM`이다. mock FCM recipient는 `fcm:dev-alpha-phone-01`, `fcm:dev-support-car-01`, `fcm:dev-support-phone-01`로 고정한다. 지휘 계정 deviceId는 웹 지휘·membership fixture 식별자로만 쓰며 Android 앱 FCM recipient로 고정하지 않는다. 실종팀 단독 사건의 지원 배정 전 기대 membership row는 `mem-solo-alpha-cmd-001=ACTIVE`, `mem-solo-alpha-team-001=ACTIVE`이고, 지원 배정 후 `mem-solo-support-cmd-001=ACTIVE`, `mem-solo-support-car-001=ACTIVE`, `mem-solo-support-team-001=ACTIVE`가 추가되어야 한다. 지구대/파출소 초동 사건의 인계 전 기대 membership row는 `mem-precinct-cmd-001=ACTIVE`, `mem-precinct-car-001=ACTIVE`, `mem-precinct-team-001=ACTIVE`뿐이고, 인계 후 기존 row와 OP1 seed ID가 보존된 채 `mem-precinct-alpha-cmd-001=ACTIVE`, `mem-precinct-alpha-team-001=ACTIVE`가 추가되어야 한다. 지원 배정 후에는 `mem-precinct-support-cmd-001=ACTIVE`, `mem-precinct-support-car-001=ACTIVE`, `mem-precinct-support-team-001=ACTIVE`가 추가되고 모든 기대 row의 `revokedAt`은 null이어야 한다. SC-10 OP 전환 후에도 OP1 marker/path/memo seed ID는 같은 사건·OP 소속으로 조회되어야 한다 |
| 팀 계정·순찰차 계정·지휘 계정 | 전 시나리오 | `COMMANDER`, `TEAM`, `PATROL` role과 `COMMAND`, `TEAM`, `PATROL_CAR` account type, Device 매핑을 포함한다. `acct-precinct-cmd`와 `acct-support-cmd`는 `COMMANDER/COMMAND`, `acct-precinct-team`과 `acct-support-team`은 `TEAM/TEAM`, `acct-precinct-car`와 `acct-support-car`는 `PATROL/PATROL_CAR`로 고정한다 |
| mock GPS 경로 | SC-05, SC-07, SC-09, SC-11 | 차량 속도 구간과 도보 속도 구간을 모두 포함한다. 하네스 path batch 최대치는 `120` points/request이며 초과 시 `400 invalid_geometry`를 기대한다. 자동 분리 정상 fixture `gps-path-normal-001`은 `pathId=path-precinct-mixed-001`, `deviceId=dev-precinct-car-01`, point 목록 `gps-precinct-001..gps-precinct-008`을 쓴다. point payload는 `(pointId, client_ts, lon, lat, speedMps)` 기준으로 `gps-precinct-001/2026-04-28T09:00:00+09:00/126.956000/37.570000/13.5`, `gps-precinct-002/2026-04-28T09:00:05+09:00/126.956650/37.570180/12.8`, `gps-precinct-003/2026-04-28T09:00:10+09:00/126.957300/37.570360/11.9`, `gps-precinct-004/2026-04-28T09:00:15+09:00/126.957850/37.570540/9.8`, `gps-precinct-005/2026-04-28T09:00:20+09:00/126.958000/37.570700/1.6`, `gps-precinct-006/2026-04-28T09:00:25+09:00/126.958080/37.570880/1.3`, `gps-precinct-007/2026-04-28T09:00:30+09:00/126.958160/37.571050/1.1`, `gps-precinct-008/2026-04-28T09:00:35+09:00/126.958250/37.571220/1.4`이다. 기대 `path_segment`는 `seg-precinct-vehicle-001` type `VEHICLE`, `startIndex=0`, `endIndex=3`, `startPointId=gps-precinct-001`, `endPointId=gps-precinct-004`와 `seg-precinct-foot-001` type `FOOT`, `startIndex=4`, `endIndex=7`, `startPointId=gps-precinct-005`, `endPointId=gps-precinct-008`이다. SC-05/07/09 red test는 이 pointId와 segment index를 기준으로 차량/도보 자동 분리, 오프라인 저장, 복구 후 서버 반영이 같은 segment 타입으로 유지되는지 검증한다. GPS 품질 저하 fixture는 `horizontalAccuracyM > 50`, `timestampSkewSec > 30`, `speedMps < 0 또는 > 45`, `5초 간 거리 > 200m`, 좌표 누락/null/NaN 중 하나를 포함한다. SC-05 red test는 품질 저하 point가 저장 완료 경로로 승격되지 않고 앱·상황판에 저품질 또는 제외 상태로 표시되는지 검증한다 |
| 하네스 geometry/GPS 기준 좌표 | SC-04, SC-05, SC-06 | 기준 지도 envelope는 EPSG:4326 bbox `minLon=126.900000`, `minLat=37.500000`, `maxLon=127.080000`, `maxLat=37.620000`이다. 최소 Polygon 면적은 `400m2`이고 좌표 precision은 소수 6자리 canonical 값을 기준으로 한다. 정상 `map_boundary` fixture는 `[[126.948000,37.565000],[126.968000,37.565000],[126.968000,37.579000],[126.948000,37.579000],[126.948000,37.565000]]`, 정상 `search_area` fixture는 `[[126.952000,37.568000],[126.961000,37.568000],[126.961000,37.575000],[126.952000,37.575000],[126.952000,37.568000]]`, 정상 marker fixture는 `[126.956500,37.571200]`를 쓴다. 실패 좌표 fixture는 `coord-outside-envelope=[127.200000,37.571200]`, `coord-latlon-swapped=[37.571200,126.956500]`, `polygon-unclosed`, `polygon-self-intersecting`, `polygon-too-small-under-400m2`, `point-null-nan`, `precision-over-6dp=[126.9565007,37.5712007]`로 고정하고 SC-04/05/06 geometry red test가 이 이름을 참조한다 |
| mock tile catalog/server 또는 local tile fixture | SC-03, SC-04 | 외부 지도 타일 네트워크를 사용하지 않는다. 하네스는 `tileManifestId=tile-manifest-inc-precinct-001-v1`, tile key 범위 `z=15..16`, `x=27925..27960`, `y=12680..12720`, blob URI `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf`를 제공한다. manifest는 사건 메타, boundary hash `boundary-hash-precinct-v1`, tile key 목록, blob sha256, byte size를 포함하고 blob fixture는 local file 또는 in-memory blob만 허용한다. 실패 주입 키는 `manifest-expired`, `manifest-boundary-stale`, `tile-404`, `tile-timeout`, `tile-checksum-mismatch`, `tile-corrupt-blob`이며 SC-03 항목별 재시도·미완료 red test와 SC-04 boundary 변경 후 stale manifest red test가 이 fixture를 참조한다. `*.tile.openstreetmap.org`, `*.mapbox.com`, `*.googleapis.com` 등 외부 tile host 호출이 관찰되면 red test 실패다 |
| mock network 상태 | SC-03, SC-07, SC-09 | 외부 지도/S3/FCM/112 네트워크를 사용하지 않고 하네스 endpoint별 실패·복구 스크립트로 온라인, 장시간 오프라인, 부분 복구, 중복 재전송을 재현한다. `net-script-manifest-001`은 `GET /incidents/{incidentId}/offline-package/manifest`에 대해 1단계 `200 manifest v1` 수신 후 local storage `offlinePackage.manifestId=tile-manifest-inc-precinct-001-v1`, `status=MANIFEST_READY`를 기대하고, 2단계 `503 network_offline` 또는 timeout에서는 `status=MANIFEST_FAILED_RETRYABLE`, Outbox 변경 없음, 3단계 복구 `200 same manifest`에서는 이미 받은 manifest hash를 재사용해 `status=MANIFEST_READY`로 돌아가야 한다. `net-script-tile-blob-001`은 `local://tiles/inc-precinct-first-001/{z}/{x}/{y}.pbf` blob fetch에 대해 성공 tile은 local storage `tileCache[{tileKey}].state=READY`, 실패 주입 `tile-timeout/tile-404/tile-checksum-mismatch`는 해당 tile만 `FAILED_RETRYABLE` 또는 `FAILED_CORRUPT`로 남기고 package 전체는 `READY`가 아니어야 하며, 복구 후 같은 tile key만 재시도해 `tileCache`가 `READY`로 수렴해야 한다. `net-script-domain-write-001`은 `POST /search-paths/batch`, `POST /markers`, `POST /incidents/{incidentId}/support-requests` 같은 domain write가 온라인이면 서버 commit + Outbox enqueue + local mirror `SYNCED`가 되고, 오프라인 실패 시 local storage row는 `PENDING_LOCAL`, Outbox row는 `outbox-path-001/outbox-marker-001/outbox-support-001` `PENDING_SEND`로 남으며 서버 row와 board snapshot은 생성되지 않아야 한다. `net-script-heartbeat-001`은 `POST /devices/{deviceId}/heartbeat` 또는 동등 heartbeat ping이 온라인이면 local storage `deviceConnectivity=ONLINE`, `lastHeartbeatAt` 갱신, 오프라인이면 `deviceConnectivity=OFFLINE`, `lastHeartbeatAt` 보존, 복구 첫 성공이면 `RECOVERING`을 거쳐 `ONLINE`으로 전이되어야 한다. `net-script-outbox-flush-001`은 SC-09 복구 시 Outbox `PENDING_SEND` row를 `SENDING -> ACKED`로 전이하고 local mirror를 `SYNCED`로 바꾸며, 부분 복구 `domain write 200 + SSE delayed`에서는 Outbox는 `ACKED`지만 board snapshot은 `PENDING_PROJECTION`, 중복 재전송은 같은 idempotency key 기준 서버 row 1건과 event outbox row 1건만 남아야 한다. SC-03은 manifest/tile 스크립트, SC-07은 domain write/heartbeat 스크립트, SC-09는 outbox flush/부분 복구/중복 재전송 스크립트를 참조한다 |
| mock object storage/presigned upload fixture | SC-06 | 실제 S3를 사용하지 않고 `mock://object-storage/suri-map-harness`와 presigned URL `http://127.0.0.1:18080/mock-upload/{photoId}`만 쓴다. 성공 fixture는 사진 `0장`, `1장`, `10장`, 파일 크기 `1_048_576 bytes`, `10_485_760 bytes`를 포함하고, 실패 fixture는 `11장`, `10_485_761 bytes`, `presign-denied`, `upload-timeout`, `upload-500`, `checksum-mismatch`, `finalize-missing-blob`, `finalize-duplicate`를 포함한다. presign/upload/finalize 각 단계는 성공·실패를 독립 주입할 수 있어야 하며, finalize 성공 시 photo row, outbox, SSE, board snapshot의 `photoId/status/version`이 일치해야 한다. 외부 `s3.amazonaws.com` 또는 실제 bucket endpoint 호출이 관찰되면 SC-06 red test 실패다 |
| mock event outbox | SC-05, SC-06, SC-08, SC-10, SC-11, SC-12 | REST DB commit 이후 outbox row의 `eventId`, entity `id`, `status`, `version`, payload를 검증하고 미적재 실패 주입을 재현 |
| mock SSE client | SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12 | outbox 기반 SSE payload의 `eventId`, entity `id`, `status`, `version`, `sequence`를 검증하고 outbox 적재 + SSE 미송신 실패, `Last-Event-ID` replay, duplicate delivery, reordered payload 주입을 재현 |
| mock board snapshot projector | SC-02, SC-03, SC-04, SC-05, SC-06, SC-08, SC-09, SC-10, SC-11, SC-12 | snapshot row가 write 응답 `version` 이상으로 수렴하는지 검증하고 board 미갱신·stale snapshot 실패 주입을 재현한다. 동일 `eventId` 재처리 시 중복 row 0건이어야 하며 낮은 `version` 또는 `sequence` event는 기존 최신 snapshot을 변경하지 않아야 한다 |
| mock FCM dispatcher | SC-02, SC-08 | 외부 FCM 없이 foreground, background, 동일 이벤트 재수신, payload/recipient/eventId/version을 검증하고 mock 미수신 실패 주입을 재현 |
| mock SC-10 command flow | SC-10 | mock radio report는 `radioReportId=rr-precinct-001`, `incidentId=inc-precinct-first-001`, `opId=op-precinct-001-op1`, `areaId=area-precinct-a1`, `reportedBy=acct-precinct-cmd`, `message=1구역 수색 완료, 북측 미확인 동선 존재`, `receivedAt=2026-04-28T10:15:00+09:00`를 쓴다. commander decision fixture는 `decisionId=dec-op-transition-001`, `decision=COMPLETE_AREA_AND_OPEN_NEXT_OP`, `nextOpId=op-precinct-001-op2`, `handoverMemoId=memo-precinct-handover-001`를 쓴다. 실행 로그 저장소는 `harness_execution_log`이며 검증 키는 `incidentId`, `opId`, `areaId`, `radioReportId`, `decisionId`, `eventId`, `sequence`, `actorAccountId`, `createdAt`이다. 기대 event/log sequence는 `radio_report_received -> commander_decision_recorded -> area_completed -> op_transitioned -> handover_saved -> board_snapshot_projected`이고, 누락·순서 역전·다른 사건/OP 키 혼입은 red test 실패다 |
| mock AI summary provider | SC-11 | 성공, 실패, 금지 문장 포함 응답을 재현 |
