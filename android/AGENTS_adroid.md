## 범위
- 이 규칙은 Suri-Map Android 현장 앱 코드/문서 작업에 적용한다.
- 기준은 `AGENTS_backend.md`의 공통 도메인 규칙과 채널 경계를 공유하되, 아래 내용은 Android 구현 시 agent가 따라야 할 코드 규칙에 집중한다.

## 핵심 규칙
- Android 앱은 Clean Architecture 의존 방향을 따른다. `presentation → domain ← data` 외의 역방향 의존을 만들지 않는다.
- 최상위 구조는 `app`, `core`, `features`를 우선 사용한다.
- 기능 구현은 `features/{feature}` 단위로 두고, feature 내부는 `domain`, `data`, `presentation`으로 분리한다.
- `domain`은 순수 Kotlin 계층으로 유지하고 Android SDK, Room, Retrofit/OkHttp, WorkManager, FCM, MapLibre 타입을 알면 안 된다.
- Repository interface는 `domain`, 구현체는 `data`에 둔다.
- `presentation`은 UI state, 사용자 이벤트, ViewModel, UseCase 호출만 담당한다.
- `data`는 REST, Room/SQLite, mapper, file staging, repository 구현, outbox 재전송 재료를 담당한다.
- foreground service, worker, location provider, FCM service, map adapter 같은 framework 의존 코드는 `core` 또는 adapter 계층에 둔다.
- ViewModel에서 DAO, API, Worker, SDK adapter를 직접 호출하지 않는다.
- 모든 중요한 write는 로컬 저장을 먼저 수행하고, 이후 outbox와 재전송으로 서버 동기화를 맞춘다.
- write 흐름은 `pending`, `sending/syncing`, `acked/synced`, `failed_retryable`, `failed_final`처럼 상태를 명시적으로 가진다.
- 서버 ack 전 로컬 원본 삭제를 기본 동작으로 두지 않는다.
- 앱은 현장 입력 채널이다. 지휘 상황판 전용 쓰기와 자동 판단 로직을 임의로 구현하지 않는다.

### 패키지 설계 원칙
- 이름: `feature 중심 + Clean Architecture 계층 분리`
- 최상위는 `app`, `core`, `features`처럼 실행 진입점, 공통 인프라, 도메인 기능으로 나눈다.
- `core`에는 인증, DB, 네트워크, 동기화, 위치, 알림, 지도, 파일 저장소처럼 여러 feature가 공유하는 인프라만 둔다.
- `features` 하위는 도메인 용어 기준으로 나누고, `misc`, `commonFlow` 같은 넓은 책임의 feature 신설은 지양한다.
- feature 내부에서는 `domain`, `data`, `presentation`을 먼저 나누고, 그 안에서 `model`, `usecase`, `local`, `remote`, `mapper`, `viewmodel`, `ui` 등을 추가한다.

좋은 예시
- `features/searchSession/domain/usecase/StartSearchSessionUseCase`
- `features/devicePath/data/repository/DevicePathRepositoryImpl`
- `features/markers/presentation/viewmodel/MarkerCreateViewModel`

지양 예시
- `presentation`에서 Room entity를 직접 state로 사용하는 구조
- `features/searchSession/presentation`에서 `features/offlineSync/data/local`을 직접 import하는 구조

### 구현 시 반드시 유지할 도메인 규칙
- 경로 기록 주체는 `Device`다.
- 계정 모델은 `TEAM`, `PATROL_CAR`, `COMMAND` 기준으로 해석한다.
- 모든 현장 기록은 `incident + active OP + device + searchSession` 기준 귀속을 우선 검토한다.
- `OP(Operational Period)`는 수색 차수, 인수인계, 경로/구역/마커 묶음의 기준 단위다.
- `OP1` 자동 생성, 이후 OP 수동 생성 전제를 깨는 앱 흐름을 추가하지 않는다.
- 지도 기준 범위 용어는 `map_boundary`를 기준으로 맞춘다.
- 사건은 앱에서 직접 생성하지 않는다.
- 실종자 정보는 장기 보존 원본이 아니라 운영 캐시 관점으로 다룬다.

