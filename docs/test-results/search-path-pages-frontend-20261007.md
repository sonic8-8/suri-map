# 상황판 경로 페이지 — 프런트엔드 연결·로컬 검증

2026-10-07. Q63에서 승인한 지도·관련 화면·SSE 갱신·오류 복구 연결과 로컬 검증이다.
서버 배포·DB migration·실제 브라우저·본 부하·커밋·푸시는 하지 않았다.

## 바뀐 흐름

1. 기존 상황판 GET은 경로를 제외한 슬롯을 조회한다. 기존 서버 API의 기본 응답과 Android는 변경하지 않았다.
2. 새 Segments/Changes POST가 경로의 끝부분과 과거 이력·변경분을 받는다. 같은 사건·차수 범위의 화면은 TanStack Query 캐시와 진행 중 요청을 공유한다.
3. 경로별 최초 기준·이력 진행·변경분 진행을 구분한다. 구간은 자체 버전으로 병합하고 경로 상태는 부모 버전으로 비교한다. 10진 문자열 버전은 BigInt로 비교하며 지도 원본 버전도 문자열을 유지한다.
4. 한 범위의 HTTP 요청은 한 번에 하나만 진행한다. 변경 신호는 합치고, 이력과 대기 중인 변경분을 번갈아 조회한다. 마지막 화면 이탈 때 요청·재시도 대기를 취소한다.
5. 받은 구간부터 기존 지도 변환에 전달한다. 미수신 구간 사이를 잇지 않으며 정상 빈 경로를 예전 경로·fallback 자료로 채우지 않는다. 지도·확대 지도·구역 상세·차수 검토에서 부분 수신을 알린다.
6. 통신 실패와 합의한 HTTP 오류는 무작위 간격으로 자동 복구한다. Retry-After를 더하고 숨김 중 실패 재시도만 보류한다. 브라우저의 오프라인 판정만으로 조회를 막지 않는다. 성공 전에는 오류와 마지막 조회 성공 시각을 구분한다.
7. 권한 거부·종료 시 위치 표시와 경로 조회를 중단한다. 로그아웃·로그인 전환·인증 만료 때 Query 캐시와 요청을 정리한다.

새 라이브러리는 추가하지 않았다. 기존 QueryClient·API client·지도 변환·일반 재조회 합치기를 재사용했다.
기존 사건 SSE 구독을 useIncidentBoardEvents로 추출했다. 단독 차수 검토 화면도 재조회 신호를 받으며, 상황판 안에서는 부모 구독을 공유한다. 기존 화면 테스트로 두 연결 조건을 확인했다.
경로 변경은 SSE 재조회뿐 아니라 기존 수동 경로 저장·구간 보정 성공 뒤에도 연결했다.
이 작업으로 기존 웹 수동 경로 쓰기의 채널·기능 정책까지 재검증한 것은 아니다.

## 검증

실행 위치: `frontend/`.

| 명령 | 결과 |
|---|---|
| `npm run typecheck` | 통과 |
| `npm run build` | 통과. 500kB 초과 번들 경고 있음 |
| `npm test -- --reporter=dot` | 60개 파일·302개 테스트 통과 |
| 마지막 차수 검토 재검사 | 구독 공유/단독 조건 기대값 추가 후 12개 통과 |
| 새 페이지/복구/구독·세션 테스트 9개 파일 Prettier 검사 | 통과 |
| `git diff --check` | 통과 |
| API·이번 결과 문서의 로컬 링크 | 대상 34개 존재 확인 |

새 검사는 실제 QueryClient와 React 훅을 사용하며 HTTP 응답을 대체한다.
페이지 교대·변경 신호 합치기·여러 화면의 요청 공유·취소·차수 변경·큰 버전 비교·늦은 이력 응답·신규 경로 발견·정상 빈 결과·미수신 구간 연결 방지·권한 거부·숨김/오프라인/재시도·로그아웃 정리를 확인했다.
기존 SSE 훅 검사는 새 경로 조회를 대체하고, 별도 합성 훅 검사에서 실제 두 조회 흐름을 연결했다.
모든 테스트가 실제 HTTP·DB·지도 렌더링을 수행한 것은 아니다.

초기 전체 검사에서는 예전 조회 키·단일 API mock을 가정한 기존 테스트 31개가 실패했다.
해당 테스트 입력을 새 책임 경계로 바꾸고, 새 페이지 흐름은 별도로 검증한 뒤 전체를 다시 통과했다.
자동 재시도를 연결한 뒤 기존 “다음 이벤트로 즉시 재조회” 기대도 합의한 “기존 대기 유지 후 자동 복구”로 수정했다.
미변경 OfflinePackageStatusPage 검사에서 React act 경고가 남는다. 이를 새 지도 동작 실패로 판정하지 않았다.

