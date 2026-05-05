# Suri Map Android Agent Spec

> 목적: 사람이 길게 읽는 설계서가 아니라, agent가 Android 앱 코드를 작성하기 전에 빠르게 확인하는 현재 기준 압축 규칙집
> 기준 문서: `prd.md`, `architecture.md`, `adr.md`, `boundaries.md`, `harness-scenarios.md`
> 범위: Suri-Map Android 현장 앱
> 업데이트: 2026-04-30

---

## 1. 기본 전제

- 이 문서는 **Android 앱 전용**이다.
- 기획에서 말하는 `app`은 **현장 폴리폰에 설치되는 Android 앱**이다.
- Android 언어는 **Kotlin**을 사용한다.
- 아키텍처 패턴은 **Clean Architecture**를 적용한다.
- 앱의 본질은 현장 입력 클라이언트다. 웹 상황판의 축소판이 아니다.
- 핵심 목표는 예쁜 UI보다 **기록 유실 방지, 오프라인 지속성, 복구 동기화, 채널 경계 준수**다.

---

## 2. 제품 고정 규칙

### 앱이 해야 하는 일

- 사건 조회
- 실종자 기본 정보 조회
- 현재 active OP 기준 수색 세션 시작/일시정지/재개/종료
- GPS 5초 수집, 10초 배치 전송
- Device 기준 경로 기록
- 운용 중인 현재 Device 궤도 강조 표시
- 현장 마커 생성
- 마커 조회/수정/삭제(권한 범위)
- OP/경로/구역 단위 인수인계 메모 작성
- 사진 첨부
- 현장 마커 목록 바텀시트 제공
- 오프라인 패키지 다운로드 및 상태 보고
- 미전송 큐 상태 표시
- GPS 중단, 배터리 저하, 지도 미다운로드 로컬 경고 표시
- 단순 지도 보기 모드 제공
- FCM 기반 지원 요청/실종자 발견 알림 수신

### 앱이 하면 안 되는 일

- 구역 완료 처리
- 지도 기준 범위 수정
- 새 OP 생성
- 차량/도보 구간 수동 보정
- 자동 사각지대 판단
- 다음 수색 구역 추천
- 위험도 자동 판단

### 채널 경계

- 앱 전용: 수색 세션, GPS 경로 생성/수집/write, 현장 마커 생성/write, 사진 첨부, 패키지 상태 보고, heartbeat
- 웹 전용: 사건 가져오기/종료, 지원 부대 배정, 지휘관 지정, 지도 기준 범위, 구역 분할/완료, OP 생성, 차량/도보 보정
- 앱+웹 공통: 사건 조회, GPS 경로 조회/표시, 마커 조회/수정/삭제(권한 범위), OP/경로/구역 인수인계 메모 작성

---

## 3. 핵심 도메인 규칙

- 경로 주체는 개인이 아니라 `Device`다.
- `Device`는 `TEAM` 또는 `PATROL_CAR` 성격의 업무폰이다.
- 조작 주체는 개인이 아니라 로그인한 `팀 계정 / 순찰차 계정 / 지휘 계정`이다.
- 모든 기록은 **현재 `incident + active OP + device + searchSession`** 기준으로 귀속된다.
- 경로, 마커, 구역 상태, 메모, AI 요약은 OP 기준으로 묶인다.
- 사건은 사용자가 앱에서 직접 생성하지 않는다. mock·seed 배정 사건을 가져온 결과를 소비한다.
- 앱은 현장 기록의 출발점이며, 서버 미도달 상황에서도 기록을 계속 유지해야 한다.

---

## 4. Clean Architecture 원칙

### 의존 방향

```text
presentation  →  domain  ←  data
                         ↑
               framework / sdk adapters
```

### 핵심 규칙

- `domain`은 순수 Kotlin이어야 한다.
- `domain`은 Android SDK, Room, Retrofit/OkHttp, WorkManager, FCM, MapLibre 타입을 알면 안 된다.
- `presentation`은 Android UI 상태와 사용자 이벤트를 다룬다.
- `data`는 API, DB, 파일, FCM 토큰, 지도 SDK, 위치 SDK 같은 외부 구현을 다룬다.
- Repository interface는 `domain`, 구현체는 `data`에 둔다.
- Android framework 의존 코드는 가능한 한 adapter 계층 바깥으로 밀어낸다.

### Clean Architecture에서 특히 중요한 점

- 이 프로젝트에서 핵심은 폴더 이름이 아니라 **의존 방향**이다.
- ViewModel이 Room, Retrofit, Event/FCM, Outbox SQL을 직접 만지기 시작하면 설계가 깨진다.
- UseCase는 UI와 분리되고, Android가 없어도 테스트 가능해야 한다.

