# 화면 × 상태 매트릭스

상태: 1차 결정 완료 (2026-05-05). 우선순위 1~5(default/loading/empty/error/permission/offline/partial_sync/stale)는 정의됨. P5 수색 하위 상태와 P8 알림 분기는 2026-05-11 보강. 우선순위 6~9는 high-fi 단계에서 채움.

## 목적

- 화면 설계가 default 상태만 그리고 끝나지 않게 한다.
- 각 화면의 상태별 분기를 누락 없이 설계한다.
- 측정 게이트(measurement-gates.md G5)의 검수 기준이 된다.

## 사용 방법

- §3 화면별 카드의 셀 정의를 따른다.
- 라벨은 [screen-labels.md](./screen-labels.md) 규칙을 따른다.
- 비어 있는 셀(우선순위 6~9)은 high-fi 단계에서 채운다.

## 1. 상태 컬럼 정의

| 상태 | 의미 | 우선순위 |
|---|---|---|
| `default` | 기본 표출. 데이터 정상, 권한 있음, 온라인 | 1 |
| `loading` | 데이터 fetch 중 | 2 |
| `empty` | 데이터 없음. 신규 사용자·미배정 등 | 2 |
| `error` | 일반 실패. 재시도 가능 | 2 |
| `permission_denied` | 권한 없음. 채널·역할·사건 배정 미보유 (PRD §8.4) | 3 |
| `permission_partial` | 조회는 되나 수정 불가 (예: 대원이 타 계정 마커를 보는 경우) | 3 |
| `offline` | 네트워크 단절. 미전송 큐 누적 중 | 4 |
| `partial_sync` | 일부만 ack됨. 미전송 항목 존재 | 4 |
| `stale` | 마지막 동기화 경과 임계 초과 (FR-24) | 5 |
| `conflict` | last-write-wins 적용 직후 사용자 인지 필요 | 9 |
| `op_transition` | OP 전환 직후. 이전 OP 데이터는 readonly로 잠김 (FR-12) | 6 |
| `dutyshift_transition` | DutyShift 인계 직후. 이전 근무자 메모 표출 (FR-37) | 6 |
| `incident_closed` | 사건 종료. terminal 상태. 재오픈 없음 (ADR-0022) | 7 |
| `purging` | 종료 직후 데이터 파기 진행 중 (FR-22) | 7 |
| `simple_mode` | FR-26 단순 보기 모드. 무엇이 숨겨지는지 명시 | 8 |

### 1.1.1 INCIDENT_CLOSED 전역 처리 정책 (2026-05-11)

INCIDENT_CLOSED FCM 수신은 **모든 사건 컨텍스트 폴리폰 화면**(P3 오프라인 패키지 / P5 수색 중 지도 / P6-A 이전 근무 확인 / P6-B 인수인계 메모 작성 / P8 강조 알림 상세 / P9 마커 상세 / P4 미전송 진단)에서 동일하게 처리한다.

1. **자동 이동**: 현재 사건 컨텍스트 화면에서 → P2 사건 선택 화면으로 강제 이동
2. **다이얼로그**: P2 위에 "사건이 종료되었습니다 · [확인]" 다이얼로그 1회
3. **작성 중 draft 처리**: 마커/메모 작성 중이었으면 다이얼로그 본문에 "작성 중인 내용은 종료된 사건에 저장되지 않습니다" 안내 1회 추가. 사용자가 [확인] 누르면 draft 자동 폐기 (write API가 어차피 `409 incident_closed` 반환)
4. **백그라운드 정리**: WorkManager가 미전송 ack, 좌표 파기, 패키지 삭제를 자동 진행. 사용자 진행률 표시 없음
5. **P2 카드 자동 제거**: 다이얼로그 [확인] 이후 P2의 종료 사건 카드는 자동으로 사라짐

이 정책의 함의:
- 화면별로 `incident_closed` 분기를 따로 정의할 필요 없음 — 모든 화면에서 "P2 자동 이동"으로 수렴
- 단, 작성 중 draft가 있는 화면(P6-B, P9 편집 모드, 마커 생성 바텀시트)에서만 다이얼로그 본문 한 줄 추가
- §3 화면별 incident_closed 셀은 "본 정책 적용 (P2 이동)"으로 통일

### 1.2 화면 전용 하위 상태

아래 값은 전역 컬럼으로 승격하지 않는다. 특정 화면 내부 state/variant로만 사용하고, API·DB enum을 새로 만들지 않는다.

| 하위 상태 | 적용 화면 | 의미 |
|---|---|---|
| `search_active` | 폴리폰 수색 중 지도 | 현재 OP와 DutyShift가 있고 경로 기록 중. 기본 `default` 안의 운용 상태 |
| `search_paused` | 폴리폰 수색 중 지도 | 경로 기록 일시정지. 재개/종료 액션만 강조하고 새 경로 batch 전송은 막는다 |
| `search_stopped` | 폴리폰 수색 중 지도 | 수색 경로 종료 후. 새 수색 시작 CTA를 제공하되 종료 사건이면 차단 |
| `op_required` | 폴리폰 수색 중 지도 | current OP 조회 실패 또는 없음. 경로·마커 write를 막고 OP 재조회/사건 선택 복귀를 제공 |
| `alert_in_app` | 폴리폰 강조 알림 수신 | 앱 foreground에서 지도 위 banner/dialog로 표시 |
| `alert_os_heads_up` | 폴리폰 강조 알림 수신 | 앱 background/lockscreen에서 Android 알림으로 표시 |
| `alert_fsi_candidate` | 폴리폰 강조 알림 수신 | Android full-screen intent 후보. 운영 정책 확인 전 기본 UX로 확정하지 않음 |

## 2. 화면 × 상태 인덱스 (셀 = 정의/N/A/미정)

