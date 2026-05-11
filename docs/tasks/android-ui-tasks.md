# Android UI Tasks · 폴리폰 Compose Screens

상태: 초안. Jira: `S14P31C106-211`. 담당 영역: Android UI.

이 문서는 ignored `TODO.md`에 있던 폴리폰 Android UI 실행 계획을 공식 task backlog로 정리한 것이다. Lane 도메인 task와 분리해서 Android 화면 구현만 관리한다.

## 작업 범위

- 폴리폰 Compose theme token, typography, dimension, shape 이식
- Android 공통 UI 컴포넌트와 화면 scaffold
- 관리 폴리폰 자동 확인, 사건 선택, 오프라인 패키지, 수색 지도, 마커 생성/상세, 인수인계, 알림, 처리 불가 큐 진단 UI
- Compose root overlay host: incident closed dialog, blocked queue toast, alert banner
- Android UI 테스트와 실기기 화면 검증

## 기준 문서

- `docs/prd.md`, `docs/architecture.md`
- `docs/api/api-spec.md`
- `docs/db-design/db-design-readable.md`
- `docs/spec/boundaries.md`
- `docs/spec/harness-scenarios.md`
- `docs/spec/specs/*.json`
- `docs/screen-design/wireframes.md`
- `docs/screen-design/dense-tokens.md`
- `docs/screen-design/screen-state-matrix.md`
- `docs/screen-design/permission-matrix.md`
- `docs/screen-design/anti-patterns.md`
- `docs/screen-design/artifacts/lo/*.html`

## 경계

- Android는 현장 입력 앱, Web은 지휘 상황판이다.
- 이 파일의 task는 Android UI 구현만 소유한다. API, DB entity, event, board slot, fixture ID를 새로 만들지 않는다.
- Domain write 의미는 기존 Lane task를 따른다.
  - 경로/Outbox/로컬 경고: L4 / S3-1 / S6
  - 마커/사진/알림: L5 / S5
  - OP/인수인계/AI 요약: L3 / S8
  - 인증/폴리폰/FCM token: L2 / S1-2 / S4
  - 오프라인 패키지/지도 tile: L6 / S7
- ID/PW 로그인, 바인딩 코드 입력, 정상 미전송 큐의 P4 직접 진입, 사건 종료 후 단독 정리 화면은 구현하지 않는다.
- AI 인수인계 요약 생성 요청은 P6-A 이전 근무 확인 화면의 책임으로만 둔다. P6-B 메모 작성 화면에는 AI 요약 생성 CTA를 두지 않는다.
- Knox/MDM 같은 운영 기술 용어는 화면 문구에 노출하지 않는다. 사용자 문구는 `관리 폴리폰`, `내부망`, `배정 사건`처럼 현장 언어로 유지한다.
- INCIDENT_CLOSED는 terminal이다. 모든 사건 컨텍스트 화면에서 P2 자동 이동 + 다이얼로그 + draft 폐기로 수렴한다.

## Task 분할 원칙

- `TODO.md`의 Phase A/B/C를 그대로 실행하지 않는다. Android 구현자가 MR 단위로 닫을 수 있도록 AUI task로 분해한다.
- 신규 의존성은 task별로 목적과 실패 모드를 확인한 뒤 추가한다. 이미 있는 Compose, Room, WorkManager, MapLibre, KSP는 재추가하지 않는다.
- `IncidentContext(incidentId, currentOpId, currentDutyShiftId)`는 Activity/Nav scope state holder에 둔다. DataStore에는 저장하지 않는다.
- `Idempotency-Key` 생성은 UI/Interceptor 책임이 아니다. Outbox/Repository가 persisted key를 만들고 UI는 상태만 표시한다.
- 정상 오프라인/동기화 진행은 sync chip으로만 표시한다. P4는 처리 불가 상태를 진단하는 화면이다.

## Phase 0 — Foundation

