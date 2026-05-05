# Suri Map Frontend Agent Spec

> 목적: 사람이 길게 읽는 설계서가 아니라, agent가 웹 프론트 코드를 작성하기 전에 빠르게 확인하는 현재 기준 압축 규칙집
> 기준 문서: `architecture.md`, `adr.md`, `adr-archive.md`, `suri-map-frontend-docs/`
> 범위: Suri-Map 웹 상황판 프론트엔드
> 업데이트: 2026-04-30

---

## 1. 현재 기준 요약

- 웹은 **현장 입력 채널이 아니라 지휘 상황판**이다.
- 웹은 **공개 HTTPS 도메인 + 데스크톱 브라우저(Chrome/Edge 최신 2버전)** 기준이다.
- 웹은 **오프라인 우선 구조를 갖지 않는다**. 오프라인 대응은 Android 앱 책임이다.
- 웹은 **React 18+ SPA + TypeScript** 기준이다.
- 지도/실시간은 **MapLibre GL JS + SSE(EventSource)** 기준이다.
- 현재 설계 문서 묶음의 프론트 구현 기준은 **Vite, React Router, Zustand, tsyringe, Axios, CSS Modules** 조합이다.
- 실시간 양방향 소켓은 쓰지 않는다. **WebSocket은 MVP 범위 외**다.
- 인증은 기존 토큰 갱신 모델이 아니라 **웹 표준 세션 기반**으로 본다.

---

## 2. 제품/도메인 기준

### 웹 상황판이 해야 하는 일

- 배정 사건 목록을 조회하고 권한이 있는 사건만 연다
- 사건 가져오기 결과를 확인하고 상황판을 연다
- 실종자 기본 정보를 확인하고, 허용된 실종팀 계정은 정보를 입력/수정한다
- 실종팀 간부 권한 범위에서 사건 종료, 지원 부대 배정, 지휘관 지정을 수행한다
- 지도 기준 범위(`map_boundary`)를 보고 조정한다
- OP(`operational_period`) 단위로 경로/구역/마커/메모/요약을 비교한다
- 구역 분할, 할당, 완료 처리와 새 OP 생성을 수행한다
- Device 최신성, 오프라인 패키지 미적재 상태, 최근 활동을 판단 보조용으로 보여준다
- 인수인계 메모와 AI 수색 이력 요약을 읽고 작성한다

### 웹이 하면 안 되는 일

- 일반 현장 마커를 새로 생성한다
- 사진을 업로드한다
- 수색 세션을 시작/일시정지/재개/종료한다
- GPS 경로를 직접 기록한다
- 자동 사각지대 판단, 다음 구역 추천, 위험도 판단을 한다

### 프론트에서 공통으로 쓰는 핵심 도메인 용어

- `incident`
- `missingPerson` 또는 `missing_person`
- `operationalPeriod`
- `incidentMembership`
- `opAssignment`
- `device`
- `searchSession`
- `searchPath`
- `pathSegment`
- `searchArea`
- `marker`
- `handoverMemo`
- `aiSummary`
- `mapBoundary`

기존 부동산/대시보드/게임식 일반 예시로 치환하지 말고, 반드시 위 용어를 우선한다.

---

## 3. 채널 경계

### 웹 전용 조작

- 배정 사건 가져오기/활성화
- 사건 종료
- 지원 부대 배정
- 지휘관 지정
- 실종자 정보 입력/수정
- 지도 기준 범위 지정/수정
- 구역 분할/할당/완료
- 새 OP 생성
- 차량/도보 구간 수동 보정
- 초기 기준점 마커 추가/수정

### 앱 전용 조작

- 수색 세션 시작/일시정지/재개/종료
- GPS 기록
- 현장 마커 생성
- 사진 첨부

### 앱+웹 공통 조작

- 배정 사건 조회
- 사건 상세 조회
- 실종자 기본 정보 조회
- 마커 조회/수정/삭제
- OP/경로/구역 인수인계 메모 작성