---

## 5. 권장 구조

### 전체 구조 예시

```text
app/
core/
features/
```

### core 예시

```text
core/
├── auth/
├── database/
├── network/
├── sync/
├── map/
├── location/
├── notification/
├── files/
└── common/
```

### feature 내부 구조 예시

```text
features/{feature}/
├── domain/
│   ├── model/
│   ├── repository/
│   └── usecase/
├── data/
│   ├── local/
│   ├── remote/
│   ├── mapper/
│   └── repository/
└── presentation/
    ├── ui/
    ├── state/
    ├── intent/
    └── viewmodel/
```

### Android에서 중요하게 다룰 feature 예시

```text
features/
├── incidents/
├── auth/
├── searchSession/
├── devicePath/
├── markers/
├── offlineSync/
├── incidentPackage/
├── handover/
└── alerts/
```

---

## 6. 레이어별 책임

### presentation

- 화면 렌더링
- 사용자 입력 처리
- ViewModel state 관리
- loading / success / failure / pending sync 상태 노출
- domain UseCase 호출

금지:

- Room DAO 직접 호출
- Retrofit API 직접 호출
- SQL/JSON/DTO 직접 해석
- WorkManager enqueue 직접 남발

### domain

- 수색 세션 상태 전이
- 마커 생성 규칙
- active OP 검증
- Device/incident/assignment 전제 검증
- 오프라인/동기화 상태 모델링
- 순수 계산과 에러 규칙

금지:

- `Context`
- `Location`
- `Cursor`
- `RoomEntity`
- `ResponseBody`
- `Bitmap`

### data

- REST API 호출
- Room 저장
- mapper
- repository 구현
- presigned URL 업로드 처리
- local file 임시 보관
- FCM token 등록
- Outbox replay 재료 제공

### framework / sdk adapter

- foreground location service
- WorkManager worker
- MapLibre adapter
- FCM service
- file storage
- battery / connectivity / permission observer

---

## 7. Android 앱 핵심 책임 모델

### 앱의 도메인 책임

- `searchSession`
- `devicePath`
- `marker`
- `photo upload staging`
- `offline package`
- `outbox replay`
- `local warnings`

### 앱이 소비만 하는 책임

- 사건 import 결과
- membership / 권한
- map boundary
- search area 배정
- OP 전환 결과
- server-side AI summary 결과

앱은 지휘 시스템이 아니라 현장 기록 시스템이라는 점을 코드 구조에 반영해야 한다.

---

## 8. 오프라인 우선 규칙

### 기본 스택

- `Room/SQLite`
- `Outbox Pattern`
- `WorkManager`
- `Idempotency Key`

### 저장 원칙

1. 사용자 조작 또는 GPS 이벤트 발생
2. 먼저 로컬 DB 저장
3. Outbox row 생성
4. 네트워크 가능 시 재전송
5. 서버 ack 후 상태 갱신
6. 필요한 경우에만 로컬 정리

### 핵심 규칙

- 서버 저장 실패는 곧 기록 실패가 아니다.
- 앱에서 중요한 write는 `pending`, `sending`, `acked`, `failed_retryable`, `failed_final` 같은 상태를 가져야 한다.
- 로컬 저장보다 서버 저장을 먼저 시도하는 구조를 만들지 않는다.
- 일부 항목만 성공한 partial success를 모델링해야 한다.
- ack 없는 삭제는 금지한다.

### Outbox 상태 예시

- `PENDING`
- `SENDING`
- `ACKED`
- `FAILED_RETRYABLE`
- `FAILED_FINAL`
- `PURGED`

---

## 9. 수색 세션 / GPS / 경로 규칙

### 세션 규칙

- 세션은 `incident + op + device`에 연결된다.
- 세션 상태는 `진행 중 / 일시정지 / 종료`를 명시적으로 가진다.
- `일시정지` 구간은 경로 선으로 이어붙이지 않는다.
- 같은 Device를 다음 근무자가 이어 써도 OP·세션 단위로 구분한다.

### GPS 규칙

- 수집 주기: 5초
- 전송 주기: 10초 배치
- 상시 소켓 연결 금지
- 경로는 Device 기준으로 기록
- 자동 분리는 GPS 속도 기반 `vehicle / foot / unknown`

### 품질 규칙

- 저품질 point는 정상 경로 point로 바로 승격하지 않는다.
- 품질 저하 point는 별도 상태 또는 제외 상태로 관리한다.
- 하네스 기준의 좌표/속도/정확도 조건을 깨는 입력은 검증해야 한다.

### 웹과의 정합성

