# Frontend Loading Optimization Plan

## 결론

프론트 로딩 최적화는 두 종류로 나눠서 봐야 한다.

1. **JS 초기 번들 최적화**
   - 목적: `/login` 또는 최초 진입 시 당장 필요 없는 page code 로딩을 늦춘다.
   - 방법: route page component를 `React.lazy`와 `Suspense`로 감싼다.
   - 효과: JS chunk 로딩 시점을 늦춘다.
   - 한계: API 요청 수는 줄어들지 않는다.

2. **Incident API 요청 최적화**
   - 목적: `/incidents` 진입 후 `GET /incidents/{id}` detail 요청이 여러 번 나가는 문제를 줄인다.
   - 방법: `IncidentListPage`의 detail 선로딩을 제거하거나 지연 로딩한다.
   - 효과: `Fetch/XHR` 요청 수와 목록 초기 로딩 부담을 줄일 수 있다.
   - 한계: 카드 표시 정보와 loading state 정책을 함께 바꿔야 한다.

DevTools `Network > JS`에서 보이는 JS 파일을 줄이는 문제는 1번이고, `Network > Fetch/XHR`에서 보이는 `incidents` API 요청을 줄이는 문제는 2번이다.

## 현재 확인된 사실

### `/login`

- `LoginPage` mount 시 incident API를 직접 호출하는 코드는 없다.
- 로그인 버튼 클릭 시 `POST /api/auth/login`만 호출된다.

### 로그인 성공 후 `/incidents`

로그인 성공 후 `App.tsx`는 `/incidents`로 이동한다. 그 뒤 `IncidentListPage`가 데이터를 가져온다.

현재 흐름:

1. `GET /api/incidents`
2. 응답 item마다 `GET /api/incidents/{incidentId}` detail 호출

따라서 로그인 직후 DevTools `Fetch/XHR`에 `incidents`, `incidents/{id}` 요청이 보이는 것은 로그인 페이지의 lazy loading 문제가 아니라 사건 목록 페이지의 데이터 로딩 정책이다.

## 방안 A: 최소 변경 JS Lazy Loading

### 선택 이유

- 변경 파일 수를 최소화할 수 있다.
- route wrapper를 별도 파일로 분리하지 않아 구조 변경이 작다.
- 지도 초기화 경로를 최대한 건드리지 않는다.
- API 요청 정책은 그대로 두므로 기능 표시 영향이 작다.

### 구현안

`App.tsx`에서 지도 의존성이 낮은 page component만 `React.lazy`로 바꾼다.

대상:

- `IncidentListPage`
- `HandoverPage`
- `OfflinePackageStatusPage`
- `IncidentClosePage`

예시:

```tsx
import { lazy, Suspense } from 'react';

const IncidentListPage = lazy(() =>
  import('../features/incidents/presentation/pages/IncidentListPage').then((module) => ({
    default: module.IncidentListPage,
  })),
);
```

route element에서는 lazy page를 `Suspense`로 감싼다.

```tsx
<Suspense fallback={null}>
  <IncidentListPage ... />
</Suspense>
```

`LoginPage`는 static import로 유지한다. 로그인 화면 자체가 즉시 보여야 하기 때문이다.

`SituationBoardPage`도 static import로 유지한다. 상황판/지도 초기화 순서를 안정적으로 유지하기 위해서다.

### 실제 구현 시 추가 확인된 점

`HandoverPage`는 `App.tsx`뿐 아니라 `SituationBoardPage.tsx`에서도 embedded workspace 용도로 static import되고 있었다.

그래서 `App.tsx`에서만 `HandoverPage`를 lazy 처리하면 빌드에서 다음 의미의 경고가 발생한다.

```text
HandoverPage.tsx is dynamically imported by App.tsx
but also statically imported by SituationBoardPage.tsx.
dynamic import will not move module into another chunk.
```

따라서 방안 A를 실제로 적용하려면 `SituationBoardPage.tsx`의 embedded `HandoverPage` import도 lazy로 바꿔야 한다.

이때도 지도 컴포넌트 자체는 변경하지 않는다.

변경 대상:

- `frontend/src/app/App.tsx`
- `frontend/src/features/situationBoard/presentation/pages/SituationBoardPage.tsx`

변경하지 않는 대상:

- `SituationBoardMap`
- `DashboardMapShell`
- `SearchMapCanvas`
- `shared/map/vworldBaseMap.ts`
- VWorld API key/env 설정

### 적용 결과

2026-05-15에 방안 A를 적용해 검증했다.

검증 명령:

```bash
cd frontend
npm run typecheck
npm run build
npx eslint src/app/App.tsx src/features/situationBoard/presentation/pages/SituationBoardPage.tsx
```

검증 결과:

- typecheck 통과
- build 통과
- 변경 파일 lint 통과
- build output에서 page chunk 분리 확인
  - `IncidentListPage-*.js`
  - `HandoverPage-*.js`
  - `OfflinePackageStatusPage-*.js`
  - `IncidentClosePage-*.js`

