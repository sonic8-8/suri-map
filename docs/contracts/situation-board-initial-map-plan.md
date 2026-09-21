# 상황판 최초 지도 범위의 설계 배경

전체 수색 구역이 아직 없는 사건도 상황판에서 확인할 수 있어야 한다는 설계 기록이다. DTO 예시·요청 순서를 중복 관리하지 않고 의도와 구현 차이를 남긴다.

## 당시 선택

최초 지도 범위는 다음 순서로 정하려 했다.

1. 활성 전체 수색 구역의 bbox 또는 Polygon 범위
2. 사건을 가져올 때 등록한 초기 기준 마커의 위치 범위
3. 관할 지역 또는 기본 지도 범위

사건 상세의 실종자 위치 설명을 좌표로 간주하지 않는다. 기준 마커가 없어도 화면 전체를 실패시키지 않고 기본 지역을 보여준다. 마커 위치를 둘러싼 가상의 구역이나 현재 화면 범위를 공식 수색 구역으로 자동 저장하지 않는다.

전체 구역이 없으면 웹의 구역 지정 동선을 제공하되 자동 이동은 하지 않는 설계였다. 구역 분할·배정과 오프라인 준비 완료는 각각 [구역](../search-area.md)·[패키지](../offline-package.md)의 선행 조건을 확인한다. 지도 표시와 패키지 준비 완료는 같은 상태가 아니다.

## 현재 코드와 차이

2026-09-21 정적 확인이며 타일·브라우저 동작을 새로 시험한 결과는 아니다.

- [getAssignedSearchAreaBounds / resolveInitialMapView](../../frontend/src/features/situationBoard/presentation/components/map/searchMapCanvasData.ts)는 전체 구역 Polygon → 다른 구역 Polygon → 전달받은 기본 범위 순으로 처리한다.
- [SearchMapCanvas](../../frontend/src/features/situationBoard/presentation/components/map/SearchMapCanvas.tsx)는 기본 범위로 `GWANGJU_BBOX`를 전달한다. 이 초기 범위 결정 함수는 기준 마커를 입력으로 받지 않는다. 원문의 “기준 마커 우선 표시”가 구현됐다고 설명하지 않는다.
- 기존 [상황판 요구](../situation-board.md)에는 “전체 범위 → 최근 활동 → 기본 지역” 표현도 있다. 기준 마커와 최근 활동 중 무엇을 우선할지는 같은 결정으로 단정하지 않고 지도 작업에서 확인한다.
- 구역 부재와 권한·통신 실패를 구분하고, 재조회 실패가 이미 표시한 사건 맥락을 불필요하게 지우지 않는 요구를 남긴다.

## 원문

당시 API·DTO 예시와 상태별 상세는 [정리 전 원문](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/contracts/situation-board-initial-map-plan.md)에 있다. 이 예시를 현재 공개 필드·enum·새 API의 근거로 사용하지 않는다.
