# KRDS-Ops Dense Variant 토큰

상태: 1차 결정 완료 (2026-05-05). MVP 시연 baseline. 변경은 high-fi 검증 후 별도 PR.

## 목적

KRDS는 시민 포털 디자인 시스템이다. 본문 17px, 카드 padding 40px, line-height 1.5 같은 기본값은 **정책 정보를 여유 있게 보여주기 위한 톤**이다. Suri-Map의 웹 상황판은 사건 1건당 폴리폰 약 12~15대·마커 수십 개·사용자 선택 OP overlay를 동시 표시해야 하는 **운영 dashboard**이고, 폴리폰(현장 단말)은 **야외 글랜스 + 장갑 조작** 환경이다. 두 사용 맥락 모두 KRDS 기본 토큰을 그대로 쓰면 정보 밀도와 가독성이 부족하다.

이 문서는 KRDS를 base로 fork한 **KRDS-Ops dense variant**의 토큰을 정의한다.

## 디자인 시스템 조합

- **base**: KRDS (범정부 UI/UX 공통가이드) — 출처: 프로젝트 루트의 `Korean Government UIUX Design System/`
- **fork**: KRDS-Ops dense variant — 정보 밀도·rugged field UI를 위한 압축 + 한국 경찰 도메인 컬러
- **borrow**: 네이버 지도 — 지도 조작 패턴(컨트롤 위치, 바텀시트 조작감)만. 검색·POI·길찾기 등 비채택
- **domain**: 한국 경찰 실종 수색 — 차량/도보 구간, 5종 마커, OP/DutyShift 시각 분리, 단말 stale·미전송 큐 표현 등

## 명명 규칙

- 원본 KRDS 토큰: `--krds-*`
- Suri-Map ops fork: `--krds-ops-*`
- 두 토큰을 같은 화면에 섞지 않는다. 화면 단위로 어느 셋을 쓰는지 결정한다.
- 한 토큰의 값을 바꿀 때는 §10 영향표를 먼저 본다.

## 1. 정보 밀도 목표

토큰 값은 이 목표를 만족하도록 역산한다. 슬롯은 field-context.md §2와 동기화한다.

> 1차 산정 운영 컨텍스트는 [field-context.md §2.0.1](./field-context.md)(광주 광역시 시연 기준) 참조. 검증 필요 항목은 △로 표기. △ 항목은 시연 baseline freeze이며, v2 검토 시 갱신.

### 1.1 웹 상황판 (1920×1080)

| 항목 | 1차 산정값 | 비고 |
|---|---|---|
| 사건 1건 동시 표시 폴리폰 수 (편안한 한계) | **약 12~15대** △ | 광주 광역 기준 추정 |
| 사건 1건 동시 표시 폴리폰 수 (cluster 임계) | **20대 초과 시 cluster** | 그 이상 식별 어려움 |
| 사건 1건 동시 표시 마커 수 | **40개** | 5종 누적 + cluster |
| OP 누적 차수 상한 | **없음** | 재수색은 발견 시까지 (FR-12). 50차+ 가능 |
| OP 동시 overlay 선택 수 | **사용자 선택, 기술 상한 없음** | FR-11 |
| OP 동시 overlay 시각 권장 한계 | **약 5차** | 그 이상 선택 시 가독성 경고 |
| OP 리스트 한 화면 표시 행 수 | **10~12행** | 그 이상 스크롤 |
| 사건 동시 활성 표시 수 (실종팀 지휘) | **3건** △ | |
| 사건 1건당 DutyShift timeline 폭 | **24시간 (3교대)** △ | |
| 좌측 사이드바 폭 | **280px** | collapsible 60px / 비-resizable (MVP) |
| 우측 상세 패널 폭 | **360px** | on-demand / 비-resizable (MVP) |
| 데이터 리스트 동시 행 수 (폴리폰) | **8행** | 그 이상 스크롤 |
| 데이터 리스트 동시 행 수 (마커/구역) | **6행** | 그 이상 스크롤 |

### 1.2 폴리폰 (412×892 기준)

