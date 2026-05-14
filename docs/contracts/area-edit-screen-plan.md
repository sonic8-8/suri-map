# 구역 편집 화면 (W3) 설계 결정 문서

> 작성일: 2026-05-08  
> 브랜치: feature/S14P31C106-84-screen-design-docs  
> 관련 명세: `docs/screen-design/wireframes.md` W3, `docs/screen-design/screen-state-matrix.md` §3.5

---

## 1. 화면 개요

수색 구역 편집 / 분할 / 할당을 하나의 화면(W3)에서 처리한다.

- **주 사용자**: 현장 지휘관 (MP_O · SU_O · LP_O)
- **대원(MP_M)**: 읽기 전용 진입
- **레이아웃**: 좌측 도구 패널 + 중앙 지도 + 우측 구역 트리 (Hero 1 재사용)
- **진입 경로**: 상황판에서 명시적 네비게이션으로만 진입

---

## 2. 설계 결정 사항 및 근거

### 2.1 상황판 → 구역 편집 화면 자동 리디렉션 없음

**결정**: 상황판 진입 시 OVERALL 미설정 여부와 관계없이 자동으로 구역 편집 화면으로 이동하지 않는다.

**근거**:
- `screen-state-matrix.md` §3.4 (109행): 신규 사건(구역 0개) 상황판 empty 상태는 "수색을 시작하면 경로와 마커가 여기 표시됩니다" 안내 카드로 정의. 자동 리디렉션 없음.
- `anti-patterns.md` §1.6.9 (169-174행): "사건 가져온 직후 = OVERALL 0개 = 지휘관에게 그리기 CTA". 리디렉션이 아닌 CTA 유도 방식을 명시.
- `harness-scenarios.md` SC-04 (197행): "지휘 계정이 명시적으로 구역 편집 화면에 접근"하는 흐름으로 기술.

**적용**: 상황판 메인에서 "구역 편집" CTA 버튼으로 진입. 상황판 자체는 OVERALL 없어도 렌더링됨.

---

### 2.2 OVERALL 그린 후 UNIT 분할은 필수 아님

**결정**: OVERALL을 저장한 후 UNIT/TEAM 분할 없이도 화면을 벗어나거나 수색을 진행할 수 있다.

**근거**:
- `harness-scenarios.md` SC-04 red test (212행): "`overall_search_area` 변경은 타일/manifest stale만 갱신하고 `search_area` row, state, `search_area_assignment`를 생성하거나 변경하지 않는다."
  → OVERALL과 UNIT/TEAM은 완전히 독립된 작업.
- `boundaries.md` S3-1 acceptance_hints (484-500행): 수색 세션 시작 조건은 OP 필요. `search_area_assignment` 선행 필수 아님.
  → 구역 배정 없이도 수색 세션 시작 가능.
- SC-04 notes (235행): "시스템은 수색 누락을 자동 확정하거나 다음 수색 구역을 지시하지 않는다. 구역 판단은 현장 지휘관이 한다."

**적용**: OVERALL 저장 후 UNIT/TEAM 도구를 비활성화하거나 경고 모달을 띄우지 않는다. 지휘관이 자유롭게 다음 작업을 결정한다.

---

### 2.3 OVERALL 있고 폴리폰 여럿인데 TEAM 배정 0개일 때 — 배지 + 안내 카드

**결정**: 강제 분할 요구나 경고 모달 없이 우측 구역 트리 패널 상단에 도움말 톤의 안내 카드를 표시한다. 닫기 가능.

**배경**: `search_area_assignment` 없는 폴리폰은 수색 세션 자체는 시작 가능하지만, 상황판에서 어느 팀이 어느 구역 담당인지 추적 불가. 이 갭에 대한 UX가 명세에 없어 이번 설계에서 추가 결정.

**강한 유도(배정 버튼 비활성 등)를 선택하지 않은 근거**:
- SC-04 notes (235행): "구역 판단은 현장 지휘관이 한다" — 자동 판단·강제 흐름 금지 원칙.
- 소규모 작전에서 UNIT 없이 OVERALL만으로 수색하는 케이스가 실제로 유효할 수 있음.
- 기존 `partial_sync` / `stale` 배지 패턴(`screen-state-matrix.md` 114행)과 일관성 — 상태 정보 제공, 강제 아님.

**안내 카드 표시 조건**:
- OVERALL 존재 (`overall_search_area` 설정됨)
- 사건 배정 폴리폰 N개 (`incident_membership` 기준)
- TEAM 레벨 `search_area_assignment` 0개

**카피** (screen-labels.md 라벨 규칙 준수):
> "N개 폴리폰에 담당 구역이 없습니다. UNIT 분할 후 배정하세요."

---

### 2.4 화면 상태 정의

