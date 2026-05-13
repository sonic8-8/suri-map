# Suri-Map Frontend 디렉토리 구조 정리

이 문서는 `frontend/AGENTS_front.md` 기준으로, Suri-Map 웹 상황판 프론트엔드에서 각 디렉토리에 무엇을 넣어야 하는지 정리한 메모다.

핵심 원칙은 다음과 같다.

- 최상위는 `app`, `core`, `features`, `shared`, `assets`, `types`를 우선한다.
- feature 내부는 `domain`, `data`, `presentation`으로 나눈다.
- `presentation -> domain <- data` 외의 역방향 의존을 만들지 않는다.
- `domain`은 순수 TypeScript만 둔다.
- `data`는 REST/SSE 연결, API 모델, mapper, repository 구현을 둔다.
- `presentation`은 page, component, hook, 화면 상태, 사용자 이벤트, UseCase 호출만 둔다.
- 서버 응답 모델은 계약을 그대로 반영하고, 화면 친화 변환은 repository 또는 mapper에서 한다.

## 현재 기준 트리

```text
src/
├── app/
├── core/
├── features/
├── shared/
├── assets/
├── types/
└── main.tsx
```

`main.tsx`는 앱 부트스트랩 진입점이고, 나머지는 역할별로 나눈다.

## 예시 트리

아래 트리는 “상황판 화면을 만들고, 전역 버튼도 하나 두고, 필요한 데이터 계층까지 붙이는” 가장 전형적인 형태다.

```text
src/
├── app/
│   ├── App.tsx
│   ├── index.ts
│   ├── routes.ts
│   └── styles.css
├── core/
│   ├── di/
│   │   └── container.ts
│   ├── network/
│   │   ├── apiClient.ts
│   │   └── eventSourceClient.ts
│   └── store/
│       └── sessionStore.ts
├── features/
│   └── situationBoard/
│       ├── domain/
│       │   ├── entities/
│       │   │   └── SituationBoard.ts
│       │   ├── repositories/
│       │   │   └── SituationBoardRepository.ts
│       │   └── usecases/
│       │       └── LoadSituationBoardUseCase.ts
│       ├── data/
│       │   ├── datasources/
│       │   │   └── SituationBoardDatasource.ts
│       │   ├── models/
│       │   │   └── SituationBoardModel.ts
│       │   └── repositories/
│       │       └── SituationBoardRepositoryImpl.ts
│       └── presentation/
│           ├── components/
│           │   ├── DashboardMapShell.tsx
│           │   ├── MapToolbar.tsx
│           │   └── IncidentStatusPanel.tsx
│           ├── hooks/
│           │   └── useSituationBoard.ts
│           ├── pages/
│           │   └── SituationBoardPage.tsx
│           ├── constants/
│           │   └── situationBoardLabels.ts
│           └── utils/
│               └── formatSituationBoardTitle.ts
├── shared/
│   └── ui/
│       ├── Button.tsx
│       ├── IconButton.tsx
│       └── SegmentControl.tsx
├── assets/
│   └── logos/
└── types/
    └── vite-env.d.ts
```

이 예시에서 핵심은 다음이다.

- `shared/ui`는 전역 재사용 컴포넌트다.
- `features/situationBoard/presentation/components`는 상황판 전용 컴포넌트다.
- `features/situationBoard/presentation/pages`는 페이지 진입점이다.
- `features/situationBoard/domain`은 규칙과 타입이다.
- `features/situationBoard/data`는 서버 계약과 저장소 구현이다.

## `src/app`

앱 전체를 조립하는 층이다. feature를 직접 구현하는 곳이 아니라, 여러 feature를 묶고 라우팅과 진입점을 구성하는 곳이다.

여기에 들어갈 것:

- `App.tsx`: 전체 화면 조립, 레이아웃 배치, feature 조합
- `routes.ts`: route 문자열 중앙 상수
- `providers.tsx`: 전역 provider 조합
- `layouts/`: 공통 레이아웃 컴포넌트가 필요할 때
- `styles.css`: 앱 전역 스타일이 필요한 경우

여기에 두지 말 것:

- API 호출 코드
- 도메인 규칙
- feature 내부 모델과 entity
- 특정 feature 전용 mapper, datasource, repository

현재 저장소 예시:

- `src/app/App.tsx`
- `src/app/routes.ts`
- `src/app/styles.css`

실무 판단 기준:

- 페이지 간 공통 shell을 만들 때는 `app`
- 라우트 보호, 권한 체크, feature 조합은 `app`
- 화면의 실제 비즈니스 처리는 feature로 내린다

## 작업 흐름