2026-10-07 커밋 준비 중 같은 전체 명령을 다시 실행했다. 타입 검사·빌드와 60개 파일의 302개 테스트가 모두 통과했다. 번들 크기·React act 경고는 그대로이며, 실제 서버 연결이나 배포 검증은 하지 않았다.

## 코드 리뷰에서 발견한 문제와 수정

2026-10-07 Q66 승인으로 기존 테스트에 실패 조건을 먼저 추가한 뒤 수정했다. 비교 범위는 마지막 배포 커밋 `a7142200` 이후의 미커밋 변경과 새 파일이다.

| 문제 | 수정 |
|---|---|
| 원본 좌표가 하나인 구간의 응답을 LineString 형식 검사에서 거부했다. | 서버 응답에서만 같은 좌표를 두 번 표현한다. 웹 훅이 이를 받아 지도 입력을 만들고 변경분 조회를 마치는지 확인했다. DB 원본과 GPS 순번은 유지한다. |
| 다른 차수 범위의 캐시로 현재 캐시를 통째로 바꾸면 현재 범위에만 있던 경로가 사라졌다. | 현재 경로를 보존하고 공통 경로의 구간을 각 구간 버전으로 합친다. |
| 방문했던 차수 범위로 돌아오면 다른 화면에서 받은 최신 구간·진행을 사용하지 못했다. | 재진입 때 다른 범위의 공통 경로를 합친 뒤 조회한다. 주기적 폴링을 추가한 것은 아니다. |
| 서로 다른 최초 기준의 이력 완료 상태까지 합치면 아직 받지 못한 중간 구간을 건너뛸 수 있었다. | 최초 기준이 다르면 이력·변경분 진행은 현재 값을 유지한다. 좌표를 합쳤다는 이유로 조회 완료까지 공유하지 않는다. |

동시 범위·왕복 전환·서로 다른 최초 기준의 세 조건은 실제 QueryClient와 조회 훅에서 수정 전 실패와 수정 후 통과를 확인했다. HTTP 응답은 대체하며 실제 브라우저 시험은 아니다.

상황판과 경로 feature를 서로 직접 참조하던 조합은 `app/board`로 옮겼다. `usePagedIncidentBoardQuery.ts`, `useIncidentBoardEvents.ts`, `refreshSituationBoards.ts`가 두 조회의 조합을 맡고, 경로 훅에는 기존 재시도·계측 함수를 전달한다. 새 라이브러리나 별도 캐시 저장소는 추가하지 않았다.

최종 실행은 `npm run typecheck && npm run build && npm test -- --reporter=dot`이며 **60개 파일·306개 테스트와 타입 검사·빌드가 통과**했다. 기존 번들 크기·React act 경고는 남는다. 관련 훅·조합 검사만 실행한 17개도 통과했다. 두 축 리뷰에서 지적한 항목을 수정한 뒤 Standards·Spec의 잔여 지적은 각각 0건이다. 이는 배포·성능·실제 서버 통합 통과를 의미하지 않는다.

## 남은 검증과 결정

