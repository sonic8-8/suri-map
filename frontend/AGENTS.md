# Frontend Agent Rules

Suri-Map Web 상황판 전용 규칙이다. 저장소 공통 규칙은 `../AGENTS.md`를 먼저 따른다.

## 기준 문서

| 관심사 | 기준 |
|---|---|
| Public API | `../docs/api/api-spec.md` |
| Board slot / SSE contract | `../docs/spec/boundaries.md §9` |
| Harness scenario | `../docs/spec/harness-scenarios.md` |
| Current stack | `package.json` |

## 현재 스택

현재 frontend stack은 React 19, TypeScript, Vite, MapLibre GL JS, TanStack Query, Zustand, lucide-react다. Routing, HTTP client, DI, styling library는 `package.json`에 실제 존재하는 의존성 기준으로만 결정한다.

## 프로젝트 / 플랫폼 개요

- Web은 현장 입력 앱이 아니라 지휘 상황판이다.
- 전체/팀 수색 구역, PolicePhone 경로, 마커, OP 비교, 인수인계 메모, 수색 이력 요약, 오프라인 패키지 상태를 보여준다.
- Web 전용 command: 사건 import/close, 전체 수색 구역 조정, 구역 분할/할당/완료, OP 생성, 차량/도보 구간 보정, 수색 이력 요약 생성.
- 앱 전용 write인 현장 마커 생성, 사진 업로드, GPS batch, SearchPath 시작/종료, package installation status write를 Web에서 구현하지 않는다.

## 구조 / 아키텍처

권장 구조:

```text
src/
├── app/
├── shared/
│   ├── api/
│   ├── config/
│   ├── lib/
│   └── ui/
└── features/
    └── {feature}/
        ├── api/
        ├── model/
        ├── hooks/
        ├── components/
        └── utils/
```

- 정통 Clean Architecture 폴더를 강제하지 않는다. DTO, query hook, UI view model, component 책임을 분리하는 경량 feature 구조를 우선한다.
- `shared`에는 특정 feature 정책을 넣지 않는다.
- Feature 간 조합은 page/app 수준에서 한다. 한 feature가 다른 feature 내부 API를 직접 깊게 import하지 않는다.
- `core`, `common`, `misc` 같은 넓은 책임의 신규 폴더는 필요성을 먼저 설명한다.

## 코드 스타일 / 컨벤션

| Do | Don't |
|---|---|
| `unknown`을 narrowing해서 사용 | `any` 추가 |
| API DTO와 UI view model 분리 | 서버 응답을 component props로 그대로 전파 |
| string union으로 API enum 표현 | 임의 문자열 비교 흩뿌리기 |
| nullable/optional 필드 명시 처리 | `!` non-null assertion 남발 |

- `strict` TypeScript를 전제로 작성한다.
- `type`은 union, mapped type, DTO alias에 우선 사용하고, `interface`는 확장 가능한 props/object contract에 사용한다.
- Component 파일명과 component명은 PascalCase를 사용한다.
- Props type은 `{ComponentName}Props`로 둔다.
- Component가 fetch, mapping, rendering, command handling을 모두 가지면 hook/component로 나눈다.
- Hook은 `use...` 이름을 사용하고 데이터 fetch, derived state, UI action 중 책임을 좁힌다.
- DTO 이름은 API 계약에 맞추고, 화면 표시용 타입은 `...ViewModel` 또는 feature-local model로 분리한다.

## API / Server State

- Server state는 TanStack Query로 관리한다.
- Query key는 feature와 scope가 드러나게 배열로 둔다. 예: `['incidentBoard', incidentId, { opIds }]`.
- API client 함수는 command/query가 드러나게 이름 짓는다. 예: `fetchIncidentBoard`, `createHandoverMemo`.
- API client는 `shared/api` 또는 feature `api`에 두고, request/response 변환 위치를 명확히 한다.
- Zustand는 선택된 OP, 지도 view mode, 열려 있는 panel처럼 여러 컴포넌트가 공유하는 UI state에만 사용한다.
- 서버 응답 cache를 Zustand에 복제하지 않는다.
- loading, error, empty, stale state를 화면에서 구분한다.
- Import는 외부 패키지, shared/app, feature local 순서로 정렬한다.
- Barrel export는 public surface가 안정된 shared UI에만 제한적으로 사용한다.

## Board / Map

- Board shell, slot mounting, shared state wiring은 S3-2 경계를 따른다.
- Slot 이름은 `../docs/spec/boundaries.md §9.2`를 그대로 사용한다: `overall_search_area`, `area`, `path`, `police_phone_freshness`, `marker`, `package_badge`, `op_toggle`, `handover_memo`, `search_history_summary`, `incident_terminal` 등.
- MapLibre source/layer id는 slot과 entity가 드러나게 정한다.
- `search_area.area_level = OVERALL`은 초기 viewport와 package 범위 기준이다. 자동 누락 판단 기준으로 쓰지 않는다.
- PolicePhone freshness는 색, 외곽선, 라벨 같은 시각 인코딩으로만 표현하고 별도 알림으로 만들지 않는다.

## Realtime

- Web 실시간은 SSE `GET /api/incidents/{incidentId}/events`를 사용한다.
- SSE payload는 refetch signal이다. 별도 board read-model DB나 클라이언트 단독 truth를 만들지 않는다.
- `eventId`로 중복 적용을 막고, replay가 불가능하면 board API를 재조회한다.
- WebSocket을 추가하지 않는다.

## UI / Style

- 상황판은 운영 도구다. 정보 밀도, 스캔 가능성, 안정적인 layout을 우선한다.
- 버튼은 명확한 command에 사용하고, 도구성 액션은 lucide-react icon과 tooltip을 우선한다.
- 카드 안에 카드를 중첩하지 않는다. 반복 항목, modal, tool panel에만 card를 사용한다.
- 텍스트가 버튼/패널 안에서 넘치지 않게 responsive constraint를 둔다.
- CSS는 component와 가까운 위치에 두고, class name은 역할이 드러나게 한다.
- Inline style은 MapLibre overlay, dynamic geometry, CSS 변수 주입처럼 런타임 값이 필요한 경우에만 사용한다.
- 자동 판단, 추천, 위험도 판정처럼 제품 범위를 넘는 문구를 넣지 않는다.

## 빌드 / 테스트 명령

- Type check: `npm run typecheck`
- Build: `npm run build`

## 테스트 지침

- 테스트를 추가하면 API DTO mapper, query hook, board slot rendering, SSE refetch convergence를 우선 검증한다.
