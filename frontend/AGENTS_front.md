## 범위
- 이 규칙은 Suri-Map 웹 상황판 프론트엔드 코드/문서 작업에 적용한다.
- 기준은 `AGENTS_backend.md`의 공통 도메인 규칙과 채널 경계를 공유하되, 아래 내용은 웹 프론트 구현 시 agent가 따라야 할 규칙에 집중한다.

## 핵심 규칙
- 프론트엔드는 Clean Architecture 의존 방향을 따른다. `presentation → domain ← data` 외의 역방향 의존을 만들지 않는다.
- 최상위 구조는 `app`, `core`, `features`, `shared`, `assets`, `types`를 우선 사용한다.
- 기능 구현은 `features/{feature}` 단위로 두고, feature 내부는 `domain`, `data`, `presentation`으로 분리한다.
- `domain`은 순수 TypeScript 계층으로 유지하고 React, Axios, EventSource, DOM, storage API를 알면 안 된다.
- `data`는 REST/SSE 연결, API 모델, mapper, repository 구현을 담당한다.
- `presentation`은 page, component, hook, 화면 상태, 사용자 이벤트, UseCase 호출만 담당한다.
- Repository interface는 `domain`, 구현체는 `data`에 둔다.
- 서버 응답 모델은 API 계약을 그대로 반영한다. `snake_case` 필드명을 presentation 친화형으로 바로 바꾸지 않는다.
- Model → Entity 변환과 응답 문자열 검증은 repository 또는 mapper에서 수행한다.
- Page, Component, Hook에서 API Model을 직접 사용하지 않는다.
- 인증은 세션 기반을 기본값으로 보고, `accessToken`/`refreshToken` 복구 흐름을 전역 기본 규칙으로 두지 않는다.
- 공통 HTTP 호출은 `apiClient`, 공통 SSE 연결은 stream wrapper 또는 datasource를 통해서만 생성한다.
- 전역 store는 인증/세션/공통 권한처럼 여러 feature가 함께 읽는 최소 상태만 둔다.
- 사건 보드 상태, 선택된 OP/구역/마커/패널 상태는 feature 내부 상태로 관리하고 `core` 전역 UI store로 일반화하지 않는다.
- route 문자열은 중앙 상수에서 관리하고 page에서 하드코딩하지 않는다.
- 다른 feature의 `domain`이나 `data`를 직접 참조하지 않는다. feature 간 조합은 `app` 또는 page 수준에서만 수행한다.
- 자동 판단 금지 원칙을 따른다. 추천, 누락 확정, 위험도 판정 같은 추론 로직을 프론트에서 임의로 추가하지 않는다.

### 패키지 설계 원칙
- 이름: `feature 중심 + Clean Architecture 계층 분리`
- 최상위는 `app`, `core`, `features`, `shared`처럼 역할별로 나눈다.
- `features` 하위는 도메인 용어 기준으로 나누고, `common`, `utils`, `misc`처럼 책임이 넓은 feature 신설은 지양한다.
- feature 내부에서는 `domain`, `data`, `presentation`을 먼저 나누고, 그 안에서 필요한 하위 디렉터리만 추가한다.
- 여러 feature가 공유하는 코드는 `shared` 또는 `core`로 올리되, 특정 feature 도메인 규칙까지 공용화하지 않는다.

좋은 예시
- `features/incidents/domain/usecases/GetIncidentBoardUseCase`
- `features/searchAreas/data/repositories/SearchAreaRepositoryImpl`
- `features/handover/presentation/hooks/useHandoverMemo`

지양 예시
- `shared/api/incidentHooks.ts`에서 특정 feature용 화면 상태와 API 호출을 함께 처리하는 구조
- `features/markers/presentation`에서 `features/operationalPeriods/data`를 직접 import하는 구조

### 구현 시 반드시 유지할 도메인 규칙
- 경로 기록 주체는 `Device`다. 사용자를 경로 주체로 취급하지 않는다.
- 계정 모델은 `TEAM`, `PATROL_CAR`, `COMMAND` 기준으로 해석한다.
- `OP(Operational Period)`는 사건 내부 비교, 인수인계, 조회 기준 단위다.
- `OP1` 자동 생성, 이후 OP 수동 생성 전제를 깨는 UI 흐름을 추가하지 않는다.
- 지도 기준 범위 용어는 `map_boundary`를 기준으로 맞춘다.
- 사건 생성, 종료 재오픈, 자동 판단 기능을 프론트 기본 흐름으로 전제하지 않는다.
- 실종자 정보는 장기 보존 원본이 아니라 운영 캐시 관점으로 다룬다.