화면을 새로 만들 때는 아래 순서로 진행하면 된다.

1. 어떤 feature에 속하는지 먼저 정한다.
2. 페이지 파일을 `features/{feature}/presentation/pages`에 만든다.
3. 화면 조각을 `features/{feature}/presentation/components`에 만든다.
4. 상태와 동작을 `features/{feature}/presentation/hooks`로 뺀다.
5. 공용 버튼이나 공통 UI가 필요하면 `shared/ui`에 만든다.
6. feature 규칙과 타입이 필요해지면 `domain`을 만든다.
7. 서버와 통신이 필요해지면 `data`를 붙인다.
8. 마지막에 `app/App.tsx`나 `app/routes.ts`에서 페이지를 조립한다.

실제 판단 기준은 다음처럼 생각하면 된다.

- “이 화면 전체의 입구다” -> `pages`
- “이 화면의 작은 조각이다” -> `components`
- “이 화면의 상태와 동작이다” -> `hooks`
- “여러 화면에서 같이 쓰는 공용 버튼이다” -> `shared/ui`
- “이 기능의 규칙과 타입이다” -> `domain`
- “서버랑 주고받는다” -> `data`
- “여러 feature를 붙인다” -> `app`

## `src/core`

여러 feature가 공유하는 최소한의 공통 기반을 둔다. 전역 성격이 강하지만, feature-specific UI 상태까지 올리지는 않는다.

여기에 들어갈 것:

- `network/`
  - `apiClient`
  - 공통 HTTP 설정
  - 공통 SSE stream wrapper
  - interceptor, base URL, 공통 에러 변환
- `di/`
  - dependency injection container
  - 토큰 정의
  - 앱 시작 시 조립되는 의존성 연결
- `store/`
  - 인증 상태
  - 세션 사용자
  - 공통 권한 정보

여기에 두지 말 것:

- incident board 상태
- active OP
- 선택된 구역/마커/패널 상태
- feature 전용 화면 상태
- 특정 feature repository 구현체

현재 저장소 예시:

- `src/core/network/`
- `src/core/di/`
- `src/core/store/`

권장 파일 예시:

- `core/network/apiClient.ts`
- `core/network/eventSourceClient.ts`
- `core/store/sessionStore.ts`
- `core/di/container.ts`

## `src/features`

도메인 용어 기준의 feature 모음이다. 이 저장소에서는 현재 다음 feature들을 기준으로 둔다.

- `auth`
- `incidents`
- `situationBoard`
- `operationalPeriods`
- `searchAreas`
- `markers`
- `devices`
- `handover`
- `incidentImport`

feature 이름은 가능한 한 도메인 용어를 쓴다. 일반적인 `map`, `common`, `utils` 같은 이름은 넓고 모호해서 피한다.

### feature 공통 구조

```text
features/{feature}/
├── domain/
├── data/
└── presentation/
```

이 세 계층은 항상 같은 책임을 유지한다.

### `domain`

순수 규칙과 타입을 둔다. 프레임워크를 모르면 된다.

여기에 들어갈 것:

- `entities/`: 핵심 도메인 객체
- `repositories/`: repository interface
- `usecases/`: 유스케이스

여기에 두지 말 것:

- React hook
- DOM 접근
- Axios, fetch, EventSource
- localStorage/sessionStorage
- API DTO

예시:

- `features/incidents/domain/entities/Incident.ts`
- `features/searchAreas/domain/repositories/SearchAreaRepository.ts`
- `features/handover/domain/usecases/CreateHandoverMemoUseCase.ts`

작성 기준:

- `execute()` 단일 진입점 우선
- 입력/출력 타입은 명확하게 분리
- 다른 feature의 domain/data를 직접 참조하지 않음

### `data`

서버와의 계약을 다루는 계층이다. REST/SSE payload와 repository 구현을 담당한다.

여기에 들어갈 것:

- `models/`: API 계약 그대로의 모델
- `datasources/`: endpoint 호출, path/query/body 조립, SSE 수신, envelope unwrap
- `repositories/`: repository 구현체, model -> entity 변환, 문자열 검증, backend 변화 완충

여기에 두지 말 것:

- 화면 렌더링
- user interaction 처리
- page-level state
- domain 규칙을 넘는 임의 판단

예시:

- `features/searchAreas/data/models/SearchAreaModel.ts`
- `features/searchAreas/data/datasources/SearchAreaDatasource.ts`
- `features/searchAreas/data/repositories/SearchAreaRepositoryImpl.ts`

작성 기준:

- 서버 응답 필드는 `snake_case`를 그대로 유지
- presentation 친화형 이름으로 바로 바꾸지 않음
- 문자열 enum 검증은 mapper/repository에서 수행
- SSE도 datasource에서 받고 repository 또는 mapper에서 도메인 모델로 바꿈

### `presentation`

화면과 사용자 상호작용을 담당한다. 데이터 취득 자체보다 화면 흐름과 조합이 주 역할이다.

여기에 들어갈 것:

- `pages/`: 라우트 단위 화면
- `components/`: 재사용 가능한 화면 조각
- `hooks/`: UI 상태, usecase 호출, 페이지 플로우 캡슐화
- `constants/`: 화면 전용 상수
- `utils/`: presentation 전용 보조 함수

여기에 두지 말 것:

- repository 구현
- datasource 직접 호출
- API 모델 직접 사용
- domain 외부 규칙의 임의 계산

예시:

- `features/situationBoard/presentation/pages/SituationBoardPage.tsx`
- `features/situationBoard/presentation/components/DashboardMapShell.tsx`
- `features/handover/presentation/hooks/useHandoverMemo.ts`

작성 기준:

- page는 라우트 진입, 권한/파라미터 검증, feature 조합만 담당
- hook은 usecase 호출, 비동기 상태, 화면 플로우 캡슐화
- component는 props 기반 렌더링과 상호작용 표현에 집중

## `src/features/situationBoard`

현재 웹 상황판의 중심 feature다. 이 feature는 실제 화면 조합, 지도 상태, 패널 상태, 사건 보드 UX를 담는다.

권장 구조:

```text
features/situationBoard/
├── domain/
│   ├── entities/
│   ├── repositories/
│   └── usecases/
├── data/
│   ├── models/
│   ├── datasources/
│   └── repositories/
└── presentation/
    ├── pages/
    ├── components/
    ├── hooks/
    ├── constants/
    └── utils/
```

현재 저장소에 이미 있는 예시:

- `features/situationBoard/presentation/components/DashboardMapShell.tsx`
- `features/situationBoard/domain/entities/`
- `features/situationBoard/domain/repositories/`
- `features/situationBoard/domain/usecases/`
- `features/situationBoard/data/models/`
- `features/situationBoard/data/datasources/`
- `features/situationBoard/data/repositories/`

이 feature에 어울리는 것:

- 사건 보드의 현재 상태
- active OP 표시
- compare OP 비교 상태
- 지도 도구
- 구역/마커/패널 선택 상태
- 상황판 전용 hook

이 feature에 두지 말 것:

- 다른 feature의 내부 repository
- 전역 인증 상태
- 공통 API 클라이언트 구현

## `src/features/incidents`

사건 조회, 사건 상세, 사건 목록, 사건 선택 같은 사건 중심 기능을 둔다.

여기에 들어갈 것:

- 사건 목록/상세 page
- 사건 선택 UI
- 사건 종료/재오픈 관련 UI가 아니라면 단순 조회
- 사건 import와 연계되는 화면 조합

적합한 하위 파일 예시:

- `domain/entities/Incident.ts`
- `data/models/IncidentModel.ts`
- `presentation/pages/IncidentListPage.tsx`

## `src/features/operationalPeriods`

OP(Operational Period)와 관련된 기능을 둔다. 사건 내부 차수, 비교 기준, 인수인계 기준을 다룬다.

여기에 들어갈 것:

- OP 목록
- OP 비교
- OP 생성 UI
- OP 인수인계와 연결되는 상태

적합한 하위 파일 예시:

- `domain/entities/OperationalPeriod.ts`
- `domain/usecases/CreateOperationalPeriodUseCase.ts`
- `presentation/hooks/useOperationalPeriodSelection.ts`

## `src/features/searchAreas`

구역 분할, 구역 할당, 구역 완료, 구역 메모처럼 검색 구역에 관한 기능을 둔다.

여기에 들어갈 것:

- search area CRUD에 해당하는 화면
- 구역 분할/할당/완료 UI
- map_boundary와 연계되는 구역 조작

적합한 하위 파일 예시:

- `data/repositories/SearchAreaRepositoryImpl.ts`
- `presentation/components/SearchAreaPanel.tsx`

## `src/features/markers`

마커 조회, 수정, 삭제 같은 마커 관련 기능을 둔다.

주의할 점:

- 일반 현장 마커 생성은 웹에서 새로 만들지 않는다
- 초기 기준점 마커는 웹에서 추가/수정 가능하다
- 지휘 계정과 일반 팀 계정의 권한 범위 차이를 분리해서 다룬다

적합한 하위 파일 예시:

- `domain/entities/Marker.ts`
- `presentation/hooks/useMarkerSelection.ts`