### 채널 경계 규칙
- Android 앱은 현장 입력 채널이다.
- 앱 전용 쓰기인 수색 세션 시작/일시정지/재개/종료, GPS 기록, 일반 현장 마커 생성, 사진 첨부, package 상태 보고를 앱에서 다룬다.
- 웹 전용 쓰기인 구역 완료, OP 생성, 지도 기준 범위 변경, 차량/도보 구간 보정은 앱에서 구현하지 않는다.
- 사건 조회, 상세 조회, 인수인계 메모, 권한 범위 내 마커 조회/수정/삭제는 공통 기능으로 본다.
- 자동 사각지대 판단, 다음 구역 추천, 위험도 판단 같은 추론 로직을 앱에서 임의로 추가하지 않는다.

## 권장 사항

### 코드 컨벤션 (Android)
- ViewModel은 화면 상태와 사용자 액션 조합만 담당하고 비즈니스 규칙은 UseCase로 내린다.
- UseCase는 하나의 명확한 목적만 가지며 `invoke()` 또는 `execute()` 단일 진입점으로 통일한다.
- UseCase 안에서 구현체를 직접 `new` 하지 않고 생성자 주입으로 연결한다.
- `domain` 모델은 framework 타입 대신 프로젝트 도메인 타입으로 표현한다.
- mapper는 Room/DTO/SDK 타입을 domain 모델로 변환하는 책임만 가진다.
- foreground service, worker, FCM service는 UseCase 호출과 시스템 이벤트 연결에 집중하고 도메인 판단을 직접 구현하지 않는다.
- 들여쓰기 depth는 가급적 2 이하로 유지하고 Early return 패턴을 우선 검토한다.
- enum/string 상태는 의미 있는 sealed class 또는 enum으로 승격하고, 문자열 원형은 `data` 계층에서만 다루는 것을 우선 검토한다.

### 오프라인/동기화 규칙
- 현장 write는 `로컬 저장 → outbox 적재 → 재전송 → ack 반영` 순서를 기본으로 유지한다.
- 서버 실패를 기록 실패와 동일시하지 않는다.
- partial success를 모델링하고 실패 항목만 재시도 가능하게 설계한다.
- 사진 업로드 성공 전 로컬 원본을 삭제하지 않는다.
- 사건 종료나 패키지 정리에서도 ack 기준 삭제 원칙을 유지한다.

### 세션 / 경로 / 마커 규칙
- 세션은 `incident + op + device`에 연결하고, `paused` 구간은 경로를 이어붙이지 않는다.
- GPS 경로는 Device 기준으로만 기록하고 사용자 기준 궤적으로 재해석하지 않는다.
- 앱은 서버가 확정한 차량/도보 구간을 다시 읽을 수 있어야 하지만, 웹 전용 수동 보정 로직을 앱에서 구현하지 않는다.
- 일반 현장 마커 생성은 앱 전용으로 유지하고, 웹 전용 초기 기준점 마커 규칙과 섞지 않는다.
- 사진 업로드, 마커 생성, outbox ack를 하나의 boolean 성공/실패로 뭉개지지 않게 분리한다.

### 상태/에러 규칙
- UI는 `saving`, `pendingSync`, `syncing`, `synced`, `retryableError`, `finalError`를 구분해서 표현한다.
- `device_required`, `device_not_registered`, `device_not_assigned`, `incident_closed`, `role_denied` 같은 서버 에러는 재시도 가능 여부와 사용자 행동을 함께 모델링한다.
- 지도 실패가 있어도 세션 상태, 마커 목록, 인수인계 메모, 패키지 상태 같은 핵심 흐름은 유지되도록 분리한다.
- 미전송 큐 상태, GPS 중단, 배터리 저하, 지도 미다운로드 경고는 presentation state에서 명시적으로 표현한다.

### 빌드/테스트
- `domain`과 UseCase는 Android framework 없이 단위 테스트를 우선한다.
- repository, mapper, outbox 상태 전이는 `data` 계층 테스트로 검증한다.
- worker, foreground service, FCM service는 시스템 이벤트와 UseCase 연결 결과를 중심으로 검증한다.
- 테스트에서는 GPS 5초 수집, 10초 배치, outbox 상태 전이, partial success, ack 전 삭제 금지를 우선 검증한다.
- 시나리오 테스트에서는 경로 주체가 `Device`인지, 조작 주체가 `Account`인지 구분해서 fixture를 만든다.

### 커밋 메시지
- Android 형식: `[ANDROID] type(scope): 설명 (Jira 티켓번호)`
- 공통 문서 형식: `[Docs] type(scope): 설명 (Jira 티켓번호)`
- type 목록: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