- [x] AUI-T01 폴리폰 Compose UI foundation과 핵심 화면 골격 구현
  - 담당 영역: Android UI
  - 필수 참조: `android/AGENTS.md`, `docs/screen-design/wireframes.md`, `docs/screen-design/dense-tokens.md`, `docs/screen-design/screen-state-matrix.md`, `docs/screen-design/permission-matrix.md`, `docs/screen-design/README.md`, `docs/screen-design/artifacts/lo/lo-polifon-wireframes-v1.html`, `docs/api/api-spec.md`, `docs/spec/boundaries.md`, `docs/spec/harness-scenarios.md`
  - 연관 Spec: S1-2, S3-1, S4, S5, S6, S7, S8
  - 시나리오: SC-01, SC-03, SC-05, SC-07, SC-09, SC-11, SC-12
  - 관련 FR: FR-03, FR-28, FR-29, FR-34, FR-37, FR-39
  - 구현 산출물:
    - Compose theme token: `Color.kt`, `Type.kt`, `Dimens.kt`, `Shape.kt`, `Theme.kt`
    - 공통 컴포넌트: `PoliButton`, `PoliChip`, `PoliBanner`, `PoliCard`, `PoliRow`, `PoliField`, `PoliProgress`, `PoliAppBar`, `PoliBrandMark`, `PoliDialog`, `PoliBottomSheet`, `PoliToast`
    - Compose mapping 문서: `docs/screen-design/compose-mapping.md`
    - 화면 골격: P1-B 관리 폴리폰 자동 확인, P2 사건 선택, P6-A 이전 근무 확인, P6-B 인수인계 메모 작성
    - 전역/상단 host: `AppOverlayHost`, `IncidentClosedDialog`, `BlockedQueueToast`, `HandoverPromptBanner`, `SyncChip`
    - fixture/mock state 기반 preview 또는 UI test
  - 구현 원칙:
    - Phase 0 범위에서는 신규 외부 의존성을 추가하지 않는다.
    - P6-A는 새 근무자가 이전 근무 정보를 확인하고 필요하면 AI 요약 생성을 요청하는 화면이다. P6-B 메모 작성과 섞지 않는다.
    - P4 미전송 진단은 처리 불가 toast 탭으로만 진입한다.
  - 예상 작업량: 2d
  - 완료 기준:
    - `cd android && ./gradlew :app:assembleDebug` 통과
    - 공통 컴포넌트와 4개 화면 골격이 low-fi HTML 구조와 screen-state-matrix의 핵심 상태를 반영한다.
    - Android UI에 Web 지휘 기능, ID/PW 로그인, 메모 작성 중 요약 생성 CTA, 사건 종료 실행 UI, 정상 미전송 큐 직접 진입 UI가 없다.
    - MR 변경 범위는 원칙적으로 `android/`, `docs/screen-design/`, `docs/tasks/android-ui-tasks.md`로 제한한다.
  - 완료 증거: Jira `S14P31C106-211`, MR `!151`, `cd android && ./gradlew :app:assembleDebug` 통과

## Phase 1 — App Shell과 의존성 결정

- [x] AUI-T02 Navigation, scaffold, app state holder 골격을 붙인다
  - 담당 영역: Android UI
  - 필수 참조: `docs/tasks/index.md`, `docs/spec/boundaries.md`, `docs/screen-design/screen-state-matrix.md`
  - 구현 산출물:
    - 단일 Compose entry와 NavHost route 정의
    - route: `auth_bootstrap`, `incident_list`, `offline_package`, `search_map`, `handover_summary`, `handover_memo`, `marker_detail`, `blocked_outbox`
    - Compose root `AppOverlayHost`를 NavHost 위에 배치
    - Activity/Nav scope `IncidentSessionState` 또는 `IncidentSessionViewModel`
    - DataStore에는 `last_seen_handover_at`, `last_known_manifest_revision`, managed config snapshot hash 같은 작은 preference만 저장
  - 명시 결정:
    - Navigation Compose 도입: `androidx.navigation:navigation-compose:2.9.8` 사용. Google Maven metadata 기준 stable 최신 2.9.8을 선택하고 alpha release는 배제한다.
    - 수동 provider/state holder 유지. Hilt는 ViewModel/Repository 주입 복잡도가 생길 때까지 보류한다.
    - root overlay event 전달은 AUI-T02 범위에서 Compose root state holder로 처리한다. `SharedFlow`/EventBus는 실제 WorkManager·FCM 이벤트 연결 task에서 필요성이 확인되면 도입한다.
  - 완료 기준:
    - 8개 route가 mock state로 이동 가능하다.
    - 사건 컨텍스트가 DataStore에 저장되지 않는다.
    - `cd android && ./gradlew :app:assembleDebug` 통과
  - 완료 증거: Jira `S14P31C106-217`, branch `feature/S14P31C106-217-police-phone-nav-scaffold-state-holder`, RED/GREEN `./gradlew :app:testDebugUnitTest --tests com.surimap.ui.navigation.PolicePhoneNavigationContractTest`, `cd android && ./gradlew :app:assembleDebug`, `cd android && ./gradlew test`

