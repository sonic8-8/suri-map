# L5 Tasks · Marker, Photo, Notification

상태: 초안. 담당 Lane: L5. 담당 Spec: S5. 주요 SC: SC-06, SC-08.

## 작업 범위

- marker domain, marker photos, marker read/update/delete
- Android marker bottom sheet input
- 업로드용 S3-compatible presigned URL 발급, object storage upload, photo attach contract
- support request/person found notification payload and recipient calculation
- FCM dispatcher adapter boundary

## 외부 계약

- Consumes L2/S1-2 auth/policePhone/FCM token fixture.
- Consumes L3/S2 overall_search_area validation and L3/S8 OP context.
- Writes through L4/S6 outbox when offline app input is used.
- Publishes through L2/S4 event contract.
- Provides marker read model, marker events, and initial marker seed to L1/L6.

## Phase -1

- [x] L5-B01A 모의 파일 저장소와 사진 실패 고정 데이터 준비
  - 담당 Spec: S5
  - 필수 참조: `architecture.md §6.4`, `adr.md ADR-0035`, `spec/specs/S5.json`, `spec/harness-scenarios.md §6 mock object storage/upload URL fixture`
  - 연관 Spec: S6
  - 시나리오: SC-06
  - 구현 산출물: MinIO dev adapter smoke fixture, mock object storage, photo failure fixtures, object key fixture
  - 예상 작업량: 1d
  - 완료 기준: upload-url/attach API behavior를 구현하지 않은 상태에서 external S3 호출 없이 MinIO-compatible object key 규칙, mock object storage, photo failure fixture가 실행된다.

- [x] L5-B01B FCM 발송 모의체와 수신자 캡처 준비
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §6 mock FCM recipient`
  - 연관 Spec: S1-2, S4
  - 시나리오: SC-08
  - 구현 산출물: FCM dispatcher mock, recipient capture store, marker_notification fixture
  - 예상 작업량: 1d
  - 완료 기준: external FCM 호출 없이 mock notification dispatch가 recipient와 payload를 기록한다.

## Phase 0

- [x] L5-T03A 마커 위치와 OP 연결 실패 테스트 작성
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/boundaries.md §4.1.1`, `spec/specs/S8.json`
  - 연관 Spec: S2, S8
  - 시나리오: SC-06
  - 구현 산출물: marker geometry red tests, OP binding failure fixture, incident/op/police_phone/account context fixture
  - 예상 작업량: 1d
  - 완료 기준: domain validator 구현 전에 marker location과 OP binding 기대 조건이 failing test 또는 fixture로 고정된다.

- [x] L5-T05A 초기 기준 마커 주입 계약 fixture 작성
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/specs/S1-1.json`, `spec/specs/S7.json`
  - 연관 Spec: S1-1, S7, S3-2
  - 시나리오: SC-01, SC-03
  - 구현 산출물: `ReferenceMarkerSeed.createForIncident` mock contract, initial marker seed fixture, package inclusion red test
  - 예상 작업량: 0.5d
  - 완료 기준: S7이 marker write를 소유하지 않은 채 incident import와 offline package test가 initial marker fixture를 소비할 수 있다.

## Phase 1

- [x] L5-T03B 마커 위치와 OP 연결 검증 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/boundaries.md §4.1.1`, `spec/specs/S8.json`
  - 연관 Spec: S2, S8
  - 시나리오: SC-06
  - 구현 산출물: marker geometry validator, OP binding policy, incident/op/police_phone/account context tests
  - 예상 작업량: 1d
  - 완료 기준: marker location이 공통 geometry rule을 따르고 marker record가 기대 incident/op/police_phone/account context를 포함한다.

- [x] L5-T01A 마커 생성 API와 생성 이벤트 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §2 SC-06`, `spec/boundaries.md §10 SC-06`
  - 연관 Spec: S1-2, S2, S6, S8, S4
  - 시나리오: SC-06
  - 관련 FR: FR-10, FR-14, FR-15, FR-16, FR-30
  - 구현 산출물: `POST /api/markers`, marker create red tests, `MARKER_CREATED` publish request
  - 예상 작업량: 1d
  - 완료 기준: app-only marker 생성이 필수 필드를 저장하고 type/location/source를 검증하며 `MARKER_CREATED`를 발행한다.

- [ ] L5-T01B Android 마커 입력 계약 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §2 SC-06`, `spec/boundaries.md §10 SC-06`
  - 연관 Spec: S3-1, S6
  - 시나리오: SC-06
  - 관련 FR: FR-14, FR-15, FR-16, FR-30
  - 구현 산출물: Android bottom sheet input contract, manual location adjustment fixture, offline input contract test
  - 예상 작업량: 1d
  - 완료 기준: Android marker input이 필수 marker field를 수집하고 manual location adjustment를 지원하며, S6 replay behavior를 소유하지 않고 S5 marker creation으로 데이터를 전달할 수 있다.