| 항목 | 1차 산정값 | 비고 |
|---|---|---|
| 지도 점유율 (바텀시트 닫힘) | **100%** | |
| 지도 점유율 (바텀시트 펼침) | **최소 50%** | |
| 바텀시트 thumb zone 도달률 (primary CTA) | **100%** | field-context §1.1 |
| 한 화면 동시 정보 단위 상한 | **상시 배지 3 + 강조 알림 1** | |
| 미전송 큐 강조 임계값 | **5건** | |
| 미전송 큐 경고 임계값 | **20건** | |
| 마커 생성 바텀시트 마커 유형 표출 | **5종 한 화면, 스크롤 없음** | FR-30. 2×3 또는 5×1 그리드 |
| 활성 사건 컨텍스트 | **1 사건** | |

## 2. Color 토큰

### 2.1 KRDS-Ops Web (상황판 — 라이트 모드 기본)

KRDS 원본 컬러 base + 도메인 컬러 추가. KRDS의 형식 톤(저채도·플랫·고대비)을 유지.

| 토큰 | 값 | 용도 | KRDS와의 관계 |
|---|---|---|---|
| `--krds-ops-bg-base` | `#F8F8F8` | 화면 배경 | KRDS subtle alternate |
| `--krds-ops-bg-surface` | `#FFFFFF` | 카드/패널 배경 | KRDS surface |
| `--krds-ops-bg-elevated` | `#FFFFFF` + shadow | 모달/팝오버 | shadow는 §6 |
| `--krds-ops-bg-muted` | `#EDF1F5` | 비활성 영역 | KRDS muted |
| `--krds-ops-fg-primary` | `#1D1D1D` | 본문 | KRDS primary text |
| `--krds-ops-fg-secondary` | `#555555` | 부가 정보 | KRDS secondary |
| `--krds-ops-fg-muted` | `#8E8E8E` | placeholder/disabled | KRDS placeholder |
| `--krds-ops-border` | `#D8D8D8` | 일반 보더 | KRDS default border |
| `--krds-ops-border-strong` | `#C6C6C6` | 강조 보더 | KRDS stronger border |
| `--krds-ops-primary` | `#246BEB` | base 액션 색 | KRDS primary blue 유지 |
| `--krds-ops-primary-hover` | `#1D56BC` | hover 액션 | KRDS primary darker |
| `--krds-ops-primary-pastel` | `#EFF5FF` | 선택 강조 배경 | KRDS pastel primary |
| `--krds-ops-emphasis` | `#D63A3A` | 실종자 발견·지원 요청·destructive | 도메인 강조. KRDS 외 |
| `--krds-ops-emphasis-bg` | `#FCE9E9` | 강조 알림 배경 | |
| `--krds-ops-warning` | `#E8881A` | stale, 패키지 미완료, 동기화 실패 | 도메인 |
| `--krds-ops-warning-bg` | `#FCF1E2` | 경고 배경 | |
| `--krds-ops-success` | `#1F8754` | 구역 완료, 동기화 성공 | 도메인 |
| `--krds-ops-success-bg` | `#E5F4EC` | 성공 배경 | |
| `--krds-ops-vehicle` | `#0066CC` | 차량 구간 경로 (실선) | 도메인. 파랑 |
| `--krds-ops-walk` | `#9333EA` | 도보 구간 경로 (실선) | 도메인. 보라 |
| `--krds-ops-unknown-segment` | `#8E8E8E` (점선 dash) | 미분류 구간 | 도메인. 회색 dash |
| `--krds-ops-area-active-fill` | `rgba(36,107,235,0.10)` | 활성 구역 fill | 파랑 10% |
| `--krds-ops-area-active-stroke` | `#246BEB` | 활성 구역 stroke | |
| `--krds-ops-area-completed-fill` | `rgba(31,135,84,0.12)` | 완료 구역 fill | 초록 12% |
| `--krds-ops-area-completed-stroke` | `#1F8754` | 완료 구역 stroke | |
| `--krds-ops-area-cancelled-fill` | `rgba(142,142,142,0.08)` | 취소 구역 fill | 회색 8% |
| `--krds-ops-area-overall-stroke` | `#1D1D1D` (1.5px dash) | 전체 수색 구역 outline | OVERALL 강조 |
| `--krds-ops-marker-clue` | `#FFB020` | 단서 마커 | 노랑 |
| `--krds-ops-marker-found` | `#D63A3A` | 발견 마커 | 강조 빨강 |
| `--krds-ops-marker-field` | `#94A3B8` | 지형 마커 | 청회색 |
| `--krds-ops-marker-support` | `#A855F7` | 지원 요청 마커 | 보라 |
| `--krds-ops-marker-note` | `#3B82F6` | 운영 메모 마커 | 파랑 |
| `--krds-ops-phone-online` | `#1F8754` | 폴리폰 online (60초 이내 동기화) | 초록 |
| `--krds-ops-phone-stale` | `#E8881A` | 폴리폰 stale (60초+ 미동기) | 주황 |
| `--krds-ops-phone-lost` | `#D63A3A` | 폴리폰 lost (5분+ 위치 끊김) | 빨강 |
| `--krds-ops-phone-mine` | `#22D3EE` (강조 outline) | 운용 중인 폴리폰 강조 | FR-25 |