- [ ] AUI-T03 신규 의존성 도입 여부를 task별로 결정한다
  - 담당 영역: Android UI / Android infra decision
  - 필수 참조: `android/gradle/libs.versions.toml`, `docs/api/api-spec.md`, `docs/spec/specs/S1-2.json`, `docs/spec/specs/S4.json`, `docs/spec/specs/S8.json`
  - 검토 대상:
    - Navigation Compose
    - DataStore Preferences
    - Retrofit + OkHttp + kotlinx-serialization 또는 Moshi
    - Firebase Messaging + google-services plugin 또는 no-op provider/flavor 전략
    - Hilt
    - CameraX
    - Turbine + MockWebServer
    - Roborazzi
    - lucide-compose
  - 보류 기본값:
    - Hilt, Firebase Messaging, CameraX, Roborazzi, lucide-compose는 목적과 실패 모드가 명확해질 때까지 보류한다.
    - FCM은 `google-services.json` 없이 assembleDebug가 깨지지 않는 no-op provider 또는 flavor 전략을 먼저 정한다.
  - 완료 기준:
    - 각 의존성의 `도입 / 보류 / 대체` 결론이 MR에 남는다.
    - 신규 의존성 추가가 있으면 즉시 `cd android && ./gradlew :app:assembleDebug`가 통과한다.

## Phase 2 — 인증 진입과 사건 선택

- [ ] AUI-T04 관리 폴리폰 자동 확인 화면을 실제 app state와 연결한다
  - 담당 영역: Android UI
  - 연관 Spec: S1-2, S4
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/boundaries.md`, `docs/spec/specs/S1-2.json`, `docs/screen-design/permission-matrix.md`
  - 구현 산출물:
    - P1-B 관리 폴리폰 자동 확인 UI 상태 연결
    - 실패 variant: `not_managed_phone`, `internal_network_unavailable`, `server_rejected_phone`, `managed_config_missing`
    - managed configuration read adapter
    - 내부망/API base URL 상태 표시
    - 성공 시 P2 자동 이동
  - 구현 원칙:
    - ID/PW 로그인, 바인딩 코드 입력, Knox 용어 노출 UI를 만들지 않는다.
    - 앱은 OS/EMM의 등록·정책·앱 설정 주입을 중복 구현하지 않는다.
  - 완료 기준:
    - 성공/실패 4분기가 화면에서 재현된다.
    - 실패 화면은 P2/P5로 진입시키지 않는다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

- [ ] AUI-T05 배정 사건 선택 화면을 실제 리스트 상태와 연결한다
  - 담당 영역: Android UI
  - 연관 Spec: S1-2, S4, S8
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/harness-scenarios.md`, `docs/screen-design/screen-state-matrix.md`
  - 구현 산출물:
    - P2 상태: default, empty, loading, error, offline, stale
    - 활성 사건 카드: 사건명, 실종자 요약, 상태, 마지막 활동 시각, 동기화 상태
    - pull-to-refresh 또는 명확한 새로고침 액션
    - 사건 카드 선택 시 `IncidentContext` set 후 P3 또는 P5로 이동
    - INCIDENT_CLOSED 수신 시 root dialog + P2 복귀
  - 구현 원칙:
    - 사건을 앱에서 생성하지 않는다.
    - 배정 사건 외 사건 검색/가져오기 UI를 만들지 않는다.
  - 완료 기준:
    - empty가 기본 가능 상태로 표현된다.
    - `police_phone_not_assigned` 또는 사건 종료 상태에서 사건 컨텍스트가 비워진다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