예외:
- 일반 현장 마커 생성은 앱 전용이다.
- 단, 사건 초기 설정용 **기준점 마커**는 웹에서 추가/수정할 수 있다.
- 마커 수정/삭제 권한은 동일하지 않다. 지휘 계정은 전체 마커, 일반 팀 계정은 자기 계정 생성분 중심으로 본다.

---

## 4. 기술/런타임 기준

### 확정된 상위 기준

- 웹 언어: `TypeScript`
- 웹 프레임워크: `React 18+ SPA`
- 빌드 런타임: `Node.js LTS`
- 지도: `MapLibre GL JS`
- 실시간: `EventSource` 기반 `SSE`
- 서버 계약: `Spring Boot REST JSON + SSE`

### 프론트 문서 묶음 기준 기본 도구

- `Vite`
- `React Router`
- `Zustand`
- `tsyringe`
- `Axios`
- `CSS Modules`

패키지나 도구를 새로 추가할 때는 "기존 기준을 단순화하는가"를 먼저 본다. 현재 기준을 뒤집는 수준의 교체는 문서 근거 없이 하지 않는다.

---

## 5. 최상위 구조 원칙

### 의존 방향

```text
presentation  →  domain  ←  data
      ↓                     ↑
   app/router         core/di/container
      ↓                     ↑
board orchestration   apiClient / stream wrapper
```

### 핵심 판단

- 비즈니스 규칙: `domain`
- API/SSE payload: `data/models`
- REST/SSE 연결: `data/datasources` 또는 `core/network`
- Model → Entity 변환: `data/repositories`
- 상황판 선택 상태, 패널 상태, view mode 상태: `presentation/hooks`
- 인증 세션, 현재 로그인 계정 정보, 공통 권한 정보: `core/store`

### 제품 레벨 구조 해석

- 저장소 전체는 **도메인 수직 슬라이스** 기준이다.
- 프론트 내부 구현은 feature 구조를 유지해도 된다.
- 다만 프론트만 따로 놀면 안 되고, 백엔드/Android와 **같은 용어와 같은 계약**을 사용해야 한다.

---

## 6. 폴더 구조 규칙

### 기본 구조

```text
src/
├── app/
├── core/
├── features/
├── shared/
├── assets/
├── types/
└── global.d.ts
```

### feature 내부 구조

