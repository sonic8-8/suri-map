# Android Agent Rules

Suri-Map Android 현장 앱 전용 규칙이다. 저장소 공통 규칙은 `../AGENTS.md`를 먼저 따른다.

## 확인 위치

| 확인할 내용 | 위치 |
|---|---|
| 의존성·버전·빌드 | `gradle/libs.versions.toml`, `app/build.gradle.kts` |
| 로컬 저장·동기화 | `app/src/main/java/com/surimap/core/database/`, `app/src/main/java/com/surimap/core/sync/` |
| 위치 수집·경로 기록 | `app/src/main/java/com/surimap/core/location/`, `app/src/main/java/com/surimap/feature/search/data/` |
| 서버와의 계약 | 실제 API 요청·응답 변환 코드, Backend Controller·DTO와 관련 테스트 |

## 프로젝트 / 플랫폼 개요

- Android 앱은 현장 입력 채널이다.
- 앱 전용 write: SearchPath 시작/종료, GPS batch, 현장 마커 생성, 사진 첨부, offline package installation status, heartbeat, FCM token 등록.
- 앱+웹 공통: 사건 조회, 마커 조회/수정/삭제, 인수인계 메모 작성.
- 웹 전용 command인 사건 import/close, 전체 수색 구역 조정, 구역 완료, OP 생성, 차량/도보 보정을 앱에서 구현하지 않는다.
- 자동 누락 판단, 다음 수색 구역 추천, 위험도 판단을 앱에서 만들지 않는다.

## 구조 / 아키텍처

Google 권장 Android 아키텍처 기반의 경량 계층을 사용한다.

```text
app/src/main/java/com/surimap/
├── core/
│   ├── database/
│   ├── network/
│   ├── sync/
│   ├── location/
│   ├── map/
│   └── model/
├── feature/
│   └── {feature}/
│       ├── ui/
│       ├── data/
│       └── domain/   # 복잡하거나 재사용될 때만
└── ui/
    └── theme/
```

- UI layer와 data layer는 기본으로 둔다.
- domain/usecase layer는 offline sync, location recording, package installation처럼 복잡하거나 여러 ViewModel에서 재사용될 때만 둔다.
- ViewModel에서 Room DAO, network client, WorkManager, MapLibre SDK를 직접 호출하지 않는다.
- Repository는 local/remote data source를 조합하고, UI state는 ViewModel/state holder가 만든다.

## 코드 스타일 / 컨벤션

- Package는 `core`, `feature/{feature}`, `ui/theme` 기준으로 둔다.
- Compose는 state hoisting을 기본으로 하고, business rule을 composable에 넣지 않는다.
- ViewModel은 UI state와 사용자 event orchestration만 담당한다.
- Repository는 DataSource를 조합하고, DataSource는 Room/API/SDK 세부 구현만 담당한다.
- Room entity는 `...Entity`, DAO는 `...Dao`로 이름 짓는다.
- WorkManager worker는 `...Worker`로 이름 짓고, retry 조건과 input/output data를 명시한다.
- 실패 처리는 boolean 대신 sealed result 또는 명시적 state로 모델링한다.
- Coroutine은 structured concurrency를 지키고, 관찰 stream은 `Flow`로 노출한다.

## Offline-first

- 앱 화면의 데이터 기준은 Room/local DB로 둔다.
- 현장 write는 로컬 저장을 먼저 하고 Outbox replay로 서버 동기화한다.
- 서버 write를 재시도할 때 같은 요청의 idempotency key를 보존한다.
- 서버 전송 실패를 로컬 기록 실패로 취급하지 않는다.
- 서버 ack 전 로컬 원본을 삭제하지 않는다.
- WorkManager는 네트워크 복구, retry, purge cleanup을 담당한다.
- Partial success를 표현하고 성공 항목만 ack 처리한다.
- 사건 종료 후에는 새 flush/requeue를 시작하지 않고, acked 항목만 purge 대상으로 삼는다.

Outbox 상태와 전이 조건은 `app/src/main/java/com/surimap/core/sync/OutboxStateMachine.kt`와 관련 테스트에서 확인한다.

## PolicePhone / Path

- 경로 기록 주체는 로그인한 `accountId`다.
- `PolicePhone`은 앱 단말 인증, 배정 guard, outbox 전송 컨텍스트로 유지한다.
- 모든 현장 write는 현재 `incidentId`, `opId`, `accountId`, `policePhoneId`, 필요 시 `dutyShiftId`에 귀속된다.
- SearchPath는 수색 시작부터 종료까지의 경로 단위다. 기록 종료 후 다시 시작하면 새 SearchPath다.
- GPS 수집 주기, 좌표 묶음 조건, HTTP 전송 간격을 구분한다. 각각 `LocationRecorder.kt`, `SearchPathGpsBatchRecorder.kt`, `OutboxWorker.kt`와 호출·설정 경로에서 확인한다. 묶음 조건을 고정 전송 간격으로 해석하거나 부하테스트 가정을 제품 설정으로 취급하지 않는다.
- 차량/도보 자동 분류 결과는 표시할 수 있지만, 수동 보정 command는 웹 전용이다.

## Marker / Photo

- 현장 마커 생성은 앱 전용이다.
- Marker type은 서버·앱이 함께 사용하는 값을 보존한다: `CLUE`, `PERSON_FOUND`, `FIELD_CONDITION`, `SUPPORT_REQUEST`, `NOTE`.
- 지원 요청 type은 `DRONE`, `POLICE_DOG`, `OTHER`만 사용한다.
- 사진은 마커 생성/첨부 흐름에서만 다룬다. 업로드 성공 전 로컬 원본을 삭제하지 않는다.
- 사진 제한은 마커당 10장, 파일당 10MB 기준이다.

## Package / Map

- 오프라인 패키지는 타일만이 아니라 사건 메타, 실종자 정보, OP, 담당 구역, 초기 마커, 전체 수색 구역, 타일을 포함한다.
- Manifest revision과 installation status를 구분한다.
- `search_area.area_level = OVERALL`은 package 범위와 초기 viewport 기준이다.
- MapLibre attribution을 숨기지 않는다.
- 지도 실패가 SearchPath/Marker/Outbox 기록을 막지 않게 분리한다.

## Compose / State

- Compose UI는 state를 렌더링하고 event를 ViewModel로 올린다.
- UI 상태는 `saving`, `pendingSync`, `syncing`, `synced`, `retryableError`, `finalError`를 구분한다.
- GPS 중단, 배터리 저하, 지도 미다운로드, Outbox 적체는 서버 없이도 로컬 경고로 표시한다.

## Error

- 서버 error code는 실제 오류 응답과 앱의 변환·재시도 처리 코드를 대조하고, 변경 시 양쪽 호환성을 검증한다.
- `police_phone_required`, `police_phone_not_registered`, `police_phone_not_assigned`, `incident_closed`, `op_mismatch`, `idempotency_mismatch`는 재시도 가능 여부를 구분한다.
- 문자열 error를 UI에 직접 흘리지 말고 domain/UI state로 변환한다.

## 테스트 지침

- Unit test는 Android framework 없이 가능한 domain/usecase/mapper부터 작성한다.
- Room, Outbox state transition, WorkManager retry, partial success, ack-before-delete를 우선 검증한다.
- fixture ID와 API path는 실제 사용처를 확인하며 승인 없이 바꾸지 않는다. 테스트가 읽는 문서·JSON 파일을 설명 자료로 오인해 삭제하지 않는다.
