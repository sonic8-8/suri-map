# 화면 × 상태 매트릭스

상태: 1차 결정 완료 (2026-05-05). 우선순위 1~5(default/loading/empty/error/permission/offline/partial_sync/stale)는 정의됨. 우선순위 6~9는 high-fi 단계에서 채움.

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
| `offline` | 네트워크 단절. Outbox 큐 누적 중 | 4 |
| `partial_sync` | 일부만 ack됨. 미전송 항목 존재 | 4 |
| `stale` | 마지막 동기화 경과 임계 초과 (FR-24) | 5 |
| `conflict` | last-write-wins 적용 직후 사용자 인지 필요 | 9 |
| `op_transition` | OP 전환 직후. 이전 OP 데이터는 readonly로 잠김 (FR-12) | 6 |
| `dutyshift_transition` | DutyShift 인계 직후. 이전 근무자 메모 표출 (FR-37) | 6 |
| `incident_closed` | 사건 종료. terminal 상태. 재오픈 없음 (ADR-0022) | 7 |
| `purging` | 종료 직후 데이터 파기 진행 중 (FR-22) | 7 |
| `simple_mode` | FR-26 단순 보기 모드. 무엇이 숨겨지는지 명시 | 8 |

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
| 로그인 / 단말 확인 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | N/A | N/A | N/A |
| 사건 선택 | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | N/A | ✓ | N/A | N/A | _ | ✓ | _ | _ |
| 오프라인 패키지 다운로드 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | ✓ | ✓ | N/A | N/A | N/A | ✓ | _ | N/A |
| 수색 중 지도 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | _ | _ | _ | ✓ | _ | _ |
| 마커 생성 바텀시트 | ✓ | ✓ | N/A | ✓ | ✓ | N/A | ✓ | ✓ | N/A | N/A | _ | _ | ✓ | _ | N/A |
| 미전송 큐 | ✓ | ✓ | ✓ | ✓ | ✓ | N/A | ✓ | ✓ | N/A | N/A | N/A | N/A | ✓ | _ | _ |
| 로컬 경고 | ✓ | N/A | ✓ | N/A | N/A | N/A | ✓ | N/A | N/A | N/A | N/A | N/A | ✓ | _ | _ |
| 사건 종료 후 로컬 정리 | ✓ | ✓ | N/A | ✓ | N/A | N/A | ✓ | ✓ | N/A | N/A | N/A | N/A | ✓ | ✓ | N/A |

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
- **offline**: 캐시된 OP·메모 표시. 새 OP 생성·메모 저장은 outbox로. 상단 배너 "오프라인".
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
- **purging**: 종료 직후 파기 진행 → 항목별 진행률 표시(실종자 정보 제거 / 폴리폰 좌표 파기 / 단말 로컬 정리). 완료 시 `완료` 배지.

---

### 3.11 Polifon 로그인 / 단말 확인

- **default**: 로고 + 팀 계정/순찰차 계정 ID·PW 입력 + `로그인` 버튼. 로그인 후 단말(폴리폰) 등록 확인 화면 → 단말 ID·소속 표시 + `시작` 버튼.
- **loading**: 로그인/등록 확인 요청 중. 버튼 비활성 + 스피너.
- **error**: 인증 실패 → 폼 아래 빨간 텍스트 + 재시도. 단말 미등록 → "이 단말이 등록되지 않았습니다. IT 부서 문의" + 재시도 버튼 없음.
- **permission_denied**: 단말 미등록·계정-단말 미매칭 → "이 단말로 로그인할 수 없습니다" + IT 문의 안내.
- **offline**: 캐시된 마지막 로그인 자동 복구 시도. 실패 시 "네트워크 연결 후 로그인하세요" + 오프라인 모드는 마지막 세션이 살아있을 때만 사건 데이터 접근.

### 3.12 Polifon 사건 선택

- **default**: 배정 사건 리스트(카드, 1행씩, 큰 타깃). 사건 카드에 실종자 이름·상태·마지막 활동 시각.
- **loading**: 카드 skeleton 3개.
- **empty**: 배정 사건 0건 → "현재 배정된 사건이 없습니다" + 새로고침 풀다운.
- **error**: 목록 로드 실패 → "사건 목록을 불러오지 못했습니다" + `다시 시도` 큰 버튼.
- **permission_denied**: 비활성 계정 → "계정이 비활성화되었습니다" + IT 문의.
- **offline**: 캐시된 마지막 목록 + 상단 다크 배너 "오프라인 / 기록 중" + 새로고침 비활성.
- **stale**: 마지막 갱신 시각 카드에 표시.
- **incident_closed**: 종료 사건은 자동 숨김(서버 알림 받으면 카드에서 제거). 진입 직후 종료된 사건은 사건 종료 후 로컬 정리 화면으로 자동 이동.

### 3.13 Polifon 오프라인 패키지 다운로드

- **default**: 사건 진입 시 자동 시작. 항목별 진행률 바(사건 메타·실종자·OP·구역·마커·전체 수색 구역·타일). 전체 진행률 상단 큰 표시.
- **loading**: 다운로드 진행. 항목별 ✓ 표시 누적.
- **error**: 일부 항목 실패 → 실패 항목명 + `실패 항목 재시도` 큰 버튼. 성공 항목은 다시 받지 않음 (SC-03 red test).
- **permission_denied**: 미배정 단말 → "이 단말은 사건에 배정되지 않았습니다" + 사건 선택으로 돌아가기.
- **offline**: 다운로드 일시정지 → "네트워크 연결을 확인하세요" + 자동 재시도 안내.
- **partial_sync**: 일부 항목 실패 후 사용 → 사용 가능 항목 안내 + 실패 항목 재시도 옵션. 타일 미완료 시 "지도 사용 제한" 경고.
- **stale**: 패키지 manifest 만료 → "전체 수색 구역이 변경되었습니다. 다시 다운로드하세요" + `재다운로드` 큰 버튼.
- **incident_closed**: 다운로드 중 사건 종료 → 즉시 중단 + 사건 종료 후 로컬 정리 화면으로 이동.

