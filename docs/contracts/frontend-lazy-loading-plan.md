# 프론트엔드 초기 로딩 개선 기록

2026-05-15의 변경 이유·대안·검증 기록이다. 당시 적용 결과와 현재 코드를 구분한다.

## 서로 다른 두 문제

| 문제 | 당시 접근 | 한계 |
|---|---|---|
| 첫 화면에 필요 없는 JavaScript 로딩 | 페이지를 `React.lazy`로 불러오고 `Suspense` 사용 | API 요청 수는 줄지 않음 |
| 사건 목록 뒤 각 사건의 상세 요청 발생 | 펼침·화면 진입 시 상세 조회 또는 목록 응답에 요약 제공 검토 | 카드 정보·로딩·실패 표시가 달라지므로 별도 합의 필요 |

지도 초기화 변경을 줄이기 위해 페이지 단위 지연 로딩을 먼저 선택했다. 라우트 전체를 분리하는 대안은 효과가 부족할 때 검토하기로 했다. 같은 페이지를 다른 곳에서 정적으로 import하면 동적 import만으로 별도 chunk가 되지 않았던 점을 확인했다.

## 당시 기록한 결과

- `typecheck`·`build`·변경 파일 lint 통과, 사건 목록·인수인계·오프라인 패키지·사건 종료 페이지 chunk 분리를 기록했다.
- 전체 lint는 기존 오류로 실패했고, 당시 사용하려던 `code-review-graph` 명령은 설치되지 않아 실행하지 못했다.
- API 요청 감소나 로딩 시간 개선의 측정값은 없다. 위 결과를 체감 속도 개선의 수치 근거로 사용하지 않는다.

## 현재 확인 위치

2026-09-21 정적 대조이며 이번에 빌드·성능 측정을 다시 실행하지 않았다.

- 현재 [라우트 연결](../../frontend/src/app/App.tsx)은 별도 adapter를 사용한다. [사건 목록](../../frontend/src/app/routeAdapters/IncidentListRoute.tsx)·[사건 상세](../../frontend/src/app/routeAdapters/IncidentDetailRoute.tsx)·[사건 종료](../../frontend/src/app/routeAdapters/IncidentCloseRoute.tsx)에 지연 import가 있다.
- [오프라인 패키지](../../frontend/src/app/routeAdapters/OfflinePackageRoute.tsx)는 정적 import다. [상황판](../../frontend/src/features/situationBoard/presentation/pages/SituationBoardPage.tsx) 안의 `HandoverPage`도 정적 import이고, [기존 인수인계 라우트](../../frontend/src/app/routeAdapters/HandoverRoute.tsx)는 수색 이력 화면으로 이동한다. 과거 네 페이지가 지금도 모두 분리된다는 설명은 유지하지 않는다.
- [IncidentListPage.loadIncidentCards](../../frontend/src/features/incidents/presentation/pages/IncidentListPage.tsx)는 목록 조회 뒤 각 상세를 병렬 조회한다. 상세 실패는 해당 카드의 대체 표시로 처리한다. 요청 수를 줄이는 후속 작업은 남아 있다.

새 최적화 전에는 같은 사건 수·캐시 조건에서 JS chunk와 Fetch/XHR를 따로 관찰한다. 반복 측정 없이 라우트 분리나 추가 캐시부터 도입하지 않는다.

## 원문

당시 명령·코드 예시·세 대안은 [정리 전 원문](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/contracts/frontend-lazy-loading-plan.md)에서 복원한다.