## Phase 3 — 오프라인 패키지와 수색 지도

- [ ] AUI-T06 오프라인 패키지 다운로드 화면을 구현한다
  - 담당 영역: Android UI
  - 연관 Spec: S3-2, S7
  - 시나리오: SC-03, SC-11
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/harness-scenarios.md`, `docs/screen-design/screen-state-matrix.md`
  - 구현 산출물:
    - P3 manifest revision 비교 상태
    - 다운로드 sequence: 사건 메타, 실종자, OP, 구역, 마커, 전체 수색 구역, 타일
    - 항목별 progress와 자동 재시도 chip
    - 부분 성공 시 `제한 안내 후 열기` CTA
    - 완료 시 P5 자동 이동
  - 구현 원칙:
    - 자동 재시도 가능한 상태에 수동 재시도 CTA를 먼저 노출하지 않는다.
    - 패키지 설치 진행 API payload를 임의 확장하지 않는다.
  - 완료 기준:
    - 100% + `readyForOfflineUse=true`에서 P5로 이동한다.
    - 부분 성공 상태가 지도 진입 위험을 명시한다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

- [ ] AUI-T07 수색 지도 shell과 동기화 상태 UI를 구현한다
  - 담당 영역: Android UI
  - 연관 Spec: S2, S3-1, S6, S7, S8
  - 시나리오: SC-05, SC-07, SC-09, SC-11
  - 필수 참조: `docs/db-design/db-design-readable.md`, `docs/api/api-spec.md`, `docs/spec/specs/S3-1.json`, `docs/spec/specs/S6.json`, `docs/screen-design/artifacts/lo/lo-polifon-search-map-v1.html`
  - 구현 산출물:
    - P5 MapLibre 또는 mock map shell
    - 사건 컨텍스트 헤더: 실종자 요약, OP 차수, DutyShift 시간
    - 전체/부대/팀 수색 구역 layer 표현
    - sync chip: Idle, Offline, Sending, Synced
    - 수색 lifecycle: start, pause, resume, stop
    - `op_required`, `op_transition`, `search_paused`, `search_stopped` 상태
    - P6-A 진입용 `HandoverPromptBanner`
  - 구현 원칙:
    - OP 누락/만료/조회 실패에서는 marker/path write를 차단한다.
    - 정상 미전송 큐는 화면 이동이 아니라 sync chip 상태로만 보인다.
  - 완료 기준:
    - OP 없음/전환/일시정지/종료 상태가 지도 write 가능 여부와 함께 드러난다.
    - P5에서 P6-A banner 진입이 가능하다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

## Phase 4 — 마커와 사진

- [ ] AUI-T08 마커 생성 bottom sheet를 구현한다
  - 담당 영역: Android UI
  - 연관 Spec: S5, S6
  - 시나리오: SC-06, SC-08
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/specs/S5.json`, `docs/screen-design/artifacts/lo/lo-polifon-marker-bottomsheet-v1.html`
  - 구현 산출물:
    - 유형 탭: `CLUE`, `PERSON_FOUND`, `FIELD_CONDITION`, `SUPPORT_REQUEST`, `NOTE`
    - 최소 마커 즉시 생성 UI
    - 추가 메모/사진 첨부 UI
    - offline outbox pending 상태
    - 사진 첨부 progress 상태
  - 구현 원칙:
    - 마커 payload 이름과 marker type은 S5/API 기준을 그대로 쓴다.
    - 사진 첨부는 presigned URL for upload -> object storage PUT -> attach finalize 흐름과 충돌하지 않는다.
  - 완료 기준:
    - 5개 마커 유형이 모두 화면에서 선택 가능하다.
    - offline 상태에서 pending 표시가 P4 직접 진입으로 바뀌지 않는다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