> 마커 5종은 색만이 아니라 **아이콘 모양**도 다르게 한다 (anti-patterns §3.1 색맹 친화). 예: 단서=사각, 발견=★, 지형=▲, 지원=◆, 운영 메모(NOTE)=●.

#### 폴리폰 freshness 임계 (spec 인용)

화면 설계가 임의 임계값을 사용하면 구현/하네스와 충돌하므로 **boundaries.md**의 정의를 따른다.

| 상태 | 임계 | 출처 |
|---|---|---|
| online | 동기화 60초 이내 | boundaries.md §단말 stale 표시 |
| stale | 60초 이상 미동기 | boundaries.md §단말 stale 표시 |
| lost | 5분 이상 위치 끊김 | boundaries.md §단말 stale 표시 |

화면 라벨은 영어 코드(`stale`/`lost`)가 아니라 한국어 경과 시간 중심으로 표기한다. screen-labels.md §3 상태 라벨 참조.

### 2.2 KRDS-Ops Polifon (다크 모드 기본)

야외 가시성·야간 운용·배터리 절약을 위한 다크 base. 주간 모드 자동 전환은 v2 검토.

| 토큰 | 값 | 용도 |
|---|---|---|
| `--krds-ops-poli-bg-base` | `#0B0F19` | 화면 배경 (다크 네이비) |
| `--krds-ops-poli-bg-surface` | `#111827` | 카드 |
| `--krds-ops-poli-bg-input` | `#1F2937` | 입력 영역 |
| `--krds-ops-poli-bg-elevated` | `#1F2937` | 모달/바텀시트 |
| `--krds-ops-poli-fg-primary` | `#FFFFFF` | 본문 (고대비) |
| `--krds-ops-poli-fg-secondary` | `#E5E7EB` | 부가 |
| `--krds-ops-poli-fg-muted` | `#9CA3AF` | placeholder |
| `--krds-ops-poli-border` | `#2A3240` | 일반 보더 |
| `--krds-ops-poli-primary` | `#3B82F6` | 일반 액션 |
| `--krds-ops-poli-emphasis` | `#EF4444` | 긴급 액션 (수색 종료, destructive) |
| `--krds-ops-poli-success` | `#22C55E` | 동기화 성공·기록 중 |
| `--krds-ops-poli-warning` | `#F59E0B` | 오프라인·미전송 강조·배터리 저하 |
| `--krds-ops-poli-rec` | `#EF4444` | GPS 기록 중 점멸 |
| `--krds-ops-poli-rugged` | `#FCBA04` | 러기드 strip / 강조 외곽 |
| (마커·구간 색상 다크 변형) | §2.1 색에 brightness +10% | 같은 의미 유지 |

### 2.3 색맹 검증