- 웹은 차량/도보 구간을 수동 보정할 수 있다.
- 앱은 서버가 확정한 구간 상태를 다시 읽을 수 있어야 한다.
- 앱이 자체적으로 웹 전용 보정 로직을 만들면 안 된다.

---

## 10. 마커 / 사진 규칙

### 마커 규칙

- 일반 현장 마커 생성은 앱 전용
- 마커는 현재 active OP에 귀속
- 마커는 `incidentId`, `opId`, `accountId`, `deviceId`, `clientTs`, `location`, `type`, `memo`, `photos` 기준으로 다룬다
- 앱은 현장 생성 이후의 마커 조회/수정/삭제도 권한 범위 안에서 지원할 수 있어야 한다

### 마커 타입

- 단서
- 실종자 발견
- 지형 상태
- 지원 요청
- 재확인 필요
- 수색 제외/확인 완료

### 사진 규칙

- 사진 첨부는 앱 마커 생성 흐름에서만 허용
- 업로드 실패 시 로컬 임시 보관
- 서버 업로드 성공 확인 후 임시 파일 삭제
- 웹이 사진 업로드를 대신하지 않는다는 전제를 앱도 지켜야 한다

### 금지

- 웹 전용 초기 기준점 마커 생성 규칙을 앱 현장 마커 생성과 섞지 않는다
- 사진 업로드 성공 전 로컬 원본을 지우지 않는다
- 마커 생성과 사진 업로드를 하나의 성공/실패 boolean으로 뭉개지 않는다

---

## 11. 오프라인 패키지 / 지도 규칙

### 지도 SDK

- `MapLibre Native Android SDK`

### 패키지 규칙

오프라인 패키지는 타일만이 아니다. 아래를 한 흐름으로 적재한다.

- 사건 메타
- 실종자 정보
- OP
- 담당 구역
- 초기 기준점 마커
- 지도 기준 범위(`map_boundary`)
- 지도 타일
- 단말/팀/순찰차 식별 정보

### 지도 규칙

- `map_boundary`는 자동 판단 기준이 아니다
- `map_boundary`는 초기 뷰포트, 패키지 범위, 구역 작성 기준이다
- 지도 미다운로드는 로컬 경고 대상이다
- OSM attribution이 보존되어야 한다

### 앱 UX 규칙

- 항목별 진행률 표시
- 실패 항목 재시도 가능
- 미완료 패키지는 준비 완료 상태로 취급하지 않음
- stale package 상태를 표현해야 함
- 사건 화면에서 실종자 기본 정보와 마지막 확인 위치를 확인할 수 있어야 함

---

## 12. 알림 / FCM 규칙

### 채널 규칙

- 서버 → 앱 실시간 전달은 FCM이 기준
- 앱은 SSE 대체 경로를 임의로 만들지 않는다

### 알림 대상 이벤트

- 지원 요청
- 실종자 발견
- 사건 종료 관련 정리 트리거

### 구현 규칙

- FCM fanout orchestration은 서버 책임이다
- 앱은 payload를 소비하고 UI/로컬 동작을 수행한다
- foreground와 background 수신 동작을 구분한다

### 금지

- 앱에서 recipient 계산을 다시 구현
- 앱에서 임의의 fanout 정책 추가
- 알림 이벤트를 도메인 write 성공의 유일한 근거로 사용

---

## 13. 시간 / 동기화 / 삭제 규칙

### 시간 필드

- `client_ts`
- `server_ts`
- `clock_offset_ms`

### 의미

- 사용자 표시 시각: `client_ts`
- 충돌 판단: `server_ts`
- 시계 오차 분석: `clock_offset_ms`

### 삭제 규칙

- 서버 ack 확인 전 로컬 데이터 삭제 금지
- 사건 종료 시 먼저 Outbox flush 시도
- flush 성공한 데이터만 ack 기준 삭제
- 실패한 미전송 항목은 로컬 보존 후 다음 복구 때 재전송
- 패키지/실종자 캐시도 점진 삭제

### 중요한 원칙

- “앱에서 안 보인다”와 “권위 있는 기록이 삭제됐다”는 다르다
- 사용자가 앱을 지워도 서버 ack된 데이터만 권위 기록으로 간주한다

---

## 14. 권한 / 인증 / Device 규칙

- 앱 계정은 `TEAM`, `PATROL_CAR`, `COMMAND` 계열을 소비한다
- 앱 write는 `assigned Device` 전제가 필요하다
- Device 없는 앱 write는 거부 대상이다
- 미등록 Device, 미배정 Device, 닫힌 incident, bootstrapping incident write는 모두 에러로 처리해야 한다

### 앱이 특히 알아야 할 에러 범주

