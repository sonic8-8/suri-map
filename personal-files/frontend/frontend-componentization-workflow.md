# Suri-Map Frontend 컴포넌트화 작업 흐름

이 문서는 와이어프레임을 기준으로 프론트 화면을 실제 TypeScript/React 컴포넌트로 나눌 때의 작업 순서를 정리한 메모다.

전제:

- 현재 기준 feature는 `situationBoard`다.
- 화면은 `frontend/AGENTS_front.md` 기준으로 `presentation -> domain <- data` 구조를 따른다.
- 아직 API나 SSE를 붙이기 전이라면 `presentation`과 `shared/ui`부터 시작해도 된다.

## 먼저 할 일

가장 먼저 만들 것은 페이지가 아니라 `page shell`이다.

즉, 아래 순서로 간다.

1. `SituationBoardPage`를 만든다.
2. 페이지 안에서 `헤더 / 좌측 패널 / 지도 / 우측 패널`의 4영역을 나눈다.
3. 각 영역을 작은 컴포넌트로 쪼갠다.
4. 반복되는 버튼, 칩, 토글은 `shared/ui`로 올린다.
5. 화면에 들어갈 mock 데이터는 `presentation/constants`에 둔다.
6. 실제 데이터가 필요해질 때만 `domain`과 `data`를 붙인다.

## 추천 시작점

```text
src/features/situationBoard/presentation/pages/
└── SituationBoardPage.tsx
```

이 파일은 와이어프레임 전체를 조립하는 입구다.

여기서 바로 화면 전체를 다 구현하지 말고, 아래처럼 나눠서 넣는다.

```text
src/features/situationBoard/presentation/components/
├── SituationBoardHeader.tsx
├── SituationBoardLeftPanel.tsx
├── SituationBoardMap.tsx
└── SituationBoardRightPanel.tsx
```

## 영역별 분해

### 1. 헤더

헤더는 사건 정보, 현재 OP, 동기화 상태, 상단 액션 버튼을 담는다.

예시 컴포넌트:

- `SituationBoardHeader.tsx`
- `IncidentSummary.tsx`
- `CurrentOpBadge.tsx`
- `BoardHeaderActions.tsx`

헤더는 페이지 최상단에서 공통으로 보이므로, 너무 세부적인 정보까지 한 파일에 넣지 않는 편이 낫다.

### 2. 좌측 패널

좌측 패널은 와이어프레임에서 조작이 가장 많은 곳이다.

예시 컴포넌트:

- `OperationalPeriodSelector.tsx`
- `LayerTogglePanel.tsx`
- `MarkerTypeFilter.tsx`
- `ViewModeSwitch.tsx`
- `BoardNavigationLinks.tsx`

좌측 패널은 상태가 많아서, 한 파일에 다 넣으면 바로 읽기 어려워진다.  
라벨 목록, 토글 목록, 버튼 그룹은 각각 따로 빼는 쪽이 좋다.

### 3. 지도

지도는 처음부터 MapLibre를 붙이지 않아도 된다.  
먼저 레이아웃과 오버레이를 맞추고, 나중에 실제 지도를 연결한다.

예시 컴포넌트:

- `SituationBoardMap.tsx`
- `SearchMapCanvas.tsx`
- `SearchAreaOverlay.tsx`
- `SearchPathLayer.tsx`
- `DeviceLocationLayer.tsx`
- `MarkerLayer.tsx`
- `MapControls.tsx`
- `MapLegend.tsx`
- `OperationalPeriodOverlay.tsx`

지도가 가장 복잡하므로, 맨 먼저 “그림처럼 보이는 상태”를 만들고 그 뒤에 상호작용을 붙이는 게 안전하다.

### 4. 우측 패널

우측 패널은 상태 요약과 보조 정보가 중심이다.

예시 컴포넌트:

- `DeviceStatusList.tsx`
- `SearchAreaTree.tsx`
- `RecentMarkerList.tsx`
- `OfflinePackageNotice.tsx`

이 영역은 리스트형 UI가 많아서, 공통 row 컴포넌트가 생기기 쉽다.  
반복이 보이면 `DeviceStatusRow`, `MarkerListRow`처럼 더 쪼개면 된다.

## 전역 공용 UI

여러 화면에서 계속 쓰일 가능성이 큰 것만 `shared/ui`에 둔다.

예시:

```text
src/shared/ui/
├── Button.tsx
├── IconButton.tsx
├── Chip.tsx
├── Toggle.tsx
└── SectionTitle.tsx
```

기준은 단순하다.

- 한 feature에서만 쓰면 feature 안에 둔다.
- 두세 feature 이상에서 반복되면 `shared/ui`로 올린다.

## mock 데이터 위치

초기에는 API 없이 화면을 맞추는 경우가 많다.  
그럴 때는 mock 데이터를 컴포넌트 파일 안에 넣지 말고 `constants`로 뺀다.

예시:

```text
src/features/situationBoard/presentation/constants/
└── mockSituationBoard.ts
```

여기에 넣을 것:

- OP 목록
- 마커 목록
- 구역 트리
- 최근 마커
- 레이어 토글 초기값

## 상태와 동작

화면 상태가 생기면 `hooks`로 뺀다.

예시:

```text
src/features/situationBoard/presentation/hooks/
└── useSituationBoard.ts
```

이 훅에는 아래 같은 것만 둔다.

- 선택 상태
- 필터 상태
- 패널 열림/닫힘 상태
- 버튼 클릭 핸들러
- usecase 호출 준비

여기에는 두지 말 것:

- 실제 서버 계약 변환
- repository 구현
- 도메인 규칙 판단

## 스타일 적용 순서

디자인을 입힐 때는 다음 순서가 안정적이다.

1. 페이지 레이아웃을 먼저 잡는다.
2. 큰 패널 4개를 맞춘다.
3. 각 패널 안의 반복 UI를 component로 쪼갠다.
4. `shared/ui`로 올릴 후보를 선별한다.
5. mock 데이터로 밀도와 간격을 맞춘다.
6. 마지막에 실제 데이터와 연결한다.

스타일은 처음부터 다 나누기보다, 화면이 어느 정도 완성된 뒤에 파일 분리를 하는 편이 낫다.

## 추천 작업 순서

실제로 손을 댈 때는 이 순서가 좋다.

1. `SituationBoardPage.tsx` 만들기
2. `SituationBoardHeader.tsx` 만들기
3. `SituationBoardLeftPanel.tsx` 만들기
4. `SituationBoardMap.tsx` 만들기
5. `SituationBoardRightPanel.tsx` 만들기
6. 지도 내부 컴포넌트 세분화
7. 우측 패널 리스트 세분화
8. 반복되는 버튼과 칩을 `shared/ui`로 올리기
9. mock 데이터를 `constants`로 옮기기
10. 그다음에 `domain/data`를 붙이기

## 한 줄 기준

어디에 둘지 헷갈리면 이렇게 보면 된다.

- 화면 입구면 `pages`
- 보이는 조각이면 `components`
- 상태와 동작이면 `hooks`
- 여러 화면 공용이면 `shared/ui`
- 규칙과 타입이면 `domain`
- 서버랑 주고받으면 `data`
- 화면 전체 조립이면 `app`