색만으로 상태를 구분하지 않는다 (anti-patterns §3.1). 모든 상태 색은 패턴/아이콘/라벨로 동시 인코딩.

- [ ] 색맹 시뮬레이션 도구로 위 토큰 검증 (Deuteranopia, Protanopia, Tritanopia) — high-fi 단계 게이트 G2-2
- [ ] 차량/도보 = hue 분리(파랑 vs 보라) + 도보는 dash 옵션 검토
- [ ] 마커 5종 = 색 + 아이콘 모양 + 라벨 3중 인코딩
- [ ] 폴리폰 online/stale/lost = 색 + outline 두께 + 라벨 (`12분 전`)

## 3. Typography 토큰

### 3.1 KRDS-Ops Web

| 토큰 | 값 | 용도 | KRDS 원본과의 차이 |
|---|---|---|---|
| `--krds-ops-font-display` | 25px / weight 700 | 헤드라인 | KRDS 32 → 25 압축 |
| `--krds-ops-font-heading` | 19px / weight 700 | 섹션 제목 | KRDS 21 → 19 |
| `--krds-ops-font-subheading` | 15px / weight 700 | 카드 제목 | KRDS 17 → 15 |
| `--krds-ops-font-body` | 14px / weight 400 | 본문 | KRDS 17 → 14 (dense) |
| `--krds-ops-font-label` | 13px / weight 500 | 라벨 | KRDS 15 → 13 |
| `--krds-ops-font-caption` | 11px / weight 400 | 메타 정보·시각 라벨 | 신규 |
| `--krds-ops-font-mono` | 12px / monospace | 좌표·ID·시각 | 신규 |
| `--krds-ops-line-height-tight` | 1.3 | 데이터 dense (리스트 행) | KRDS 1.5 → 1.3 |
| `--krds-ops-line-height-default` | 1.5 | 본문 | KRDS 유지 |

### 3.2 KRDS-Ops Polifon

야외 가독성. KRDS 원본보다 **본문은 키우되** dense 라벨은 유지.

| 토큰 | 값 | 용도 | 원본 KRDS와의 차이 |
|---|---|---|---|
| `--krds-ops-poli-font-display` | 25px / weight 800 | 화면 제목 | KRDS 25 유지 |
| `--krds-ops-poli-font-body` | 17px / weight 500 | 본문 | KRDS 17 유지. 야외 가독성 위해 weight 500 |
| `--krds-ops-poli-font-label` | 14px / weight 600 | 라벨 | dense 보다는 가독 우선 |
| `--krds-ops-poli-font-cta` | 19px / weight 800 | primary CTA | 굵게 |
| `--krds-ops-poli-font-cta-large` | 22px / weight 800 | 강조 CTA (수색 시작 등) | 가장 큰 액션 |
| `--krds-ops-poli-font-status` | 13px / weight 700 | 상태 배지 | letter-spacing 0.4 |
| `--krds-ops-poli-font-mono` | 13px / monospace | 좌표·시각 | |

### 3.3 폰트 패밀리

| 용도 | 폰트 |
|---|---|
| 본문/라벨 (Web/Polifon) | Pretendard GOV (KRDS 표준), fallback Pretendard, Noto Sans KR, system-ui |
| 디스플레이 | Noto Sans KR Bold (KRDS 표준 유지) |
| 숫자/좌표/시각 (monospace) | JetBrains Mono — 라이선스 OFL, 시인성 우수, 한글 fallback Pretendard 숫자 |

## 4. Spacing 토큰

KRDS 8px 스케일 base. 카드 padding은 dense fork.