`✓` = §3에 정의됨 / `N/A` = 의미 없음 / `_` = high-fi 단계 정의 예정 (우선순위 6~9 위주).

### Web 상황판

| 화면 | default | loading | empty | error | permission_denied | permission_partial | offline | partial_sync | stale | conflict | op_transition | dutyshift_transition | incident_closed | purging | simple_mode |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 로그인 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 사건 목록 | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | N/A | ✓ | N/A | N/A | N/A | ✓ | _ | _ |
| 배정 사건 가져오기 | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | N/A | N/A | _ |
| 사건 상황판 메인 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | _ | _ | _ | ✓ | _ | _ |
| 수색 구역 편집 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | _ | _ | N/A | ✓ | N/A | _ |
| OP 비교 / 인수인계 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | _ | _ | _ | ✓ | _ | _ |
| 마커 상세 / 사진 확인 | ✓ | ✓ | N/A | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | _ | _ | N/A | ✓ | _ | _ |
| 차량·도보 구간 보정 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | _ | _ | N/A | ✓ | N/A | _ |
| 오프라인 패키지 상태 | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | ✓ | ✓ | N/A | N/A | N/A | ✓ | _ | _ |
| 사건 종료 / 파기 상태 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | ✓ | ✓ | N/A |

### Android 폴리폰

| 화면 | default | loading | empty | error | permission_denied | permission_partial | offline | partial_sync | stale | conflict | op_transition | dutyshift_transition | incident_closed | purging | simple_mode |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 인증 / 폴리폰 접속 (관리 폴리폰 자동 확인) | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 사건 선택 (default 1건 + empty 일반 + P7 종료 다이얼로그) | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | N/A | ✓ | N/A | N/A | _ | ✓ | ✓ | _ |
| 오프라인 패키지 다운로드 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | ✓ | ✓ | N/A | N/A | N/A | ✓ | _ | N/A |
| 수색 중 지도 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | _ | _ | _ | ✓ | _ | _ |
| 마커 생성 바텀시트 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | ✓ | N/A | N/A | _ | _ | ✓ | _ | N/A |
| 미전송 진단 화면 (진입 = 처리 불가 toast 탭, 평소 미진입) | ✓ | _ | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A | ✓ | _ | _ |
| alert variant 카탈로그 (지도 Hero 배너) | ✓ | N/A | ✓ | N/A | N/A | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | ✓ | _ | _ |
| 강조 알림 수신 | ✓ | N/A | N/A | ✓ | ✓ | N/A | ✓ | ✓ | ✓ | N/A | N/A | N/A | ✓ | N/A | N/A |
| 마커 상세 / 편집 | ✓ | ✓ | N/A | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | _ | N/A | N/A | ✓ | N/A | N/A |
| 이전 근무 확인 (AI 요약 생성 요청은 P6-A 전용) | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | ✓ | ✓ | N/A | _ | ✓ | ✓ | N/A | N/A |
| 인수인계 메모 작성 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | ✓ | N/A | _ | _ | _ | ✓ | N/A | N/A |

**카탈로그에서 제외된 행 (사유)**:
- `권한 / 환경 거부` — Knox managed configuration이 위치/배터리/Kiosk를 OS 단에서 처리하므로 정상 사용자가 마주치지 않음
- `사건 종료 후 로컬 정리` — 단독 화면을 폐기하고 `사건 선택` 위 다이얼로그 + 백그라운드 정리로 격하 (incident_closed/purging 셀은 사건 선택 행에 흡수)

## 3. 화면별 셀 정의 (우선순위 1~5)

각 화면 카드: 우선순위 1~5 상태만 정의. 우선순위 6~9는 high-fi 단계.

라벨은 screen-labels.md를 따른다. 색·간격은 dense-tokens.md를 따른다.

---

### 3.1 Web 로그인

- **default**: 한국 경찰 로고 + 로그인 폼(아이디·비밀번호) + `로그인` 버튼.
- **loading**: 로그인 요청 중. 버튼 비활성 + 스피너.
- **error**: 로그인 실패 시 폼 아래 빨간 텍스트. 비밀번호 필드 클리어. (잠금 정책은 운영 측 결정)
- **permission_denied**: 자체 회원가입/비번 재설정 UI 없음 안내(PRD §8.4 — 경찰 IT 발급). "계정 발급은 IT 부서 문의" 텍스트.
- **offline**: "네트워크에 연결되어 있지 않습니다. 연결 후 다시 시도하세요" + 로그인 버튼 비활성.

### 3.2 Web 사건 목록

- **default**: 진행 중 사건 카드 그리드 + 종료 사건 토글. 정렬 = 생성일 내림차순. 실종팀 지휘 계정은 `사건 가져오기` 버튼 노출.
- **loading**: 카드 skeleton 6개 + 헤더는 즉시 표시.
- **empty**: 진행 중 사건 0건 → "현재 진행 중인 사건이 없습니다" + 권한자에게 "배정 사건 가져오기" CTA. 권한 없으면 CTA 숨김.
- **error**: 목록 로드 실패 → 빈 영역 + "사건 목록을 불러오지 못했습니다" + `다시 시도`.
- **permission_denied**: 사건 배정이 0건인 계정 → "배정된 사건이 없습니다" empty와 동일하게 처리.
- **offline**: 캐시된 마지막 목록 + 상단 배너 "오프라인 — 마지막 갱신 N분 전" + 가져오기 CTA 비활성.
- **stale**: 마지막 갱신이 임계 초과 → 카드에 stale 배지 + 새로고침 안내.
- **incident_closed**: 종료 사건 토글 ON 시만 표시. 카드에 `종료` 배지 + dimmed.

### 3.3 Web 배정 사건 가져오기