- [x] L5-T05B 초기 기준 마커 데이터 주입 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/specs/S1-1.json`, `spec/specs/S7.json`
  - 연관 Spec: S1-1, S7, S3-2
  - 시나리오: SC-01, SC-03
  - 구현 산출물: `ReferenceMarkerSeed.createForIncident` port, initial marker seed persistence, package inclusion test
  - 예상 작업량: 0.5d
  - 완료 기준: S7이 marker write를 소유하지 않은 채 incident import가 seed marker를 만들고 offline package가 initial marker를 포함할 수 있다.

- [x] L5-T04A 사진 업로드 서명·완료 API 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §6 mock object storage/upload URL fixture`
  - 연관 Spec: S1-2, S6
  - 시나리오: SC-06
  - 관련 FR: FR-20
  - 구현 산출물: photo 업로드용 presigned URL 발급 API, photo attach API, object storage mock integration, `MARKER_UPDATED.photoDelta` PublishRequest contract test, orphan/mismatched photo rejection tests
  - 예상 작업량: 1d
  - 완료 기준: photo upload-url/attach가 presigned URL 기반 업로드 기대 상태를 지원하고, `MARKER_UPDATED.photoDelta`가 안정적인 photoId/status/version을 포함하며, orphan 또는 mismatched marker photo는 거부된다.

## Phase 2

- [x] L5-T02 마커 수정·삭제 정책 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/boundaries.md §4.6`, `spec/specs/S3-2.json`
  - 연관 Spec: S1-2, S4, S3-2
  - 시나리오: SC-06
  - 관련 FR: FR-10, FR-15, FR-16
  - 구현 산출물: marker update/delete APIs, `MARKER_UPDATED` and `MARKER_DELETED` PublishRequest contract tests, authorization/version/audit tests, S3-2 detail panel API contract
  - 예상 작업량: 1d
  - 완료 기준: 권한 있는 update/delete가 audit/version semantics를 보존하고, 안정적인 id/status/version/opId/policePhoneId를 가진 `MARKER_UPDATED` 또는 `MARKER_DELETED`를 발행하며, S3-2 marker detail panel은 domain policy를 소유하지 않고 S5 API를 호출한다.

- [x] L5-T08 상황판·OP 이력용 마커 조회 모델 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/specs/S3-2.json`, `spec/specs/S8.json`
  - 연관 Spec: S3-2, S8
  - 시나리오: SC-02, SC-06, SC-11
  - 관련 FR: FR-11, FR-15, FR-16
  - 구현 산출물: `MarkerQuery.byIncident`, marker read DTO, OP/context filter tests, board/OP evidence fixture
  - 예상 작업량: 1d
  - 완료 기준: marker query가 board와 OP evidence 소비자에게 id/status/version/opId/policePhoneId/type/location 필드를 일관되게 노출한다.

## Phase 3

- [x] L5-T04B 사진 업로드 중복·오프라인 재시도 검증 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/specs/S6.json`, `spec/harness-scenarios.md §6 mock object storage/upload URL fixture`
  - 연관 Spec: S6
  - 시나리오: SC-06, SC-09
  - 관련 FR: FR-20
  - 구현 산출물: duplicate photo attach tests, offline retry fixture, object key idempotency evidence
  - 예상 작업량: 1d
  - 완료 기준: duplicate attach와 offline retry가 duplicate marker photo 또는 orphan object reference를 만들지 않는다.

- [x] L5-T06A 지원 요청 마커 정책과 수신자 계산 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §2 SC-08`, `spec/harness-scenarios.md §6 mock event_dispatch_job`
  - 연관 Spec: S1-1, S1-2, S4, S6, S3-2
  - 시나리오: SC-08
  - 관련 FR: FR-17
  - 구현 산출물: support request marker policy, notification payload factory, recipient resolver, `SUPPORT_REQUEST_CREATED` PublishRequest contract test, duplicate-safe event flow test
  - 예상 작업량: 1d
  - 완료 기준: support request marker가 기대 notification payload와 recipient를 만들고, 안정적인 id/status/version/opId/policePhoneId를 가진 duplicate-safe `SUPPORT_REQUEST_CREATED`를 발행한다.

- [x] L5-T06B 지원 요청 FCM 발송과 상황판 알림 토스트 수렴 검증
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §2 SC-08`, `spec/harness-scenarios.md §6 mock FCM recipient`
  - 연관 Spec: S1-2, S4, S3-2
  - 시나리오: SC-08
  - 관련 FR: FR-17
  - 구현 산출물: mock FCM dispatch test, board toast fixture, marker_notification evidence
  - 예상 작업량: 1d
  - 완료 기준: support request notification dispatch가 mock FCM에 capture되고 기대 board toast evidence로 수렴한다.

- [x] L5-T07 실종자 발견 알림 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §2 SC-08`
  - 연관 Spec: S1-1, S1-2, S4, S3-2
  - 시나리오: SC-08
  - 관련 FR: FR-10
  - 구현 산출물: person-found marker policy, high-priority notification payload, `PERSON_FOUND` PublishRequest contract test, mock FCM dispatch test, unsupported external push guard
  - 예상 작업량: 1d
  - 완료 기준: person found marker가 high priority notification flow를 따르고, 안정적인 id/status/version/opId/policePhoneId를 가진 `PERSON_FOUND`를 발행하며, 지원하지 않는 external push infrastructure를 노출하지 않는다.