- `channel_not_allowed`
- `device_required`
- `device_not_registered`
- `device_not_assigned`
- `incident_bootstrapping`
- `incident_closed`
- `role_denied`
- `invalid_geometry`

앱은 이 에러를 단순 문자열 토스트로 끝내지 말고, 재시도 가능 여부와 사용자 행동을 함께 모델링해야 한다.

---

## 15. UseCase 규칙

- UseCase는 하나의 명확한 목적만 가진다
- 단일 진입점 `invoke()` 또는 `execute()`로 통일한다
- 입력과 출력 타입을 명확히 둔다
- Android API를 직접 받지 않는다
- UseCase 안에서 repository 구현체를 `new` 하지 않는다

예:

- `StartSearchSessionUseCase`
- `PauseSearchSessionUseCase`
- `AppendPathBatchUseCase`
- `CreateFieldMarkerUseCase`
- `ReportPackageStatusUseCase`
- `FlushOutboxUseCase`
- `ObserveLocalWarningsUseCase`

---

## 16. ViewModel / UI 상태 규칙

- UI는 서버 성공/실패만 보지 않는다
- `saving`, `pendingSync`, `syncing`, `synced`, `retryableError`, `finalError`를 구분한다
- 기록 중 상태를 화면에서 명확히 보여야 한다
- 장시간 오프라인이면 `오프라인 / 기록 중` 상태를 배너로 보여야 한다
- 지도 실패 시에도 마커 목록, 세션 상태, 패키지 상태, 인수인계 메모 등 핵심 흐름은 유지해야 한다

UI toolkit은 이 문서에서 강제하지 않는다. Compose를 쓰든 다른 UI를 쓰든 위 상태 모델은 유지해야 한다.

---

## 17. 테스트 / 하네스 관점

Android agent가 특히 자주 걸리는 Spec은 아래다.

- `S1-2 Account, Device & RBAC`
- `S3-1 Device Path Collection`
- `S5 Markers / Photo / Notification Delivery`
- `S6 Offline Sync & Local Warnings`
- `S7 Offline Tiles & Incident Package`

### red test에서 반드시 보일 상태

- 로딩 중 비활성화
- 중복 실행 방지
- 오프라인 저장 성공 + 서버 미전송 상태
- 복구 후 ack 반영
- partial success 후 실패 항목만 재시도
- 패키지 미완료 경고
- GPS 중단 / 배터리 저하 / 지도 미다운로드 경고
- 현재 운용 중 Device 경로 강조 상태
- 바텀시트 마커 목록 진입 및 상세 이동
- 사건 종료 후 점진 삭제

### 하네스와 맞춰야 하는 값

- GPS 5초 수집 / 10초 배치
- path batch 최대치
- geometry / precision / envelope 검증
- Outbox 상태 전이
- package stale / retry / ready 상태

---

## 18. 금지 패턴

- ViewModel에서 DAO 직접 호출
- ViewModel에서 Retrofit 직접 호출
- UseCase에서 `Context`, `LocationManager`, `EventSource`, `Bitmap` 직접 사용
- domain model에 Room annotation 추가
- domain에서 Android permission 체크
- 서버 write 성공 전까지 아무 것도 저장하지 않는 구조
- `Boolean success` 하나로 동기화 상태를 표현
- active OP 없이 마커/경로/세션 write
- Device 없는 write
- 웹 전용 API를 앱에서 우회 호출
- 자동 판단형 UX 추가
- “앱은 어차피 오프라인이니까 서버 계약 무시” 식 구현

---

## 19. 코드 작성 전 최종 체크

1. 이 코드는 `presentation`, `domain`, `data`, `framework adapter` 중 어디에 속하는가?
2. Android framework 타입이 `domain`에 새어 들어가진 않았는가?
3. 이 write는 먼저 로컬 저장되고 Outbox에 들어가는가?
4. 이 기능은 앱 책임인가, 웹 책임인가?
5. 현재 `incident + active OP + device + session` 귀속이 명확한가?
6. Device가 없는 요청을 허용하고 있지 않은가?
7. 실패를 `retryable`과 `final`로 구분하고 있는가?
8. 서버 ack 전 삭제가 일어나지 않는가?
9. 지도 실패 시에도 핵심 현장 기록 흐름이 유지되는가?
10. 자동 판단 금지 원칙을 깨는 로직이 들어가 있지 않은가?

---

## 20. 이 문서의 용도

- Android agent 사전 점검
- PR 전 자기 점검
- Kotlin + Clean Architecture 구조 판단 기준
- 오프라인 우선 구현 시 경계 재확인

세부 배경과 계약은 아래 문서를 우선 본다.

- `prd.md`
- `architecture.md`
- `adr.md`
- `boundaries.md`
- `harness-scenarios.md`