- **default**: mock 112 배정 사건 후보 리스트 + 각 항목별 `가져오기` 버튼.
- **loading**: 후보 리스트 로딩 시 skeleton.
- **empty**: 가져올 배정이 없음 → "현재 가져올 수 있는 배정이 없습니다" + 마지막 polling 시각 표시.
- **error**: polling 실패 → 마지막 성공 시각 + 실패 사유 + `다시 시도`.
- **permission_denied**: SU_O(지원 부서 간부)·MP_M·SU_M·LP_M 등 권한 없는 계정 → 화면 진입 자체 차단, "이 화면에 접근 권한이 없습니다" + 사건 목록으로 돌아가기.
- **offline**: 가져오기 불가 → "오프라인 상태에서는 사건을 가져올 수 없습니다" + 모든 가져오기 버튼 비활성.

### 3.4 Web 사건 상황판 메인

- **default**: 좌측 OP/레이어/마커 필터 패널(280px) + 중앙 지도 + 우측 폴리폰 목록(360px on-demand). 상단 사건/실종자/지휘관 헤더 + 진행 상태 칩.
- **loading**: 지도는 베이스맵만 즉시 표시. 좌·우 패널 skeleton. 데이터 도착하면 점진적 표출.
- **empty**: 신규 사건(경로 0개·마커 0개·구역 0개) → 지도는 전체 수색 구역 outline만 + "수색을 시작하면 경로와 마커가 여기 표시됩니다" 안내 카드.
- **error**: 데이터 로드 부분 실패 시 영향받은 패널만 에러 + `다시 시도`. 지도는 캐시된 마지막 상태 유지.
- **permission_denied**: 사건 미배정 계정 → 진입 차단, 사건 목록으로 돌아가기.
- **permission_partial**: MP_M·SU_M·LP_M(대원) → 화면 진입은 허용. 마커 수정·삭제 버튼은 자기 계정 생성분만 활성화. 구역 완료 등 지휘 액션은 disabled + tooltip "지휘 권한이 필요합니다".
- **offline**: 상단 빨간 배너 "오프라인 — 마지막 동기화 N분 전". 폴리폰 위치는 마지막 ack 기준으로 freeze. 새 데이터 미반영 표시.
- **partial_sync**: 일부 폴리폰만 동기화 누락 → 해당 폴리폰만 stale 색(주황). 우측 패널에 미동기 폴리폰 카운트 배지.
- **stale**: 폴리폰 마지막 동기화 경과 5분+ → 위치 점에 `12분 전` 라벨 + outline 주황. (임계는 FR-24, 정확값 운영 측 협의)
- **incident_closed**: 사건 종료 후 진입 시 → 모든 액션 disabled + 상단 회색 배너 "이 사건은 종료되었습니다" + 조회만 가능.

### 3.5 Web 수색 구역 편집 / 분할 / 할당

- **default**: 지도 + 좌측 도구 패널(폴리곤 그리기, 분할, 할당) + 우측 구역 목록(상태 칩).
- **loading**: 지도 + 도구 패널은 즉시 표시. 구역 목록은 skeleton.
- **empty**: 구역 0개 → "전체 수색 구역을 먼저 그려주세요" 도움말 카드 + 그리기 도구 강조.
- **error**: 저장 실패(geometry invalid 등 SC-04 red test 케이스) → 사용자가 그리던 draft 유지 + 실패 사유 표시 + `다시 시도`.
- **permission_denied**: 권한 없는 계정 → 진입 차단.
- **permission_partial**: 대원 계정 → 화면 readonly 진입. 그리기 도구 disabled + "지휘 권한이 필요합니다".
- **offline**: 그리기 가능하나 저장 시 "오프라인 상태에서는 저장할 수 없습니다" → draft 로컬 보존, 복구 시 `저장` 재시도.
- **stale**: 다른 지휘 계정이 동시 편집 가능성 → 패널에 "마지막 갱신 N분 전" + `새로고침` 권장.
- **incident_closed**: 종료 사건 → 모든 편집 disabled, 조회만.

### 3.6 Web OP 비교 / 인수인계

- **default**: 좌측 OP 리스트(시간순, 사용자 임의 N개 체크) + 중앙 지도(선택 OP overlay) + 우측 인수인계 메모·수색 이력 요약.
- **loading**: OP 리스트는 즉시. 지도 overlay는 선택 OP별 progressive load.
- **empty**: OP 1차만 있고 인수인계 메모 0개 → "인수인계 메모가 없습니다" + 메모 작성 CTA(권한자만).
- **error**: 수색 이력 요약 FAILED 상태 → "요약을 생성하지 못했습니다" + `다시 시도` + 원본 경로/마커/메모 직접 확인 안내 (FR-39).
- **permission_denied**: 사건 미배정 → 진입 차단.
- **permission_partial**: 대원 계정 → `새 OP 열기` disabled + tooltip. 메모 작성은 모든 계정 가능 (PRD §8.4).
- **offline**: 캐시된 OP·메모 표시. 새 OP 생성·요약 생성·메모 저장은 비활성. 상단 배너 "오프라인".
- **stale**: 마지막 갱신 시각 표시. 새 메모 알림 미수신 가능 안내.

### 3.7 Web 마커 상세 / 사진 확인

- **default**: 마커 메타(유형·위치·시각·작성 계정) + 메모 + 사진 그리드. 권한자에게 `수정`·`삭제` 버튼.
- **loading**: 메타 즉시 + 사진 progressive load.
- **error**: 사진 로드 실패 → placeholder + `다시 시도`. 메타 로드 실패 → 전체 에러.
- **permission_denied**: 사건 미배정 → 진입 차단.
- **permission_partial**: 자기 계정 생성분이 아닌 마커를 대원이 볼 때 → 메타·사진 조회 OK, `수정`·`삭제` disabled + tooltip "본인이 생성한 마커만 수정·삭제 가능합니다".
- **offline**: 캐시된 메타·사진 표시. 수정 불가, "오프라인" 안내.
- **stale**: 마커 메타 마지막 갱신 시각 표시. (다른 사용자가 수정했을 가능성)