- [x] L5-T04C 사건 종료 후 마커·사진 쓰기 차단과 삭제 후크 계약 구현
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/specs/S1-3.json`, `spec/boundaries.md §4.5`, `spec/harness-scenarios.md §2 SC-12`
  - 연관 Spec: S1-1, S1-3, S4, S6, S3-2
  - 시나리오: SC-12
  - 지원 FR: FR-22 through marker/photo purge hook and closed write guard
  - 구현 산출물: marker/photo closed write guard tests, `MarkerPhotoPurgeHook` contract adapter, marker/photo purge idempotency tests
  - 예상 작업량: 1d
  - 완료 기준: closed incident marker/photo write가 row 또는 PublishRequest를 만들지 않고 `incident_closed`를 반환하며, S1-3가 idempotent marker/photo purge hook을 호출할 수 있다.

## Phase 4

- [x] L5-T09A SC-06 마커·사진 하네스 작성
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §2 SC-06`
  - 연관 Spec: S1-2, S2, S4, S6, S8, S3-2
  - 시나리오: SC-06
  - 구현 산출물: marker/photo harness runner, geometry/current OP/outbox/event mocks, marker/photo red tests, board marker API convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: marker/photo red test가 auth, geometry, current OP, outbox, event, board API assembly mock contract로 통과한다.

- [ ] L5-T09B SC-08 알림 하네스 작성
  - 담당 Spec: S5
  - 필수 참조: `spec/specs/S5.json`, `spec/harness-scenarios.md §2 SC-08`
  - 연관 Spec: S1-1, S1-2, S4, S6, S3-2
  - 시나리오: SC-08
  - 구현 산출물: notification harness runner, FCM recipient mocks, support/person-found red tests, board toast API convergence evidence
  - 예상 작업량: 1d
  - 완료 기준: support request와 person-found notification red test가 auth, event, FCM, board toast mock contract로 통과한다.

- [ ] L5-T09D SC-02 지원 배정 FCM evidence 하네스 작성
  - 담당 Spec: S5
  - 필수 참조: `spec/harness-scenarios.md §2 SC-02`, `spec/harness-scenarios.md §6 mock FCM dispatcher`, `spec/specs/S5.json`
  - 연관 Spec: S1-1, S1-2, S4
  - 시나리오: SC-02
  - 구현 산출물: `INCIDENT_ASSIGNMENT_CHANGED` 기반 112/mock support assignment FCM recipient fixture, `FcmDispatcher.send` mock capture test, PII 없는 payload/recipient/eventId/version failure injection test
  - 예상 작업량: 1d
  - 완료 기준: 112/mock 지원 배정 후 신규 배정 계정이 운용 중인 활성 policePhoneId로 가는 FCM payload/recipient/eventId/version이 mock dispatcher에 capture되고, 지휘 계정 policePhoneId 제외와 mock 미수신 실패 주입이 red test로 검증된다.

- [ ] L5-T09C 마커·사진·알림 시연 스모크 절차 작성
  - 담당 Spec: S5
  - 필수 참조: `prd.md §2.3`, `spec/harness-scenarios.md §2 SC-06`, `spec/harness-scenarios.md §2 SC-08`
  - 연관 Lane: L1, L2, L4, L6
  - 시나리오: SC-06, SC-08
  - 구현 산출물: marker/photo/notification demo smoke checklist, mock FCM/S3 evidence capture plan, board convergence capture target
  - 예상 작업량: 1d
  - 완료 기준: Phase 5 실행 전에 demo smoke procedure가 준비되며 final pass/fail evidence는 기록하지 않는다.

## Phase 5

- [ ] L5-D01 마커·사진·알림 시연 증거 수집
  - 담당 Spec: S5
  - 필수 참조: `prd.md §2.3`, `spec/harness-scenarios.md §2 SC-06`, `spec/harness-scenarios.md §2 SC-08`
  - 연관 Lane: L1, L2, L4, L6
  - 시나리오: SC-06, SC-08
  - 선행 task: L5-T09C
  - 구현 산출물: mock FCM/S3 evidence, board convergence screenshot or log, linked defect task list
  - 예상 작업량: 1d
  - 완료 기준: demo가 real external FCM 또는 S3 없이 marker를 생성하고, fixture에 따라 photo 첨부 또는 skip을 처리하며, support/person-found mock notification을 보내고 board toast/marker convergence를 보여준다.

## 담당하지 않음

- FCM infrastructure outside mock adapter boundary
- Event fanout orchestration
- Board shell or marker detail layout ownership
- OP lifecycle, area state, package manifest
- Location access logging or retention purge orchestration