```text
features/{feature}/
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

### 권장 feature 예시

```text
features/
├── auth/
├── incidents/
├── situationBoard/
├── operationalPeriods/
├── searchAreas/
├── markers/
├── devices/
├── handover/
└── incidentImport/
```

기존 `zones` 같은 일반 명칭보다 현재 문서 기준인 `searchAreas`가 더 적합하다.

---

## 7. Feature 간 참조 규칙

### 기본 금지

- `domain` → 다른 feature 참조 금지
- `data` → 다른 feature 참조 금지
- 다른 feature의 내부 구현 세부 경로를 무분별하게 참조 금지

### 제한적 허용

다음 조건이면 `presentation` 또는 `app` 레벨에서만 허용:

- 하나의 상황판 흐름을 연결하기 위해 필요하다
- 도메인 규칙 공유가 아니라 UI 조합이다
- 순환 참조를 만들지 않는다

예:

- `IncidentBoardPage` → `OperationalPeriodTimeline`
- `IncidentBoardPage` → `HandoverSummaryPanel`
- `IncidentBoardPage` → `SearchAreaPanel`
- `IncidentBoardPage` → `useBoardRealtime`

### 실무 판단

- 다른 feature의 UseCase를 domain에서 직접 호출하지 말 것
- 다른 feature의 presentation 모듈을 끌어오더라도 page 수준에서 조합할 것
- `mapBoundary`, `device freshness`, `offline package status` 같은 상황판 조합 정보는 UI 레벨 조합으로 푼다

---

## 8. 상태 관리 규칙

### 전역 store로 둘 것

- `isAuthenticated`
- `sessionUser`
- `sessionStatus`
- `accountType`
- `affiliation`
- `grantedIncidentRoles`처럼 여러 feature가 공통으로 읽는 최소 권한 정보

### feature hook 또는 feature store로 둘 것

- 현재 사건 보드 로딩 상태
- active OP
- compare OP 목록
- 선택한 구역 / 마커 / 단말 / 세션
- 사이드바 탭
- 인수인계 요약 패널 상태
- 단순 지도 보기 모드
- 단말 최신성 강조 상태
- 오프라인 패키지 경고 배지 표시 상태
- page 단위 loading/error

### 컴포넌트 로컬로 둘 것

- 토글
- 입력값
- hover/expanded 상태
- 메모 임시 draft
- 레전드 열림/닫힘

### 금지

- `accessToken`, `refreshToken`을 전역 auth 기본 모델로 가정하지 않는다
- active OP를 전역 store에 올리지 않는다
- 선택한 엔티티를 `core/store`로 일반화하지 않는다
- 상황판 패널 상태를 거대한 글로벌 UI store로 만들지 않는다

---

## 9. Page / Component / Hook 역할

### Page

- route entry
- `params`, `query`, `location.state` 해석
- 현재 `view` 결정
- feature 간 UI 조합
- 접근 권한/진입 상태 검증
- 최종 navigate

허용:

- 다른 feature의 공개 panel/hook/page 조합
- flow guard
- state 검증

금지:

- axios 직접 호출
- DTO 직접 매핑
- 비즈니스 규칙 구현

### Component

- props 기반 렌더링
- 시각 블록
- 단일 역할
- 지도 레이어 또는 패널 1개 책임

### Hook

- UseCase 호출
- async 상태 관리
- loading/error 관리
- page 내부의 UI 플로우 상태 캡슐화
- 지도/SSE wrapper 연결

---

## 10. 라우팅 규칙

### 라우트 상수 중앙 관리

- 경로 문자열 하드코딩 금지
- `app/routes.ts`에서 관리

예:

```ts
export const ROUTES = {
  LOGIN: '/login',
  INCIDENTS: '/incidents',
  INCIDENT_IMPORT: '/incidents/import',
  INCIDENT_BOARD: '/incidents/:incidentId',
  INCIDENT_BOARD_COMPARE: '/incidents/:incidentId/compare',
  INCIDENT_BOARD_HANDOVER: '/incidents/:incidentId/handover',
  NOT_FOUND: '*',
} as const;
```

### view 기반 page 재사용 허용

예:

```tsx
<IncidentBoardPage view="live" />
<IncidentBoardPage view="compare" />
<IncidentBoardPage view="handover" />
```

### `location.state` 사용 기준

URL보다 `location.state`에 둘 것:

- 직전 플로우에서만 필요한 임시 데이터
- 복귀용 경로
- 특정 구역/마커/단말 포커스 정보
- 알림에서 넘어온 focus 대상

URL에 둘 것:

- 새로고침 후에도 유지돼야 하는 식별자
- 공유 가능한 사건/OP 주소

### page 진입 시 검증 필수

- `compare` 뷰면 `compareOpIds` 검증
- `handover` 뷰면 `selectedOpId` 또는 기본 active OP fallback 검증
- 지휘 전용 액션이면 현재 세션 계정의 권한 범위 검증
- state 부족 시 조용히 실패하지 말고 fallback 패널 또는 안전한 기본 뷰로 전환

---

## 11. UseCase 규칙

- UseCase는 `execute()` 단일 진입점만 가진다
- React, DOM, Axios, EventSource, storage 직접 의존 금지
- Repository는 DI로 주입받는다
- 도메인 규칙 위반은 `DomainError`
- 에러를 내부에서 삼키지 않는다

허용:

- 입력 검증
- 순수 계산
- 여러 repository 조합
- OP 생성 사유 같은 도메인 enum 검증

금지:

- `useState`, `useEffect`
- `axios.get(...)`
- `new EventSource(...)`
- `window`, `document`, `localStorage`, `sessionStorage`
- 자동 판단 로직 추가
- 다른 feature의 presentation 호출

---

## 12. 인증 / 세션 / DI 규칙

### 인증/세션

- 웹은 **표준 세션 기반**으로 본다
- 인증 정보 전달은 세션 쿠키 전제를 우선한다
- `401` 발생 시 refresh token 복구 흐름을 기본 가정하지 않는다
- 세션 만료 시 auth store 정리 후 로그인 화면으로 이동한다

### DI

- `tsyringe` 사용
- `reflect-metadata`는 진입점 최상단 import
- 토큰은 문자열 하드코딩 대신 `DI_TOKENS` 상수 사용
- DataSource는 `registerSingleton`
- Repository는 인터페이스 토큰에 `useClass` 등록

금지:

- UseCase에서 구현체 `new` 생성
- component에서 repository 구현체 직접 resolve
- `@injectable()` 누락

---

## 13. DataSource / Repository / Model 규칙

### Model

- API 계약 그대로 반영
- snake_case 허용
- 서버 응답 필드를 임의로 프론트 친화형으로 바꾸지 않는다
- 화면용 상태를 직접 담지 않는다

### DataSource

- endpoint 호출만 담당
- path/query/body 조립
- SSE 연결 생성
- envelope unwrap

### Repository

- Model → Entity 변환
- union/string 안전성 검증
- backend 응답 변화에 대한 완충층
- 현재 문서 용어 기준으로 정규화

### 특히 검증해야 하는 문자열

- `account_type`
- `device_type`
- `marker_type`
- `search_area_status`
- `path_segment_kind`
- `operational_period_reason`
- `offline_package_status`

지원하지 않는 문자열은 반드시 `ResponseMappingError`로 실패시킨다.

### 금지

- presentation에서 `Model` 직접 사용
- DataSource가 domain entity 반환
- `as` 단언으로 enum/string 강제 통과

---

## 14. 네트워크 / 에러 규칙

### 공통 규칙

- 모든 HTTP 통신은 공통 `apiClient`
- component/page/hook에서 axios 직접 호출 금지
- 세션 기반 요청 옵션과 공통 에러 매핑은 `apiClient` 또는 interceptor에서 처리
- SSE는 공통 wrapper 또는 stream datasource에서만 생성

### 응답 처리

- `401`: 세션 정리 후 로그인 이동
- `400`: `ValidationError`
- `403`: `ForbiddenError`
- `404`: `NotFoundError`
- `409`: `ConflictError`
- 기타 HTTP: `NetworkError`
- 스트림 연결 실패: `StreamConnectionError`
- 응답 매핑 실패: `ResponseMappingError`

### Presentation hook 책임

- 로딩/에러 메시지 상태 관리
- 사용자 메시지 변환
- 재시도 버튼 상태 관리
- SSE 일시 끊김 시 banner/badge 노출

---

## 15. 지도 / 실시간 규칙

### 레이어 분리

```text
IncidentBoardMap
  ├── MapBoundaryLayer
  ├── SearchAreaLayer
  ├── SearchPathLayer
  ├── PathSegmentLayer
  ├── SearchMarkerLayer
  ├── DeviceLayer
  ├── DeviceFreshnessLayer
  └── FocusOverlay