### 3.8 Web 차량·도보 구간 보정

- **default**: 경로 LineString 위에 자동 분류된 segment 표시(파랑=차량, 보라=도보, 회색=미분류) + segment 클릭 시 우측 패널에서 유형 변경.
- **loading**: 경로 즉시 + segment 색은 progressive.
- **empty**: 경로 0개 → "보정할 경로가 없습니다" + 사건 상황판으로 돌아가기.
- **error**: 보정 저장 실패 → segment 원래 상태 유지 + 실패 사유 + `다시 시도`.
- **permission_denied**: 권한 없는 계정 → 진입 차단.
- **permission_partial**: 대원 계정 → readonly. segment 클릭은 가능하나 유형 변경 disabled.
- **offline**: 보정 변경은 outbox. 상단 "오프라인" 배너.
- **stale**: 다른 지휘자가 보정 가능성 → 새로고침 권장 배너.

### 3.9 Web 오프라인 패키지 상태

- **default**: 사건별 폴리폰 목록 + 각 폴리폰의 패키지 적재 진행률(%, 항목별: 사건 메타·실종자·OP·구역·마커·전체 수색 구역·타일).
- **loading**: 폴리폰 목록 skeleton.
- **empty**: 사건에 배정된 폴리폰 0대 → "배정된 폴리폰이 없습니다".
- **error**: 폴리폰 적재 보고 실패 → 해당 행에 에러 표시 + `다시 시도`.
- **permission_denied**: 사건 미배정 계정 → 진입 차단.
- **offline**: 캐시된 마지막 상태 + 상단 "오프라인" 배너. 새 보고 미반영.
- **partial_sync**: 적재 일부 항목 실패한 폴리폰 → 해당 폴리폰만 노란색 + 실패 항목 라벨.
- **stale**: 폴리폰 보고가 임계 초과 → stale 표시.

### 3.10 Web 사건 종료 / 파기 상태

- **default (실종팀 간부)**: 사건 요약 + `사건 종료` 버튼 (강조 빨강 + confirm 다이얼로그 필요). 종료 후 → 종료 시각·파기 진행률 표시.
- **loading**: 종료 요청 처리 중. 모든 액션 비활성 + 스피너.
- **error**: 종료 실패 → 사유 표시 + `다시 시도`. (terminal 처리 실패 케이스)
- **permission_denied**: 실종팀 간부 외 → 종료 버튼 자체 숨김. 파기 상태는 조회만.
- **offline**: 종료 액션 비활성 → "오프라인 상태에서는 사건을 종료할 수 없습니다".
- **incident_closed**: 종료 후 진입 → 종료 시각 표시 + 파기 항목별 상태(완료/진행중) + **재오픈 UI 없음** (ADR-0022).
- **purging**: 종료 직후 파기 진행 → 항목별 진행률 표시(실종자 정보 제거 / 폴리폰 좌표 파기 / 폴리폰 로컬 정리). 완료 시 `완료` 배지.

---

### 3.11 폴리폰 인증 / 폴리폰 접속

- **운영 전제**: 폴리폰 = 팀 단위/순찰차 단위 1대 지급(PRD §0.1, §8.4) + 단말 ↔ 팀/순찰차 계정 1:1 매핑(L5-03 §4.2) + 장기 세션 유지(PRD §8.4, boundaries.md §S1-2 §282) + 삼성 Knox 위 관리 단말. 정상 운영 첫 화면은 관리 폴리폰 자동 확인 전환 상태이며, 사용자가 ID/PW나 바인딩 코드를 직접 입력하지 않는다.
- **default = managed_phone_checking → internal_network_ok**: 2단계 progress bar. 단계 통과 시 chip `확인`, 진행 중 chip `확인 중`. 모두 통과하면 **사건 선택(P2)으로 자동 이동** — 배정 사건 0건이든 N건이든 P2가 처리. 단계명에 `Knox`/`MDM` 노출하지 않는다 (사용자 문구는 `관리 폴리폰`/`내부망`).
- **loading**: 단계별 progress 진행. 사용자 입력 요구 없음.
- **not_managed_phone**: 관리 폴리폰 검증 실패 → "관리 단말이 아닙니다. IT 부서 문의". 사용자 재시도 CTA 없음. 인증 전 화면에 사건명·OP·배정 사건 노출 금지.
- **internal_network_unavailable**: 내부망 도달 실패 → "내부망 연결을 확인하세요" + 네트워크 재시도 CTA (Wi-Fi 설정 이동 보조).
- **server_rejected_phone**: 폴리폰 미등록·계정-폴리폰 미매칭 → "이 폴리폰으로 접속할 수 없습니다. IT 부서 문의" + 재시도 버튼 없음. (배정 사건 0건은 P1 실패가 아니라 P2 empty로 처리)
- **managed_config_missing**: 관리 설정 미주입·기기 교체 후 미등록 → "관리 설정이 없습니다. IT 부서 문의" + 사용자 입력 폼 없음.
- **offline**: 캐시된 마지막 세션 자동 복구 시도. 실패 시 "네트워크 연결 후 다시 시도하세요" + 마지막 세션이 살아있을 때만 사건 데이터 접근 허용.

### 3.12 폴리폰 사건 선택