- [ ] AUI-T09 마커 상세/편집 화면을 구현한다
  - 담당 영역: Android UI
  - 연관 Spec: S5
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/specs/S5.json`, `docs/screen-design/artifacts/lo/lo-polifon-marker-detail-v1.html`
  - 구현 산출물:
    - P9 marker detail
    - 자기 계정 생성 마커 edit/delete 가능 상태
    - 타 계정 마커 readonly 상태
    - 명시적 삭제 버튼 + confirm dialog
    - 사진 첨부/삭제 progress UI
  - 구현 원칙:
    - long-press 단독 삭제/수정 confirm을 만들지 않는다.
    - 권한 source는 `SecurityContext.accountId`와 marker 생성자 비교 결과를 소비한다.
  - 완료 기준:
    - readonly 상태에서는 수정/삭제 버튼이 숨겨진다.
    - 삭제는 명시 버튼과 confirm dialog를 모두 거친다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

## Phase 5 — 인수인계와 처리 불가 큐

- [ ] AUI-T10 이전 근무 확인과 인수인계 메모 화면을 도메인 상태와 연결한다
  - 담당 영역: Android UI
  - 연관 Spec: S8, S6
  - 시나리오: SC-10, SC-11
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/specs/S8.json`, `docs/screen-design/artifacts/lo/lo-polifon-duty-handover-v1.html`, `docs/screen-design/artifacts/lo/lo-polifon-handover-memo-v1.html`
  - 구현 산출물:
    - P6-A 이전 근무 확인: AI 요약 생성/상태 카드, 원본 경로/마커/메모 목록, `자동 처리 중`, `요약 생성 필요`, `원본 확인`, `이전 기록 없음`, `summary_unavailable`
    - P6-B 메모 작성: 대상 탭(OP/경로/구역/근무/마커), 메모 입력, 저장
    - offline 저장 시 outbox pending 상태
    - HandoverPromptBanner 표시 조건: 서버 `current_duty_shift.started_at` > client `last_seen_handover_at`
  - 구현 원칙:
    - AI 요약 생성 요청 CTA는 P6-A에만 둔다. P6-B 메모 작성 화면에는 두지 않는다.
    - Android는 on-device AI를 수행하지 않는다. S8/API 계약의 생성 요청 endpoint 또는 상태 조회 endpoint를 소비하고, 없는 endpoint를 임의로 만들지 않는다.
  - 완료 기준:
    - 새 근무자가 이전 근무 정보를 확인하는 흐름과 새 메모를 남기는 흐름이 분리된다.
    - P6-B 저장 후 P5 또는 P6-A로 복귀하고 toast가 표시된다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