```

- `searchPath`, `searchArea`, `marker`, `device`, `mapBoundary`는 source/layer 단위로 분리한다
- active OP와 compare OP는 시각적으로 명확히 구분한다
- `pathSegment`의 차량/도보/unknown 구분은 라인 스타일에서 드러나야 한다

### SDK 규칙

- 지도 타입은 `any`로 다루지 않는다
- 지도 생성/cleanup은 `useBoardMap` 또는 전용 adapter
- component 본문에서 지도 인스턴스 직접 생성 금지

### 제품 규칙

- 자동 사각지대 하이라이트를 만들지 않는다
- 추천/판정형 오버레이를 추가하지 않는다
- 초기 뷰포트는 `mapBoundary`와 최근 활동 기준으로 계산한다
- 단말 최신성은 `last_sync_ts` 기반 경과 시간 시각화로 푼다

### fallback 필수

타일 지연, WebGL 이슈, SSE 문제여도 플로우는 이어져야 한다.

허용 fallback:

- 구역 목록 중심 패널
- 마커/단말 리스트
- 인수인계 요약
- 수동 새로고침
- 마지막 REST 조회 기준 읽기 전용 상태

금지:

- 지도 실패 시 화면 전체 중단
- fallback 없이 에러 문구만 출력

---

## 16. 스타일링 규칙

### 기본

- 일반 UI는 CSS Module 우선
- 공통 값은 CSS 변수 사용

### 예외 허용

다음은 inline style 허용:

- 지도 overlay
- floating panel
- 최신성 badge / tooltip
- 지도 미가용 fallback 레이아웃

### 레이어링

```text
z-index 0   : map canvas
z-index 10  : path / area / marker overlay
z-index 20  : badge / tooltip / legend
z-index 30  : sidebar / floating panel
z-index 40+ : modal
```

### 금지

- 모든 UI를 inline style로 작성
- CSS Module과 inline style 혼용 기준 없이 사용
- active OP와 compare OP를 애매하게 표현
- Device 최신성, 오프라인 패키지 경고, 지원 요청 같은 상태를 시각적으로 같은 톤으로 섞어 표현

---

## 17. 네이밍 / TypeScript 규칙

### 네이밍

- component 파일/폴더: PascalCase
- hook: `use` 접두사
- UseCase: `UseCase` 접미사
- Repository interface: `I` 접두사
- Repository 구현체: `Impl` 접미사
- DataSource: `RemoteDataSource`, `StreamDataSource`
- Store: camelCase + `Store`
- Page: `Page` 접미사

금지:

- `Component`, `Manager`, `Helper` 남용
- 의미 없는 축약어
- presentation에서 `Service` 용어 사용

### TypeScript

- `any` 금지
- 반환 타입 명시
- union type 우선
- domain entity는 `readonly` 우선
- 지도/스트림 타입도 `any`로 뭉개지 않는다
- `@ts-ignore` 금지, 필요한 경우 `@ts-expect-error` + 이유 명시

특히 아래는 적극 사용:

- discriminated union
- type guard
- `Partial`, `Pick`, `Omit`, `Record`

---

## 18. 코드 작성 전 최종 체크

아래 질문에 모두 답할 수 있어야 한다.

1. 이 코드는 `domain`, `data`, `presentation`, `core` 중 어디에 속하는가?
2. 이 기능은 웹 상황판 책임인가, 앱 책임인가?
3. 현재 도메인 용어를 `incident / OP / device / searchArea / marker / handover` 기준으로 쓰고 있는가?
4. 이 상태는 정말 전역 store가 필요한가?
5. 이 API 호출 또는 SSE 연결은 DataSource/공통 wrapper 안에만 있는가?
6. DTO를 UI에 직접 넘기고 있지 않은가?
7. route-state 필수값 검증이 있는가?
8. 지도 실패 시 fallback이 존재하는가?
9. 자동 판단 금지 원칙을 깨는 UI/로직이 들어가 있지 않은가?
10. `401` 처리에 refresh token 전제를 몰래 넣지 않았는가?
11. 문자열 enum을 검증 없이 단언하고 있지 않은가?
12. 이 변경이 `live / compare / handover` 또는 사건 가져오기 흐름 중 어디에 영향을 주는지 확인했는가?

---

## 19. 이 문서의 용도

- agent 사전 점검
- PR 전 자기 점검
- 새 기능 추가 시 빠른 구조 판단
- 기존 문서의 오래된 가정 제거 확인

상세 배경은 아래 문서를 우선 본다.

- `architecture.md`
- `adr.md`
- `adr-archive.md`
- `suri-map-frontend-docs/00_INDEX.md`
- `suri-map-frontend-docs/09_router-and-flow.md`
- `suri-map-frontend-docs/10_network-and-error.md`
- `suri-map-frontend-docs/11_map-sdk-and-live-map.md`