- **운영 전제**: 단말 = 팀/순찰차 1대 1:1 매핑 + FR-08 동시 활성 OP 1개 → **default는 보통 활성 사건 1건**, **empty가 더 일반적**.
- **default**: 활성 배정 사건 1건 카드(활성 배지 강조) + [선택한 사건 열기] CTA. 잠시 2건이 겹치는 경우는 사건 가져오기/종료 시점의 짧은 transition.
- **loading**: 캐시된 마지막 목록이 있으면 먼저 표시하고 sync chip을 `갱신 중`으로 둔다. 캐시가 없을 때만 카드 skeleton.
- **empty (가장 자주 보이는 상태)**: 배정 사건 0건 → "현재 배정된 사건이 없습니다 · 상황실 배정 대기" + 새로고침 풀다운. 사건 가져오기 버튼은 없음 (Web 상황판 전용, PRD §8.4).
- **error**: 목록 로드 실패 → "사건 목록을 불러오지 못했습니다" + `다시 시도` 큰 버튼.
- **permission_denied**: 비활성 계정 → "계정이 비활성화되었습니다" + IT 문의.
- **offline**: 캐시된 마지막 목록 + 상단 다크 배너 "오프라인 / 기록 중" + 새로고침 비활성.
- **stale**: 마지막 갱신 시각 카드에 표시.
- **incident_closed (P7 격하 후 진입점)**: INCIDENT_CLOSED FCM 수신 시 이 화면 위에 다이얼로그 "사건이 종료되었습니다 · 더 이상 입력할 수 없습니다 · [확인]" 표시. 로컬 정리(미전송 ack, 좌표 파기, 패키지 삭제)는 WorkManager 백그라운드 진행. 사용자에게 progress bar를 보여주지 않음.
- **purging**: incident_closed 다이얼로그 [확인] 이후 사건 카드가 자동 제거되는 transition. 별도 UI 없음.

### 3.13 폴리폰 오프라인 패키지 다운로드

- **진입·종료 트리거 (2026-05-11)**:
  - **진입**: P2 사건 카드 탭 → 서버 manifest 변경 체크 → 변경 있으면 P3 자동 진입, 변경 없으면 P3 건너뛰고 P5 직진
  - **종료 (성공)**: 100% + readyForOfflineUse=true → P5로 자동 이동
  - **종료 (부분 성공, 타일 미완료)**: readyForOfflineUse=false → `제한 안내 후 열기` CTA로 사용자 결정 → P5 이동
  - **종료 (실패)**: auto_retry_exhausted → 사용자가 수동 재시도하거나 사건 선택 복귀
- **자동 재시도 정책 (2026-05-11)**: 실패 항목은 WorkManager 자동 재시도(1초 → 5초 → 30초 backoff, 최대 3회). 한계 도달 전에는 chip `자동 재시도 N/3`로 상태 표시만. 사용자 수동 재시도 CTA는 한계 도달 시에만 노출 (P4 진단 화면과 같은 원칙).
- **default**: 사건 진입 시 자동 시작. 항목별 진행률 바(사건 메타·실종자·OP·구역·마커·전체 수색 구역·타일). 전체 진행률 상단 큰 표시.
- **loading**: 다운로드 진행. 항목별 ✓ 표시 누적.
- **auto_retry_in_progress (구 error 일부)**: 실패 항목 자동 재시도 중. chip `자동 재시도 N/3` 표시만, 수동 CTA 없음.
- **auto_retry_exhausted**: 자동 재시도 3회 모두 실패. `수동 재시도` CTA + `네트워크 확인 / IT 부서 문의` 안내. 성공 항목은 다시 받지 않음 (SC-03 red test).
- **permission_denied**: 미배정 폴리폰 → "이 폴리폰은 사건에 배정되지 않았습니다" + 사건 선택으로 돌아가기.
- **offline**: 다운로드 일시정지 → "네트워크 연결을 확인하세요" + 자동 재시도 안내.
- **partial_sync**: 필수 사건 데이터는 있으나 타일/일부 항목 실패 → `readyForOfflineUse=false` 유지 + 자동 재시도 진행 chip. 지도 타일 미완료 시 "지도 사용 제한" 경고 후 진입을 허용하되, 오프라인 사용 준비 완료로 표시하지 않는다.
- **stale**: 패키지 manifest 만료 → "전체 수색 구역이 변경되었습니다. 다시 다운로드하세요" + `재다운로드` CTA.
- **incident_closed**: §1.1.1 전역 정책 적용 — 다운로드 즉시 중단 + P2로 자동 이동 + 다이얼로그.

### 3.14 폴리폰 수색 중 지도

- **default**: 전체화면 지도(다크 테마) + 상단 사건/OP/동기화 상태 배지 + 하단 바텀시트 핸들. 운용 중인 폴리폰 위치 강조(시안 outline). 다른 폴리폰 위치는 일반.
- **loading**: 지도 베이스맵 즉시. 경로·마커 progressive.
- **empty**: 경로 기록 전 → 큰 `수색 시작` CTA 강조(72px 높이). 마커 0개일 때는 표시 없음.
- **error**: GPS 신호 약함 → 상단 노란 배너 "GPS 신호가 약합니다" (FR-29). 지도는 마지막 위치 유지.
- **permission_denied**: 사건 미배정 → 진입 차단, 사건 선택으로.
- **permission_partial**: 마커 상세에서 자기 계정 생성분이 아닌 마커 수정 시도 → 비활성 + tooltip.
- **offline**: 상단 노란 배너 "오프라인 — N건 미전송". GPS 기록·마커 생성 모두 정상 가능 (로컬 저장).
- **partial_sync**: 미전송 큐 5건+ → 배너 색 변경 + 카운트 강조. 20건+ → 빨강 + 알림.
- **stale**: 다른 폴리폰 위치 stale 시 → 해당 점 outline 주황 + 라벨 (Web과 같은 규칙, FR-24).
- **search_active**: 현재 OP/DutyShift가 유효하고 기록 중. `일시정지`·`종료`·`마커 생성`을 제공한다.
- **search_paused**: 기록 일시정지. `재개`를 primary로 두고, 위치는 마지막 유효 좌표로 표시한다. 미전송 큐는 계속 표시한다.
- **search_stopped**: 현재 경로 종료. 새 `수색 시작` CTA를 제공하되 종료 사건이면 숨긴다.
- **op_required**: current OP 누락·만료·조회 실패. 경로·마커 write를 차단하고 `OP 다시 확인` 또는 사건 선택 복귀를 제공한다.
- **incident_closed**: §1.1.1 전역 정책 적용 — INCIDENT_CLOSED FCM 수신 시 P2 사건 선택으로 자동 이동 + P2 위 다이얼로그. 기록 중이던 search_path는 클라이언트가 segment를 마감하고 outbox로 전달 시도(server는 어차피 `409 incident_closed`로 거부 → outbox는 처리 불가 그룹으로 분류 → P4 진단 화면에서 IT 안내 흐름).