| 토큰 | 값 | 용도 | 원본 KRDS |
|---|---|---|---|
| `--krds-ops-space-1` | 2px | 타이트 inline | 신규 |
| `--krds-ops-space-2` | 4px | inline | KRDS 4 |
| `--krds-ops-space-3` | 8px | 기본 gap | KRDS 8 |
| `--krds-ops-space-4` | 12px | 그룹 gap | KRDS 12 |
| `--krds-ops-space-5` | 16px | 섹션 gap | KRDS 16 |
| `--krds-ops-space-6` | 24px | 큰 섹션 | KRDS 24 |
| `--krds-ops-space-7` | 40px | 페이지 gap | KRDS 40 |
| `--krds-ops-card-padding` | 16px | 카드 안쪽 (Web dense) | KRDS 40 → 16 압축 |
| `--krds-ops-card-padding-comfortable` | 24px | 사진/실종자 정보 등 강조 카드 | |
| `--krds-ops-panel-padding` | 16px | 사이드 패널 | |
| `--krds-ops-row-padding-y` | 12px | 리스트 행 세로 padding | dense (KRDS 16~20에서 압축) |
| `--krds-ops-row-padding-x` | 12px | 리스트 행 가로 padding | |
| `--krds-ops-poli-card-padding` | 16px | 폴리폰 카드 | |
| `--krds-ops-poli-section-padding` | 20px | 폴리폰 섹션 | thumb 조작 여유 |

## 5. Touch target 토큰

| 토큰 | 값 | 용도 | 근거 |
|---|---|---|---|
| `--krds-ops-poli-touch-min` | 48px | 최소 터치 타깃 | WCAG 2.5.5 |
| `--krds-ops-poli-touch-glove` | 56px | 장갑 사용 타깃 | field-context §1.1 |
| `--krds-ops-poli-cta-height` | 56px | primary CTA 높이 | 일반 |
| `--krds-ops-poli-cta-height-large` | 72px | 강조 CTA 높이 | 수색 시작·종료, 마커 생성 |
| `--krds-ops-poli-bottomsheet-handle` | 32px | 바텀시트 drag handle 높이 | 엄지 grip |
| `--krds-ops-poli-marker-tap-area` | 48px | 지도 위 마커 탭 영역 | 시각 마커 자체는 작아도 가능 |

## 6. Radius / Border / Shadow 토큰

| 토큰 | 값 | 용도 | 원본 |
|---|---|---|---|
| `--krds-ops-radius-sm` | 4px | 칩·뱃지 | KRDS 4 |
| `--krds-ops-radius-md` | 8px | 버튼·입력·일반 카드 | KRDS 8 |
| `--krds-ops-radius-lg` | 12px | 큰 카드·바텀시트 상단 | KRDS 12 |
| `--krds-ops-radius-pill` | 999px | 라운드 칩·OP 토글 | |
| `--krds-ops-poli-radius-bottomsheet` | 20px | 폴리폰 바텀시트 상단 | rugged 톤 |
| `--krds-ops-border-width` | 1px | 일반 | KRDS 1 |
| `--krds-ops-border-width-strong` | 1.5px | 강조 (활성 구역, mine 폴리폰) | |
| `--krds-ops-shadow-card` | `0 1px 2px rgba(0,0,0,0.04)` | 일반 카드 elevation | KRDS는 거의 없음 |
| `--krds-ops-shadow-floating` | `0 4px 12px rgba(0,0,0,0.10)` | 지도 위 floating UI | 신규 |
| `--krds-ops-shadow-modal` | `0 20px 40px rgba(0,0,0,0.16)` | 모달 | |
| `--krds-ops-poli-shadow-bottomsheet` | `0 -8px 24px rgba(0,0,0,0.40)` | 다크 모드 바텀시트 | 위 방향 그림자 |

## 7. Map overlay 토큰

지도 위 UI 전용. KRDS에 없음. 네이버 지도 패턴에서 borrow.