- [ ] AUI-T11 처리 불가 미전송 진단 화면을 구현한다
  - 담당 영역: Android UI
  - 연관 Spec: S6, S4
  - 시나리오: SC-07, SC-09, SC-12
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/specs/S6.json`, `docs/screen-design/artifacts/lo/lo-polifon-outbox-v1.html`, `docs/screen-design/artifacts/lo/lo-polifon-local-warning-v1.html`
  - 구현 산출물:
    - P4 blocked outbox diagnostic
    - 처리 불가 그룹 상단 강조
    - 사유별 안내: `incident_closed`, `police_phone_not_assigned`, retry exhausted, payload validation failure
    - 자동 처리 대기는 한 줄 축약
    - IT 부서 문의 CTA 또는 운영 안내
    - `BlockedQueueToast` 탭으로만 진입
  - 구현 원칙:
    - 정상 offline pending/replaying queue를 사용자에게 관리 목록으로 노출하지 않는다.
    - WorkManager 재시도 정책을 UI 버튼으로 대체하지 않는다.
  - 완료 기준:
    - 처리 불가 사유가 명확하게 구분된다.
    - 정상 큐 상태에서는 P4 진입 CTA가 노출되지 않는다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

## Phase 6 — 알림과 terminal state

- [ ] AUI-T12 강조 알림 UI와 FCM 라우팅 shell을 구현한다
  - 담당 영역: Android UI
  - 연관 Spec: S4, S5
  - 시나리오: SC-08, SC-12
  - 필수 참조: `docs/api/api-spec.md`, `docs/spec/specs/S4.json`, `docs/spec/specs/S5.json`, `docs/screen-design/artifacts/lo/lo-polifon-incident-alert-v1.html`
  - 구현 산출물:
    - P8 in-app banner
    - `PERSON_FOUND`, `SUPPORT_REQUEST` variant
    - action: `확인`, `지도 열기`
    - `focusMarkerId` 기반 P5 deeplink
    - FCM payload route shell: `INCIDENT_CLOSED`, `PERSON_FOUND`, `SUPPORT_REQUEST`
  - 구현 원칙:
    - Android FSI, snooze, battery exemption은 운영 정책 확인 전 보류한다.
    - 자기 발신 케이스 필터는 서버 책임이지만, 클라이언트 부수 가드는 가능하다.
  - 완료 기준:
    - 알림에서 P5 marker focus로 이동한다.
    - INCIDENT_CLOSED는 alert UI가 아니라 root terminal dialog로 처리된다.
    - `cd android && ./gradlew :app:assembleDebug` 통과

## Phase 7 — Test와 실기기 검증

- [ ] AUI-T13 Android UI test와 ViewModel test를 추가한다
  - 담당 영역: Android test
  - 필수 참조: `docs/spec/harness-scenarios.md`, `docs/spec/fixtures/`, `docs/tasks/review-guide.md`
  - 구현 산출물:
    - 공통 컴포넌트 variant UI test 또는 snapshot test
    - ViewModel 상태 전이 test
    - Room + WorkManager + HTTP mock 통합 test
    - fixture JSON test resource 연결
    - SC-03/05/06/07/08/09/10/11/12 중 Android UI가 닫아야 하는 1단계 test
  - 완료 기준:
    - `cd android && ./gradlew :app:assembleDebug`
    - 테스트 추가 범위에 맞춰 `cd android && ./gradlew test` 또는 `:app:testDebugUnitTest` 통과

- [ ] AUI-T14 연결된 Android 실기기에서 화면 검증을 수행한다
  - 담당 영역: Android QA
  - 필수 참조: `docs/screen-design/measurement-gates.md`, `docs/screen-design/anti-patterns.md`, `docs/prd.md`
  - 구현 산출물:
    - 갤럭시 폴리폰 또는 연결 단말 debug APK 설치
    - P1-B/P2/P3/P5/P6/P8/P9 주요 화면 screenshot 또는 기록
    - 외부 도메인 차단 환경에서 리소스 fallback 확인
    - 통신 on/off 반복 후 sync chip과 blocked outbox 상태 확인
  - 완료 기준:
    - `adb devices`에서 대상 단말 확인
    - `cd android && ./gradlew :app:installDebug` 성공
    - 화면 겹침, 잘림, 터치 타깃 미달, long-press 단독 confirm 0건 확인

## 보류 / 별도 기획 필요

- high-fi 지도 라벨 halo / MapLibre layer paint 정교화
- v2 음성 입력
- v2 handedness 토글
- v2 개인 단위 식별: Knox Authentication Manager PIN/Face 또는 Knox SSO SDK
- Android FSI 사용 여부, 배터리 최적화 예외, Kiosk/Lock Task Mode 허용 범위
- 인증·세션 정책 본문 문서 갱신: PRD/API/ADR 별도 작업
- Android에서 AI 인수인계 요약 생성을 요청하는 방향은 기획 문서 갱신 완료를 전제로 한다. 구현 전 S8/API 계약에 생성 요청/상태 조회 endpoint가 있는지 확인한다.

## 참고한 외부 운영 사실

- Samsung Knox Mobile Enrollment: `https://docs.samsungknox.com/admin/knox-mobile-enrollment/how-to-guides/enroll-devices/complete-device-enrollment/`
- Samsung Knox Manage authentication method: `https://docs.samsungknox.com/admin/knox-manage/configure/basic-setup/set-the-user-authentication-method/`
- Samsung Knox Authentication Manager overview: `https://docs.samsungknox.com/admin/knox-authentication-manager/overview/`
- Samsung Knox Configure shared device: `https://docs.samsungknox.com/admin/knox-configure/how-to-guides/profiles/about-shared-device/`
- Samsung Knox Firewall: `https://docs.samsungknox.com/admin/fundamentals/whitepaper/samsung-knox-mobile-security/network-security/knox-firewall/`
- Android managed configurations: `https://developer.android.com/work/managed-configurations`
- 경찰청 PS-LTE 러기드 스마트폰 사례: `https://www.samsung.com/sec/business/insights/case-study/reference-police-agency/`