### 3.15 폴리폰 마커 생성 바텀시트

- **default**: 5종 마커 유형 큰 아이콘 그리드(2×3 또는 5×1, 각 56px+) + 메모 입력 + 사진 첨부. CLUE/PERSON_FOUND/FIELD_CONDITION/NOTE는 유형 탭으로 최소 마커를 즉시 생성하고, SUPPORT_REQUEST는 supportRequestType 선택 탭에서 즉시 생성한다. 하단 CTA는 생성 후 메모/사진 추가 저장용이다.
- **loading**: 최소 마커 생성 또는 추가 정보 저장 요청 중. 해당 버튼 비활성 + 스피너.
- **error**: 마커 생성 또는 추가 정보 저장 실패(권한·서버 오류) → 바텀시트 위 토스트 "마커를 저장하지 못했습니다. 다시 시도하세요" + 입력 유지.
- **permission_denied**: 사건 미배정 폴리폰 → 바텀시트 자체 진입 차단 (사건 진입 단계에서 차단).
- **offline**: 정상 동작(로컬 생성/추가 저장 + outbox). 처리 후 토스트 "오프라인 저장됨. 연결 시 자동 전송".
- **partial_sync**: 미전송 큐 누적 시 바텀시트 상단에 작은 카운트 배지 (현재 큐 N건).
- **incident_closed**: §1.1.1 전역 정책 적용 — 마커 작성 중이었으면 다이얼로그 본문에 "작성 중인 마커는 저장되지 않습니다" 안내 1회 추가. [확인] 후 draft 폐기 + P2 자동 이동.

### 3.16 폴리폰 미전송 진단 화면

- **정체성 재정의 (2026-05-11)**: dashboard가 아니라 **"사용자 조치가 필요한 처리 불가 항목이 발생했을 때만 진입하는 진단 화면"**. 정상 오프라인·복구 진행은 지도 sync chip aggregate(`미전송 12 → 전송 중 8 → 동기화`)로 표현하고, 본 화면에 진입하지 않는다.
- **진입 동선 (단일)**: 지도 상단 toast `미전송 N건 처리 불가 · 탭하여 확인` 탭. 처리 불가 0건이면 toast 미발생 → 본 화면 진입 없음.
- **default (= blocked 1건+)**: 처리 불가 그룹 강조 카드 (사유별 안내 + IT 부서 문의 CTA). 자동 처리 대기 그룹은 한 줄 축약("자동 처리 대기 10건 · 연결 시 자동 전송 · 사용자 조치 불요").
- **loading**: 로컬 큐는 즉시 읽는 것이 기본. 본 화면 loading은 거의 발생하지 않음.
- **empty (처리 불가 0건)**: 본 화면에 진입하지 않는다. 사용자는 sync chip aggregate(`동기화`)만 봄.
- **blocked**: `incident_closed`, `police_phone_not_assigned`, WorkManager 자동 재시도 3회 실패 등 자동 해결 불가 사유별 카드. 사용자 수동 재시도 CTA 없음(WorkManager 자동), IT 부서 문의 CTA만 제공.
- **permission_denied**: N/A (자기 폴리폰 큐만 보므로).
- **offline / partial_sync**: 본 화면 진입 없음. sync chip aggregate가 `미전송 N · M분` / 카운트 감소(`12 → 8 → 3`)로 표현.

### 3.17 폴리폰 alert variant 카탈로그 (구 P5 로컬 경고)

- **정체성**: 독립 화면이 아니라 지도(Hero) 상단 `.alert-banner` slot 위의 alert variant 모음. `lo-polifon-local-warning-v1.html`은 화면 카탈로그가 아니라 alert 컴포넌트 참조 시트.
- **default**: 화면 자체로는 별도 페이지가 아니라 **다른 화면 위에 띄우는 배너/토스트**. 조건별 (오프라인·GPS 중단·배터리 저하·지도 미다운로드) 색·아이콘 다름. 동시 노출은 우선순위 상위 2개까지.
- **empty**: 경고 없을 때는 표시 안 함.
- **permission_denied**: N/A.
- **offline**: "오프라인" 배너 자체가 alert variant의 한 형태. 정상 오프라인/자동 전송 대기는 P4로 이동하지 않고 sync chip aggregate로만 표시한다. P4 진입은 처리 불가 toast 탭으로 제한한다.
- **incident_closed**: 사건 종료 시 모든 로컬 경고 dismiss.

### 3.18 폴리폰 강조 알림 수신