## `src/features/devices`

Device 최신성, 온라인 상태, 마지막 활동, 오프라인 패키지 미적재 같은 단말 관련 정보를 둔다.

여기에 들어갈 것:

- device 목록
- device health
- device activity 표시

중요한 도메인 규칙:

- 경로 기록 주체는 사용자 대신 `Device`다

## `src/features/handover`

인수인계 메모와 관련된 기능을 둔다. 앱/웹 공통으로 읽고 쓰는 범위가 있지만, 프론트 구현은 화면과 폼 중심이다.

여기에 들어갈 것:

- 메모 작성/수정 UI
- 메모 조회 패널
- OP/구역 인수인계 화면

적합한 하위 파일 예시:

- `presentation/hooks/useHandoverMemo.ts`
- `presentation/components/HandoverMemoEditor.tsx`

## `src/features/auth`

세션 인증, 로그인 상태, 공통 권한 상태와 연결되는 기능을 둔다.

여기에 들어갈 것:

- 로그인/세션 유지 UI
- 현재 로그인 사용자 정보
- 공통 권한 체크와 연결되는 화면

여기에 두지 말 것:

- access/refresh token 기반 복구 흐름을 전역 기본 규칙으로 두는 구조

## `src/features/incidentImport`

배정 사건 가져오기, import 결과 확인, 초기 사건 세팅과 연결되는 기능을 둔다.

여기에 들어갈 것:

- 사건 import 리스트
- import 결과 패널
- 초기 배정 사건 활성화 화면

## `src/shared`

여러 feature가 같이 쓰는 공용 코드다. 특정 feature의 도메인 규칙을 공용화하는 곳은 아니다.

여기에 들어갈 것:

- 공용 helper
- 공용 formatter
- 공용 UI primitive
- 공용 config
- 공용 validation

여기에 두지 말 것:

- incident 전용 hook
- searchArea 전용 repository
- situationBoard 상태
- feature-specific API wrapper

현재 저장소 예시:

- `src/shared/config.ts`

권장 파일 예시:

- `shared/constants.ts`
- `shared/formatters.ts`
- `shared/ui/`

## `src/assets`

정적 자산을 둔다.

여기에 들어갈 것:

- 이미지
- 아이콘 원본
- 폰트
- 정적 지도 보조 리소스

여기에 두지 말 것:

- React 컴포넌트
- feature별 비즈니스 코드

## `src/types`

전역 타입 선언과 ambient declaration을 둔다.

여기에 들어갈 것:

- `vite-env.d.ts`
- `*.d.ts`
- 전역 모듈 선언
- window 확장 타입

여기에 두지 말 것:

- 기능별 entity
- API 모델
- repository interface

## `src/main.tsx`

앱 진입점이다. 여기서는 부트스트랩만 한다.

여기에 들어갈 것:

- `createRoot`
- 전역 provider 조립
- 전역 CSS import
- 앱 마운트

여기에 두지 말 것:

- 화면 로직
- domain 규칙
- API 호출

## 파일 배치 빠른 기준

새 파일을 만들 때는 아래 순서로 판단하면 된다.

1. 이 파일이 앱 전체 조립인지 확인한다. 맞으면 `app`.
2. 여러 feature가 같이 쓰는 최소 기반인지 확인한다. 맞으면 `core` 또는 `shared`.
3. 도메인 용어로 설명되는 개별 기능인지 확인한다. 맞으면 `features/{feature}`.
4. 해당 기능 안에서도 순수 규칙이면 `domain`, 서버 계약이면 `data`, 화면이면 `presentation`.

## 지양 기준

- `features/map`처럼 도메인 용어가 아닌 일반명으로 feature를 만들지 않는다.
- `shared/api` 아래에 특정 feature UI와 API를 섞지 않는다.
- `presentation`에서 다른 feature의 `data`를 직접 import하지 않는다.
- `domain`에서 React, DOM, storage, Axios, EventSource를 참조하지 않는다.
- 화면 상태를 `core` 전역 store로 무조건 올리지 않는다.

## 현재 저장소에 맞는 해석

이 저장소의 현재 프론트 구조는 문서 기준으로 보면 이미 큰 틀은 맞춰진 상태다.

특히 다음은 기준에 맞는다.

- `src/app` 존재
- `src/core` 존재
- `src/features/{feature}` 구조 사용
- `src/features/situationBoard`를 실제 상황판 중심 feature로 둠
- `src/shared`, `src/assets`, `src/types` 분리

이 문서는 앞으로 기능을 추가할 때 어느 파일을 어디에 둘지 판단하는 기준 문서로 쓰면 된다.