| 토큰 | 값 | 용도 |
|---|---|---|
| `--krds-ops-map-overlay-bg` | `rgba(255,255,255,0.95)` | 지도 위 패널 배경 (Web 라이트) |
| `--krds-ops-map-overlay-bg-poli` | `rgba(17,24,39,0.92)` | 지도 위 패널 배경 (Polifon 다크) |
| `--krds-ops-map-overlay-blur` | `backdrop-filter: blur(8px)` | 배경 blur (지원 브라우저만) |
| `--krds-ops-map-text-halo` | `2px white outline` (Web) / `2px #0B0F19 outline` (Polifon) | 지도 위 라벨 outline |
| `--krds-ops-map-marker-z` | 100 | 마커 기본 z-index |
| `--krds-ops-map-marker-cluster-z` | 110 | 마커 cluster z |
| `--krds-ops-map-emphasis-z` | 200 | PERSON_FOUND·SUPPORT_REQUEST 강조 마커 z |
| `--krds-ops-map-mine-z` | 150 | 운용 중인 폴리폰 마커 z |
| `--krds-ops-map-control-bg` | `--krds-ops-bg-surface` | 줌·layer 컨트롤 배경 |
| `--krds-ops-map-control-size` | 40px | 컨트롤 버튼 크기 |

## 8. Motion 토큰

| 토큰 | 값 | 용도 |
|---|---|---|
| `--krds-ops-duration-instant` | 100ms | 즉시 (hover, active) |
| `--krds-ops-duration-default` | 200ms | 일반 전환 (panel slide, modal) |
| `--krds-ops-duration-slow` | 400ms | 큰 변화 (페이지 전환) |
| `--krds-ops-easing-default` | `cubic-bezier(0.4, 0, 0.2, 1)` | 일반 |
| `--krds-ops-easing-emphasis` | `cubic-bezier(0.0, 0, 0.2, 1)` | 강조 알림 등장 |
| `--krds-ops-emphasis-pulse` | `1.4s ease-in-out infinite` | PERSON_FOUND 점멸 (dismiss 전까지) |

`prefers-reduced-motion: reduce` 환경에서는 모든 transition·pulse를 비활성화하고 즉시 상태 전환만 한다.

## 9. 적용 범위

| 화면 | KRDS 원본 | KRDS-Ops Web | KRDS-Ops Polifon |
|---|---|---|---|
| 웹 로그인 | ✓ (기본 KRDS 톤) | — | — |
| 웹 사건 목록 | — | ✓ | — |
| 웹 사건 상황판 메인 | — | ✓ | — |
| 웹 수색 구역 편집 | — | ✓ | — |
| 웹 OP 비교 / 인수인계 | — | ✓ | — |
| 웹 마커 상세 | — | ✓ | — |
| 웹 차량·도보 보정 | — | ✓ | — |
| 웹 오프라인 패키지 상태 | — | ✓ | — |
| 웹 사건 종료 / 파기 상태 | — | ✓ | — |
| 폴리폰 전 화면 | — | — | ✓ |

웹 로그인은 시민 포털과 비슷한 단순 폼이라 KRDS 원본 톤 유지. 그 외 웹 화면은 ops fork.

## 10. 변경 시 영향

- 토큰 값 결정/변경 → measurement-gates.md G1·G2 게이트 재검증
- 색상 토큰 변경 → anti-patterns §3.1 색맹 검증 재실행 (G2-2)
- 폰트 크기 변경 → screen-labels.md의 라벨 길이 영향 검토
- spacing 압축 변경 → field-context §1.1 (장갑 터치 타깃) 위반 여부 검토 (G4-1)
- 마커 색상 변경 → screen-labels.md 마커 라벨 일관성 검증

## 11. 검토 필요

- [ ] KRDS 라이선스/사용 조건 확인 (fork 가능 여부) — 운영 전 법무
- [ ] Pretendard GOV 라이선스 (앱·웹 임베드) — OFL 기반, 일반적으로 OK
- [ ] JetBrains Mono 라이선스 (OFL — 사용 OK)
- [ ] 다크 모드 기본 채택 vs 자동 전환 (field-context §1.1 조도 변동) — 폴리폰 다크 고정, 주간 자동 전환은 v2
- [ ] 색맹 검증 도구 (Color Oracle, Stark 등) 결정
- [ ] `--krds-ops-map-overlay-blur`의 `backdrop-filter` 브라우저 지원 (Chrome/Edge OK, fallback 정의)
- [ ] OVERALL 구역 outline의 dash pattern 정확값 (현재 `1.5px dash` 추상)