- **액션 정책 (2026-05-11)**: PERSON_FOUND·SUPPORT_REQUEST는 PRD §8.3 "즉시 도달" 정신상 snooze 부적합. 알림 액션은 **`확인`(dismiss) + `알림 보러 가기/지도 열기`(이동) 2종**으로 단순화. `나중에` 같은 보류 버튼은 두지 않는다. 인지만 하고 즉시 이동하지 못하는 경우는 OS notification을 그대로 두면 됨 (사용자 액션 없음 = 보류와 동일).
- **발생자 = 수신자 케이스 (2026-05-11)**: 자기 폴리폰이 생성한 PERSON_FOUND/SUPPORT_REQUEST는 본인에게 알림 발생 X. 서버 FCM recipient 필터에서 발신자 폴리폰 제외 (UI 결정이 아니라 서버 책임). 단, 다른 폴리폰이 같은 사건에 생성한 알림은 정상 수신.
- **도착지 (2026-05-11)**: `알림 보러 가기` / `지도 열기` 탭 = **해당 마커가 강조된 P5 지도로 이동**(`recenter` + 마커 포커스). P9 마커 상세가 아님. 사용자는 P5에서 위치를 본 다음 필요시 마커 탭으로 P9 진입.
- **default / alert_in_app**: 앱 foreground에서 PERSON_FOUND 또는 SUPPORT_REQUEST를 지도 위 banner/dialog로 표시한다. snackbar/toast 단독 표시는 금지.
- **error**: 알림 payload의 incident/marker 접근권한 검증 실패 → 민감 내용 없이 "알림을 열 수 없습니다"만 표시하고 서버 재조회로 복구한다.
- **permission_denied**: 사건 미배정 폴리폰에는 알림 상세를 표시하지 않는다.
- **offline**: 수신된 OS 알림은 열 수 있으나 상세는 캐시 기준으로 표시하고, 서버 확인이 필요한 액션은 비활성.
- **partial_sync**: 마커/사진 일부만 동기화되면 알림은 유지하되 상세 화면에 "일부 기록 전송 대기" 배지를 표시한다.
- **stale**: 알림 수신 후 오래 지난 경우 현재 사건 상태를 먼저 재조회한다.
- **alert_os_heads_up**: 앱 background/lockscreen에서는 Android heads-up/lockscreen notification으로 표시한다. 액션은 `지도 열기` 1개.
- **alert_fsi_candidate**: full-screen intent는 Android 14+ 권한·심사·경찰 운영정책 확인 전 기본 흐름으로 확정하지 않는다.
- **incident_closed**: §1.1.1 전역 정책 적용 — INCIDENT_CLOSED FCM 수신 시 P8 알림 상세도 즉시 dismiss하고 P2로 자동 이동.

### 3.19 폴리폰 마커 상세 / 편집

- **default**: 마커 유형·위치·시각·작성 계정·사진·메모를 표시한다. 자기 계정 생성분 또는 권한 보유자는 수정/삭제 가능. 사진·마커 삭제는 명시적 삭제 버튼과 confirm dialog로 확정하며 long-press 단독 삭제는 쓰지 않는다.
- **loading**: 메타를 먼저 표시하고 사진은 progressive load.
- **error**: 사진/메타 로드 실패 시 영향받은 영역만 재시도. 삭제/저장은 비활성.
- **permission_denied**: 사건 미배정 폴리폰 → 사건 선택으로 이동.
- **permission_partial**: 타 계정 마커를 대원이 보는 경우 읽기 전용. 수정/삭제 버튼은 숨기거나 disabled.
- **offline**: 캐시된 상세 표시. 수정은 outbox에 적재 가능한 범위만 허용하고 삭제는 서버 확인 전 보류.
- **partial_sync**: 사진 attach 또는 메모 수정이 일부 전송 대기이면 상태 chip으로 표시한다.
- **stale**: 마지막 갱신 시각 표시. 저장 전 새로고침 권장.
- **incident_closed**: §1.1.1 전역 정책 적용 — P9 마커 상세에서 편집 중이었으면 다이얼로그 본문에 "작성 중인 내용은 저장되지 않습니다" 안내 1회 추가. [확인] 후 draft 폐기 + P2 자동 이동.

### 3.20 폴리폰 권한 / 환경 거부 — 카탈로그에서 제외

- **결정 (2026-05-11)**: 본 화면은 카탈로그에서 제외한다. Knox managed configuration이 위치/배터리/Kiosk를 OS 단에서 처리하므로 정상 운영에서 사용자가 마주칠 일이 없다.
- 만약 운영 중 OS 정책상 사용자 동의 필수 권한이 발견되면, 본 섹션이 아니라 P1-B 관리 폴리폰 자동 확인의 실패 분기 또는 alert variant 카탈로그(§3.17) variant로 흡수한다.

### 3.21 폴리폰 이전 근무 확인

