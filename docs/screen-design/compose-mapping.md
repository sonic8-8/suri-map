# 폴리폰 Low-fi → Compose Mapping

상태: Android UI 구현 기준 초안. 기준 화면은 `docs/screen-design/wireframes.md`와 `docs/screen-design/artifacts/lo/lo-polifon-wireframes-v1.html`이다.

## Token Mapping

| Low-fi token | Compose token |
|---|---|
| `--poli-bg-base` | `PoliBgBase` |
| `--poli-bg-surface` | `PoliBgSurface` |
| `--poli-bg-input` | `PoliBgInput` |
| `--poli-fg-primary` | `PoliFgPrimary` |
| `--poli-fg-secondary` | `PoliFgSecondary` |
| `--poli-fg-muted` | `PoliFgMuted` |
| `--poli-border` | `PoliBorder` |
| `--poli-border-strong` | `PoliBorderStrong` |
| `--poli-primary` | `PoliPrimary` |
| `--poli-primary-border` | `PoliPrimaryBorder` |
| `--poli-primary-fg` | `PoliPrimaryFg` |
| `--poli-success` | `PoliSuccess` |
| `--poli-warning` | `PoliWarning` |
| `--poli-emphasis` | `PoliEmphasis` |

## Component Mapping

| Low-fi class / pattern | Compose component |
|---|---|
| `.button.primary` | `PoliButton(variant = Primary)` |
| `.button.secondary` | `PoliButton(variant = Secondary)` |
| `.button.danger` | `PoliButton(variant = Danger)` |
| `.chip.good/.warn/.bad` | `PoliChip(variant = Good/Warn/Bad)` |
| `.banner.warn/.bad` | `PoliBanner(variant = Warn/Bad)` |
| `.card` | `PoliCard` |
| `.row` | `PoliRow` |
| app bar title block | `PoliAppBar` |
| brand mark | `PoliBrandMark` |
| 종료/삭제 confirm | `PoliDialog` |
| 처리 불가 toast | `PoliToast` |
| bottom sheet shell | `PoliBottomSheet` |

## Screen Mapping

| Low-fi screen | Compose screen |
|---|---|
| P1-B 관리 폴리폰 자동 확인 | `AuthBootstrapScreen` |
| P2 사건 선택 | `IncidentListScreen` |
| P6-A 이전 근무 확인 | `DutyHandoverScreen` |
| P6-B 인수인계 메모 작성 | `HandoverMemoScreen` |
| P7 종료 다이얼로그 | `IncidentClosedDialog` state in `AppOverlayHost` |
| P4 처리 불가 toast 진입 | `BlockedQueueToastState` in `AppOverlayHost` |

## State Boundary

- Composable은 state를 렌더링하고 event를 위로 올린다.
- `IncidentContext`는 Activity/Nav scope state holder 책임이다. DataStore에 저장하지 않는다.
- 정상 오프라인/복구 진행은 `SyncChip` 계열 상태로 표시한다. P4 진입은 처리 불가 toast에만 연결한다.
- P6-A는 조회 전용이다. Android 요약 생성/재생성/재시도 CTA를 두지 않는다.