확인된 한계:

- `GET /incidents`, `GET /incidents/{id}` 요청 수는 줄어들지 않는다.
- `/incidents` 진입 후 detail 요청을 줄이려면 방안 C를 별도 작업으로 진행해야 한다.
- 전체 `npm run lint`는 기존 unrelated lint 오류들 때문에 실패했다. 방안 A 변경 파일 자체의 lint 문제는 없었다.
- `code-review-graph update --repo .`는 로컬에 `code-review-graph` 명령어가 없어 실행하지 못했다.

현재 상태:

- 위 구현은 현재 코드에 적용된 상태다.
- 현재 저장소에는 구현 변경과 계획 문서가 함께 남아 있다.

## 방안 B: Route Wrapper 분리 Lazy Loading

### 선택 시점

- `/login` 초기 번들에서 상황판 route wrapper, SSE hook 관련 코드까지 더 강하게 분리하고 싶을 때
- route별 chunk 경계를 더 명확히 만들고 싶을 때

### 구현 방향

`App.tsx` 내부 route wrapper를 `app/routeElements/*` 파일로 분리하고 wrapper 자체를 lazy import한다.

### 장점

- route별 chunk 경계가 명확하다.
- `/login` 초기 번들에서 상황판 관련 wrapper 코드까지 빠질 수 있다.

### 단점

- 새 파일이 여러 개 생긴다.
- props 전달 구조를 옮겨야 하므로 변경량이 커진다.
- 최소 변경 관점에서는 과하다.

### 판단

현재 단계에서는 방안 A가 더 적절하다. 방안 A 적용 후 build output을 보고 효과가 부족할 때만 방안 B를 검토한다.

## 방안 C: Incident Detail 요청 지연 로딩

### 선택 시점

- DevTools `Fetch/XHR`에서 보이는 `incidents/{id}` 요청 수를 줄이고 싶을 때
- 로그인 후 사건 목록 페이지 진입 체감 속도를 개선하고 싶을 때

### 현재 문제 지점

`frontend/src/features/incidents/presentation/pages/IncidentListPage.tsx`의 `loadIncidentCards()`는 list 응답 이후 모든 incident detail을 병렬로 가져온다.

현재 구조:

```ts
const response = await incidentReadApi.list();
const sources = await Promise.all(
  response.items.map(async (item) => ({
    item,
    detail: await incidentReadApi.detail(item.incidentId),
  })),
);
```

### 개선 방향

첫 화면은 `GET /api/incidents` 응답만으로 렌더한다.

detail은 다음 시점에만 가져온다.

- 카드가 펼쳐질 때
- 카드가 viewport에 들어왔을 때
- 사용자가 상황판으로 들어가기 직전
- 또는 서버 list API가 카드 summary를 제공하도록 개선된 뒤 detail 호출 제거

### 장점

- 실제 `Fetch/XHR` 요청 수를 줄인다.
- 사건 수가 늘어도 최초 목록 로딩 부담이 줄어든다.

### 단점

- 카드에 표시되는 장소/실종자/배정 정보가 detail에 의존한다.
- placeholder, loading state, 테스트 수정이 필요하다.
- JS lazy loading보다 기능 표시 정책 변경에 가깝다.

### 판단

DevTools 스크린샷의 `incidents/{id}` 요청을 줄이는 것이 목표라면 방안 C가 본질적인 해결책이다. 다만 UI 표시 정책 영향이 있으므로 방안 A와 별도 작업으로 진행해야 한다.

## 페이지별 데이터 로딩 확인 방법

### API 요청 확인

Chrome DevTools:

1. Network 탭 열기
2. `Disable cache` 체크
3. `Fetch/XHR` 필터 선택
4. 페이지 새로고침 또는 route 이동

기대 흐름:

| 페이지 | 현재 기대 API |
|---|---|
| `/login` | 새로고침 직후 없음. 로그인 버튼 클릭 시 `POST /auth/login` |
| `/incidents` | `GET /incidents`, 이후 각 사건 detail |
| `/incidents/:id/board` | board/detail/events |
| `/incidents/:id/handover` | board/detail/OP/memo/duty shift/summary |
| `/incidents/:id/offline-package` | package badge board/manifest/detail |

### JS 로딩 확인

Chrome DevTools:

1. Network 탭 열기
2. `Disable cache` 체크
3. `JS` 필터 선택
4. `/login` 새로고침

확인할 것:

- `/login`에서 사건 목록/인수인계/오프라인 패키지/사건 종료 JS가 바로 뜨는지
- `/incidents` 이동 시 사건 목록 JS가 추가로 뜨는지
- `/board` 이동 시 지도가 정상 렌더되는지

## 추천 순서

1. JS 초기 번들 개선이 목표라면 방안 A를 적용한다.
2. 적용 후 `npm run build` output과 DevTools `Network > JS`를 확인한다.
3. API 요청 수가 문제라면 방안 C를 별도 작업으로 진행한다.
4. 방안 A 효과가 부족할 때만 방안 B를 검토한다.