- **정체성 (2026-05-11)**: **이전 근무 확인 + AI 요약 생성 요청 화면**. 새 근무자는 원본 기록을 확인하고, 요약이 없거나 오래되었거나 실패한 경우 P6-A에서 생성 요청을 보낸다. 실제 AI 처리는 내부망 백엔드가 수행한다. P6-B 메모 작성에는 요약 생성 CTA를 두지 않는다.
- **default (요약 준비됨)**: AI 요약 카드 + 원본 경로/마커/인수인계 메모 목록. 기본 상태에서는 생성 CTA 없음. 메모 작성 폼 없음(P6-B).
- **summary_needed (요약 필요)**: 원본 기록 표시 + `요약 생성 필요` chip + [AI 요약 생성] CTA.
- **loading (요약 준비 중)**: chip `자동 처리 중` + 원본 기록은 즉시 표시.
- **empty (이전 근무 기록 0건)**: "이전 근무 기록이 없습니다" + 수색 화면 진입 CTA만.
- **summary_unavailable (요약 준비 못 함)**: 안내 텍스트 "원본 기록을 확인하세요" + 원본 기록 표시. S8/API 계약이 재요청을 허용하면 [다시 생성 요청] CTA 노출, 없으면 원본 확인만 제공.
- **permission_denied**: 사건 미배정 폴리폰 → 사건 선택으로 이동.
- **offline**: 캐시된 요약/원본 기록 표시. 마지막 갱신 시각 함께 표시. 생성 요청 CTA 비활성 + "내부망 연결 후 생성 가능" 안내.
- **partial_sync**: 미전송 항목이 남아 있으면 요약 기준 시각과 "일부 기록 전송 대기" 배지를 표시. 전송 완료 전에는 생성 CTA 비활성.
- **stale**: 요약 기준 시각이 임계 초과 → `요약 생성 필요` chip + [AI 요약 생성] CTA.
- **dutyshift_transition (진입 트리거 결정 2026-05-11)**: 자동 진입을 채택하지 않는다. 대신 새 근무자가 P5(수색 중 지도)에 진입할 때 **상단 reserved slot에 "이전 근무 기록 있음 · 확인" 배너**를 표시한다. 사용자가 배너 탭 시 P6-A 진입. 사용자 작업을 강제 중단하지 않는 정책. 감지 신호: 단말 1:1 매핑이므로 폰 부팅·앱 재진입 시점에 서버 `current_duty_shift.started_at` > 클라이언트 `last_seen_handover_at` 이면 배너 표시.
- **incident_closed**: §1.1.1 전역 정책 적용 — P6-A 확인 중이었으면 즉시 P2로 자동 이동. P6-A에는 draft가 없으므로 draft 안내 불요.
- **계약 메모**: Android는 외부 AI API를 직접 호출하지 않는다. 내부망 백엔드의 S8/API 계약만 소비하고, 계약에 없는 생성 endpoint를 임의로 만들지 않는다.

### 3.22 폴리폰 인수인계 메모 작성

- **default**: 현재 사건·OP·근무 구간(DutyShift)을 표시하고 대상(OP/근무 구간/경로/구역/마커) 선택 + 본문 입력 + `저장` 버튼을 제공.
- **loading**: 저장 요청 중. 버튼 비활성 + 스피너.
- **error**: 저장 실패 → 입력 draft 유지 + 실패 사유 + `다시 시도`.
- **permission_denied**: 사건 미배정 폴리폰 → 저장 차단, 사건 선택으로 이동.
- **offline**: 로컬 저장 + 미전송 큐 적재. "오프라인 저장됨. 연결 시 자동 전송" 표시.
- **partial_sync**: 저장됨/전송 대기/ACKED를 구분해 표시. 성공 항목은 완료, 실패 항목만 미전송 큐에 남김.
- **incident_closed**: §1.1.1 전역 정책 적용 — P6-B 메모 작성 중이었으면 다이얼로그 본문에 "작성 중인 메모는 저장되지 않습니다" 안내 1회 추가. [확인] 후 draft 폐기 + P2 자동 이동.

### 3.23 폴리폰 사건 종료 후 로컬 정리 — 다이얼로그로 격하

- **결정 (2026-05-11)**: 단독 화면을 폐기. **§3.12 폴리폰 사건 선택 화면 위 다이얼로그 + 백그라운드 정리**로 격하.
- **사유**: 현장 사용자는 "이 사건은 종료됐고 더 이상 입력할 수 없다"만 알면 충분하다. 실종자 정보 제거·좌표 파기·패키지 삭제 진행률은 백그라운드 작업이지 사용자에게 progress bar로 보여줄 가치가 없다 (anti-patterns §6.1 OS 영역 모방).
- **incident_closed/purging 처리**: §3.12 사건 선택 항목으로 이관됨. PRD §8.5 정리 정책(ack 받은 데이터만, 미전송 항목은 flush 후 삭제)은 그대로 유지하되 UI 노출은 다이얼로그 1개로 단순화.

## 4. board_shell_slots 정렬

웹 상황판 화면은 spec(`boundaries.md`) §9.2 `board_shell_slots` 계약과 정렬되어야 한다. 화면 설계 산출물의 slot 명명·소유는 spec을 따른다.

### 화면 × slot 매핑 (초안)

slot 13종 (boundaries.md §9.2): `overall_search_area`, `area`, `path`, `police_phone_freshness`, `marker`, `toast`, `package_badge`, `op_toggle`, `op_history`, `handover_memo`, `handover_status`, `search_history_summary`, `incident_terminal`.

| 화면 | 사용 slot |
|---|---|
| 사건 상황판 메인 | `overall_search_area`, `area`, `path`, `police_phone_freshness`, `marker`, `toast`, `op_toggle`, `op_history`, `handover_status`, `package_badge` |
| 수색 구역 편집 / 분할 / 할당 | `overall_search_area`, `area` |
| OP 비교 / 인수인계 | `op_toggle`, `op_history`, `handover_memo`, `handover_status`, `search_history_summary`, `path`, `marker` |
| 마커 상세 / 사진 확인 | `marker` |
| 차량·도보 구간 보정 | `path` |
| 오프라인 패키지 상태 | `package_badge` |
| 사건 종료 / 파기 상태 | `incident_terminal`, `package_badge` |

> 위 매핑은 harness-scenarios.md `board_merge` 컬럼을 기반으로 한 초안. 각 화면이 owns가 아니라 mounted by S3-2임을 잊지 않는다 (spec). 화면 설계 산출물은 slot-scoped renderer만 납품한다.

## 5. 변경 시 영향

- 새 화면 추가 → 행 추가 + 모든 상태 재검토
- 새 상태 추가 → 컬럼 추가 + 모든 화면 재검토
- 셀 정의 변경 → measurement-gates.md G5, anti-patterns.md 영향 검토
- 우선순위 6~9 셀 채우기 → high-fi 단계