### 3.14 Polifon 수색 중 지도

- **default**: 전체화면 지도(다크 테마) + 상단 사건/OP/동기화 상태 배지 + 하단 바텀시트 핸들. 운용 중인 폴리폰 위치 강조(시안 outline). 다른 폴리폰 위치는 일반.
- **loading**: 지도 베이스맵 즉시. 경로·마커 progressive.
- **empty**: 경로 기록 전 → 큰 `수색 시작` CTA 강조(72px 높이). 마커 0개일 때는 표시 없음.
- **error**: GPS 신호 약함 → 상단 노란 배너 "GPS 신호가 약합니다" (FR-29). 지도는 마지막 위치 유지.
- **permission_denied**: 사건 미배정 → 진입 차단, 사건 선택으로.
- **permission_partial**: 마커 상세에서 자기 계정 생성분이 아닌 마커 수정 시도 → 비활성 + tooltip.
- **offline**: 상단 노란 배너 "오프라인 — N건 미전송". GPS 기록·마커 생성 모두 정상 가능 (로컬 저장).
- **partial_sync**: 미전송 큐 5건+ → 배너 색 변경 + 카운트 강조. 20건+ → 빨강 + 알림.
- **stale**: 다른 폴리폰 위치 stale 시 → 해당 점 outline 주황 + 라벨 (Web과 같은 규칙, FR-24).
- **incident_closed**: 사건 종료 알림 수신 → 지도 dimmed + 모달 "사건이 종료되었습니다" + 사건 종료 후 로컬 정리 화면으로 이동.

### 3.15 Polifon 마커 생성 바텀시트

- **default**: 5종 마커 유형 큰 아이콘 그리드(2×3 또는 5×1, 각 56px+) + 메모 입력 + 사진 첨부 + `저장` 큰 버튼(72px).
- **loading**: 저장 요청 중. 버튼 비활성 + 스피너.
- **error**: 저장 실패(권한·서버 오류) → 바텀시트 위 토스트 "저장 실패. 다시 시도하세요" + 입력 유지.
- **permission_denied**: 사건 미배정 단말 → 바텀시트 자체 진입 차단 (사건 진입 단계에서 차단).
- **offline**: 정상 동작(로컬 저장 + outbox). 저장 후 토스트 "오프라인 저장됨. 연결 시 자동 전송".
- **partial_sync**: 미전송 큐 누적 시 바텀시트 상단에 작은 카운트 배지 (현재 큐 N건).

### 3.16 Polifon 미전송 큐

- **default**: 큐 항목 리스트(유형별 그룹: 경로·마커·사진). 각 항목 시각 + 재시도 상태. 상단 전체 카운트.
- **loading**: 큐 로드 시 skeleton (보통 즉시 로컬에서 읽음).
- **empty**: 큐 0건 → "모든 기록이 동기화되었습니다" 초록 체크 표시.
- **error**: 큐 항목 재전송 3회 실패 → 해당 항목 빨강 + "재시도 실패" 라벨. 사용자 수동 `재시도` 버튼.
- **permission_denied**: N/A (자기 단말 큐만 보므로).
- **offline**: 정상 표시 + 상단 "오프라인" 배너. 자동 전송 대기 중 안내.
- **partial_sync**: default와 거의 동일. 일부 ack 처리 중 항목은 노란색.

### 3.17 Polifon 로컬 경고

- **default**: 화면 자체로는 별도 페이지가 아니라 **다른 화면 위에 띄우는 배너/토스트**. 조건별 (GPS 중단·배터리 저하·지도 미다운로드) 색·아이콘 다름.
- **empty**: 경고 없을 때는 표시 안 함.
- **permission_denied**: N/A.
- **offline**: "오프라인" 배너 자체가 로컬 경고의 한 형태.
- **incident_closed**: 사건 종료 시 모든 로컬 경고 dismiss.

### 3.18 Polifon 사건 종료 후 로컬 정리

- **default**: 사건 종료 알림 + 로컬 정리 진행률 (실종자 정보 제거 / 미전송 ack 후 좌표 파기 / 패키지 삭제). 자동 진행. 사용자 입력 없음.
- **loading**: default와 동일 (정리 중 자체가 loading).
- **error**: 정리 실패 항목 → 빨간색 + 재시도 자동(WorkManager). 3회 실패 시 사용자에게 IT 문의 안내.
- **permission_denied**: N/A (자기 단말 로컬만).
- **offline**: 미전송 ack 대기 중일 때 → "동기화 완료 후 정리됩니다" 안내. 정리는 ack 후에만 (PRD §8.5).
- **partial_sync**: 일부 항목만 ack됨 → 진행률 부분 진행. ack 안 된 항목은 closed final 상태로 전이 (PRD §8.5 단말 로컬 삭제 트리거).
- **incident_closed**: 화면 자체가 incident_closed 상태에서만 표시.
- **purging**: 정리 진행 중이 곧 purging.

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