- Backend 두 API와 실제 브라우저를 연결한 전체 경로 표시·신규 GPS 반영·지도 조작/확대·차수 전환은 아직 확인하지 않았다.
- 서버 API는 기본 비활성이다. 기존 자료 보완·검증과 활성화 설정 없이 프런트엔드만 배포하면 새 조회가 동작하지 않는다.
- 선택한 응답 시험 한도는 80경로·80구간·480좌표·경로당 1구간이다. 이번에 서버 설정이나 운영 기본값으로 반영하지 않았다.
- 후속 합의에서 요청/조회 범위 경로 수 시험 한도는 1,000으로 선택했다([설정·적용 범위](../api/search-path-pages.md#시험-한도와-활성화)). HTTP 요청 제한시간 숫자는 미확정이다. 제한시간을 임의로 넣지 않았으므로 응답이 영원히 끝나지 않는 요청의 시간 초과 복구는 아직 구현·검증하지 않았다.
- 첫 지도 표시·전체 이력 완료·새 좌표 반영 시간과 동시 상황판 성능을 측정해야 한다. 앞선 로컬 Backend 첫 페이지 시간으로 브라우저 성능 개선을 주장하지 않는다.
- 구역 상세의 pathCount는 실제로 이동 구간 수다. 이름 후보에 등록했으며 집계 정의 자체는 바꾸지 않았다.

[기능 요구·합의](../features/situation-board.md), [API 예시](../api/search-path-pages.md), [Backend 로컬 결과](search-path-pages-local-20261006.md)를 함께 확인한다.

## 이번 변경 파일

이전부터 남아 있던 Backend 저장/조회 구현은 이번 변경 파일에 포함하지 않는다.

- [src/app/board/useIncidentBoardEvents.ts](../../frontend/src/app/board/useIncidentBoardEvents.ts)

- [src/app/useAppSession.test.tsx](../../frontend/src/app/useAppSession.test.tsx)
- [src/app/board/usePagedIncidentBoardQuery.test.tsx](../../frontend/src/app/board/usePagedIncidentBoardQuery.test.tsx)
- [src/app/board/usePagedIncidentBoardQuery.ts](../../frontend/src/app/board/usePagedIncidentBoardQuery.ts)
- [src/app/board/refreshSituationBoards.ts](../../frontend/src/app/board/refreshSituationBoards.ts)
- [src/features/board/model/boardReadRecovery.ts](../../frontend/src/features/board/model/boardReadRecovery.ts)
- [src/features/path/api/searchPathPagesApi.test.tsx](../../frontend/src/features/path/api/searchPathPagesApi.test.tsx)
- [src/features/path/api/searchPathPagesApi.ts](../../frontend/src/features/path/api/searchPathPagesApi.ts)
- [src/features/path/model/searchPathPages.test.ts](../../frontend/src/features/path/model/searchPathPages.test.ts)
- [src/features/path/model/searchPathPages.ts](../../frontend/src/features/path/model/searchPathPages.ts)
- [src/app/useAppSession.ts](../../frontend/src/app/useAppSession.ts)
- [src/features/board/api/incidentBoardApi.ts](../../frontend/src/features/board/api/incidentBoardApi.ts)
- [src/features/board/model/incidentBoardMerge.ts](../../frontend/src/features/board/model/incidentBoardMerge.ts)
- [src/features/handover/presentation/components/HandoverComparisonMap.tsx](../../frontend/src/features/handover/presentation/components/HandoverComparisonMap.tsx)
- [src/features/handover/presentation/pages/HandoverPage.test.tsx](../../frontend/src/features/handover/presentation/pages/HandoverPage.test.tsx)
- [src/features/handover/presentation/pages/OperationalPeriodReviewWorkspace.tsx](../../frontend/src/features/handover/presentation/pages/OperationalPeriodReviewWorkspace.tsx)
- [src/features/path/api/searchPathApi.ts](../../frontend/src/features/path/api/searchPathApi.ts)
- [src/features/situationBoard/presentation/components/map/DashboardMapShell.tsx](../../frontend/src/features/situationBoard/presentation/components/map/DashboardMapShell.tsx)
- [src/features/situationBoard/presentation/components/map/SearchAreaInspectorCard.tsx](../../frontend/src/features/situationBoard/presentation/components/map/SearchAreaInspectorCard.tsx)
- [src/features/situationBoard/presentation/components/map/SearchMapCanvas.tsx](../../frontend/src/features/situationBoard/presentation/components/map/SearchMapCanvas.tsx)
- [src/features/situationBoard/presentation/components/map/SituationBoardMap.module.css](../../frontend/src/features/situationBoard/presentation/components/map/SituationBoardMap.module.css)
- [src/features/situationBoard/presentation/components/map/SituationBoardMap.tsx](../../frontend/src/features/situationBoard/presentation/components/map/SituationBoardMap.tsx)
- [src/features/situationBoard/presentation/hooks/useSituationBoardData.test.ts](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.test.ts)
- [src/features/situationBoard/presentation/hooks/useSituationBoardData.ts](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardData.ts)
- [src/features/situationBoard/presentation/hooks/useSituationBoardPageState.ts](../../frontend/src/features/situationBoard/presentation/hooks/useSituationBoardPageState.ts)
- [src/features/situationBoard/presentation/pages/SituationBoardPage.tsx](../../frontend/src/features/situationBoard/presentation/pages/SituationBoardPage.tsx)
- [src/shared/api/client.test.ts](../../frontend/src/shared/api/client.test.ts)
- [src/shared/api/client.ts](../../frontend/src/shared/api/client.ts)
- [src/shared/model/boardMapSlots.test.ts](../../frontend/src/shared/model/boardMapSlots.test.ts)
- [src/shared/model/boardMapSlots.ts](../../frontend/src/shared/model/boardMapSlots.ts)
- [src/shared/model/situationBoardViewModel.ts](../../frontend/src/shared/model/situationBoardViewModel.ts)

공유 기록: 이 문서, `docs/features/situation-board.md`, `docs/api/search-path-pages.md`, `docs/refactoring/codebase-naming-candidates.md`.