`screen-state-matrix.md` §3.5 (118-128행) 기준으로 구현할 상태:

| 상태 | 트리거 | 표현 |
|---|---|---|
| `empty` | 구역 0개 | 지도 overlay "전체 수색 구역을 먼저 그려주세요" + OVERALL 도구 강조 |
| `default` | 정상 | 도구 패널 + 지도 + 구역 트리 |
| `permission_denied` | 권한 없는 계정 진입 | 화면 전체 차단 + "접근 권한이 없습니다" + 상황판으로 돌아가기 |
| `permission_partial` | 대원(MP_M) 진입 | 모든 도구 disabled + tooltip "지휘 권한이 필요합니다" + 읽기 전용 |
| `offline` | 네트워크 없음 | 상단 배너 "오프라인 — 저장 불가" + 도구는 활성 (저장 버튼만 비활성) |
| `incident_closed` | 종료 사건 | 상단 배너 "이 사건은 종료되었습니다" + 모든 도구 disabled |
| `error` | geometry invalid | draft 유지 + 실패 사유 + 다시 시도 |

`permission_denied`와 `permission_partial` 구분 (`permission-matrix.md` §3, 164-180행):
- `permission_denied`: 화면 자체 차단 (배정 없는 계정, 채널 위반)
- `permission_partial`: 화면은 보이되 도구만 비활성 (대원 계정)

---

## 3. 구현 계획

### 수정 대상 파일

| 파일 | 변경 내용 |
|---|---|
| `frontend/src/features/areaEdit/presentation/pages/AreaEditPage.tsx` | `MOCK_PAGE_STATE` selector + 상태별 prop 전달 + permission_denied 전체 화면 |
| `frontend/src/features/areaEdit/presentation/components/AreaEditToolbar.tsx` | `disabledTools`, `highlightedTool`, `tooltipText` props 추가 |
| `frontend/src/features/areaEdit/presentation/components/AreaEditMap.tsx` | `pageState` prop + 상태별 overlay |
| `frontend/src/features/areaEdit/presentation/components/AreaHierarchyPanel.tsx` | 미배정 안내 카드 + StatusBadge 교체 |
| `frontend/src/features/areaEdit/presentation/constants/mockAreaEdit.ts` | `MOCK_UNASSIGNED_PHONE_COUNT` 추가 |

### AreaEditToolbar 상태별 disabledTools

```
empty          → disabledTools: ['unit', 'team', 'complete'], highlightedTool: 'overall'
permission_partial → disabledTools: ['overall', 'unit', 'team', 'complete'], tooltip 활성
incident_closed    → disabledTools: ['overall', 'unit', 'team', 'complete']
```

### 미배정 폴리폰 안내 카드 (AreaHierarchyPanel 상단)

```
┌──────────────────────────────────────┐
│ ⓘ 3개 폴리폰에 담당 구역이 없습니다.  │
│   UNIT 분할 후 배정하세요.            │
│                                [✕]  │
└──────────────────────────────────────┘
```

- 색상: `--suri-board-fg-muted` (정보 톤, 경고색 사용 안 함)
- `[✕]` 클릭 시 세션 중 dismiss (로컬 state)
- `unassignedPhoneCount === 0` 또는 dismiss 시 미표시

---

## 4. 검증 체크리스트

- [ ] `MOCK_PAGE_STATE = 'empty'` → 지도에 "먼저 그려주세요" overlay, OVERALL 버튼만 강조
- [ ] `MOCK_PAGE_STATE = 'permission_partial'` → 모든 도구 disabled, hover 시 tooltip 표시
- [ ] `MOCK_PAGE_STATE = 'permission_denied'` → 전체 화면 차단 메시지, 도구 패널 미표시
- [ ] `MOCK_PAGE_STATE = 'offline'` → 상단 배너, 도구는 활성
- [ ] `MOCK_PAGE_STATE = 'incident_closed'` → 상단 배너, 모든 도구 disabled
- [ ] `MOCK_UNASSIGNED_PHONE_COUNT = 3` → 우측 패널 상단 안내 카드 표시
- [ ] `MOCK_UNASSIGNED_PHONE_COUNT = 0` → 안내 카드 미표시
- [ ] 안내 카드 `[✕]` 클릭 → dismiss 확인
- [ ] 구역 상태 배지가 StatusBadge(shared) 컴포넌트로 표시됨

---

## 5. 열린 이슈 (미결)

- **실제 지도 그리기 인터랙션** (maplibre-gl polygon drawing): 이번 범위 외, 별도 작업 필요
- **구역 저장 API 연결**: Mock 단계. S2 `POST /search-areas`, `POST /search-areas/{id}/split` 연동은 추후
- **상황판 우측 폴리폰 패널 미배정 배지**: 상황판(SituationBoardPage) 측 작업 필요 (이번 범위 외)