### 채널 경계 규칙
- 웹은 지휘·상황 공유 채널이다.
- 앱 전용 쓰기인 수색 세션 제어, GPS 기록, 일반 현장 마커 생성, 사진 업로드를 웹에서 구현하지 않는다.
- 웹 전용 쓰기인 구역 완료, OP 생성, 지도 기준 범위 변경, 차량/도보 구간 보정은 웹에서만 다룬다.
- 인수인계 메모는 앱/웹 공통 기능으로 본다.
- 현장 마커 생성은 앱 전용으로 보되, 초기 기준점 마커는 웹에서 추가·수정 가능하다는 현재 기준을 유지한다.

## 권장 사항

### 코드 컨벤션 (FE)
- 컴포넌트와 hook은 한 가지 화면 책임에 집중한다.
- 들여쓰기 depth는 가급적 2 이하로 유지하고 Early return 패턴을 우선 검토한다.
- 과한 `useEffect` 연결보다 page 진입, 사용자 액션, SSE 이벤트 같은 명시적 트리거를 우선한다.
- Page는 라우트 진입, 권한/파라미터 검증, feature 조합만 담당하고 비즈니스 규칙을 직접 구현하지 않는다.
- Hook은 UseCase 호출, 비동기 상태, 화면 플로우 캡슐화에 집중한다.
- Component는 props 기반 렌더링과 상호작용 표현에 집중하고 데이터 취득/매핑 책임을 섞지 않는다.
- UseCase는 `execute()` 단일 진입점을 우선 사용하고, 입력/출력 타입을 명확히 둔다.
- UseCase 안에서 구현체를 직접 `new` 하지 않고 의존성 주입으로 연결한다.
- 에러는 `ValidationError`, `ForbiddenError`, `ConflictError`, `ResponseMappingError`, `StreamConnectionError`처럼 의미 있는 타입으로 분리한다.
- 응답 문자열 enum은 `as` 단언으로 통과시키지 말고 mapper에서 명시적으로 검증한다.
- DI 토큰은 상수로 관리하고, 진입점에서 container 구성을 끝낸다.

### 상태/라우팅 규칙
- 전역 store에는 `isAuthenticated`, `sessionUser`, 공통 권한 정보 같은 최소 세션 상태만 둔다.
- 현재 incident board 상태, active OP, compare OP, 선택 엔티티, 패널 상태는 feature store 또는 page hook에 둔다.
- 새로고침 후 유지돼야 하는 식별자는 URL에 두고, 일시적인 포커스/복귀 정보만 `location.state`에 둔다.
- `compare`, `handover`, 지휘 전용 화면은 page 진입 시 필수 state와 권한을 먼저 검증한다.
- state 부족 시 조용히 실패하지 말고 안전한 기본 뷰나 fallback panel로 전환한다.

### DataSource / Repository 규칙
- DataSource는 endpoint 호출, path/query/body 조립, envelope unwrap만 담당한다.
- Repository는 Model → Entity 변환, 문자열 검증, backend 변화 완충 책임을 가진다.
- SSE payload도 동일하게 datasource에서 수신하고 repository 또는 mapper에서 도메인 모델로 변환한다.
- Presentation은 datasource를 직접 참조하지 않는다.

### 빌드/테스트
- 프론트엔드 패키지 매니저는 `npm` 사용을 우선한다.
- `domain`과 UseCase는 framework 없이 단위 테스트를 우선한다.
- `data` 계층은 mapper/repository 중심 테스트로 계약 변환과 예외 처리를 검증한다.
- `presentation` 테스트는 hook/page/component 책임에 맞춰 분리하고, 권한별 진입/로딩/에러/재시도 흐름을 우선 검증한다.
- 실시간 기능 테스트에서는 active OP 비교, SSE 재연결, 세션 만료, 응답 매핑 실패를 우선 검증한다.

### 커밋 메시지
- 프론트엔드 형식: `[FE] type(scope): 설명 (Jira 티켓번호)`
- 공통 문서 형식: `[Docs] type(scope): 설명 (Jira 티켓번호)`
- type 목록: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
