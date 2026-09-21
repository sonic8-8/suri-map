# 화면 구성과 시안 찾기

2026-05의 화면 설계 의도와 원본 시안 목록이다. W·P·M 번호는 과거 자료를 찾기 위한 식별자이며 현재 구현 단위·작업 순서가 아니다. 긴 ASCII 도면과 중복 API 설명은 Git 원문에 보존했다.

상태별 요구는 [화면 상태](screen-state-matrix.md), 권한 가정은 [화면 권한](permission-matrix.md), 제품 목적은 [PRD](../prd.md)에서 확인한다. 시안이 있다는 사실을 API 연결·저장·알림 검증 완료로 해석하지 않는다.

## 웹 지휘 화면

| 옛 구분 | 화면에서 하려던 일 | 자료·확인 위치 |
|---|---|---|
| W1 로그인 | 접속·실패 안내. 자체 계정 발급·비밀번호 재설정은 제외 | [인증의 실제 흐름](../authentication.md). 옛 ID/PW 폼을 현재 OIDC 화면으로 간주하지 않음 |
| W2 사건 목록·가져오기 | 배정 사건 선택, 권한자의 원천 사건 가져오기, 종료 사건 구분 | [사건 생명주기](../incident-lifecycle.md) |
| Hero 1 상황판 | 사건·수색 차수 맥락, 지도·구역·경로·마커·업무폰 상태 함께 확인 | [HTML 시안](artifacts/lo/lo-web-situation-board-main-v1.3.html), [리뷰](artifacts/lo/lo-web-situation-board-main-v1.3-review.html) |
| W3 구역 편집 | 전체 범위 그리기 → 하위 구역 분할·담당 배정, 사람이 완료 처리 | [구역 편집 설계 배경](../contracts/area-edit-screen-plan.md). 분할을 수색 시작의 필수 절차로 단정하지 않음 |
| W4 차수 비교·인수인계 | 임의 차수를 같은 지도에서 비교, 근무 구간·메모·요약·원본 연결 | [인수인계](../handover.md). 요약 생성·재시도 버튼은 옛 시안과 현재 요구가 다름 |
| W5 마커 상세 | 유형·위치·발생 시각·작성 계정·사진·메모 확인, 허용된 수정·삭제 | [마커·사진](../marker-photo.md). 앱 사진 업로드를 웹에도 자동 허용하지 않음 |
| W6 차량·도보 보정 | 경로 구간을 고르고 자동 분류 결과를 확인·보정 | [수색 경로](../search-path.md). 지도 조회와 원본 변경은 다른 동작 |
| W7 패키지 상태 | 업무폰별 적재 완료·실패·마지막 보고 확인 | [오프라인 패키지](../offline-package.md). 웹에서 단말 다운로드를 재시도시키는 기능은 옛 제안이지 구현 보장이 아님 |
| W8 사건 종료·파기 | 종료 확인, 종료·파기 시각과 허용 메타 표시 | [데이터 파기](../data-retention.md). 개인정보·좌표·재다운로드·재구독은 종료 화면에 되살리지 않음 |

상황판 첫 지도는 전체 구역이 없어도 열려야 한다는 의도가 있었다. 기준 마커와 기본 지역의 우선순위·현재 코드 차이는 [최초 지도 범위](../contracts/situation-board-initial-map-plan.md)에 있다.

## Android 현장 입력 화면

[업무폰 시안 모음](artifacts/lo/lo-polifon-wireframes-v1.html)에서 관련 화면만 선택한다. 당시 Knox 관리·장기 세션·팀/순찰차 공유 운용을 가정했지만 실제 연동 검증과는 별개다.

| 옛 구분 | 화면에서 하려던 일 | 원본 시안 |
|---|---|---|
| P1 인증 후보 | 관리 단말 자동 확인·로그인·바인딩 대안 비교. 실제 사용자 화면이 아닌 검토 보드 | [인증 후보](artifacts/lo/lo-polifon-auth-v1.html) |
| P1-B 접속 확인 | 관리 설정·내부망·서버 거부를 구분하고 인증 전 사건 정보 숨김 | [관리 업무폰 확인](artifacts/lo/lo-polifon-auth-managed-device-v1.html) |
| P2 사건 선택 | 배정 사건 선택, 빈 목록·캐시·종료 알림 표시. 앱에서 사건 가져오기 제외 | [사건 목록](artifacts/lo/lo-polifon-incident-list-v1.html) |
| P3 패키지 다운로드 | 항목별 적재, 실패 부분 재시도, 지도 제한과 준비 완료 구분 | [패키지](artifacts/lo/lo-polifon-offline-package-v1.html) |
| Hero 2 수색 지도 | 경로 기록 상태·근무 구간·담당 구역·자기 업무폰 강조, 시작·일시정지·재개·종료 | [지도](artifacts/lo/lo-polifon-search-map-v1.html), [리뷰](artifacts/lo/lo-polifon-search-map-v1-review.html) |
| Hero 3 마커 입력 | 5종 유형 선택, 지원 요청 세부 종류, 위치·시각 기록, 메모·사진 추가 | [입력 시트](artifacts/lo/lo-polifon-marker-bottomsheet-v1.html), [리뷰](artifacts/lo/lo-polifon-marker-bottomsheet-v1-review.html) |
| P4 미전송 진단 | 자동 전송 대기와 사용자가 확인할 처리 불가 사유 구분 | [진단](artifacts/lo/lo-polifon-outbox-v1.html) |
| P5 로컬 경고 | GPS·배터리·지도 미다운로드 등 지도 위 배너 표현. 별도 업무 화면은 아님 | [경고 변형](artifacts/lo/lo-polifon-local-warning-v1.html) |
| P6-A 이전 근무 확인 | 원본 기록·요약 상태 확인, 수색으로 복귀. 자동 강제 이동보다 확인 배너를 제안 | [이전 근무](artifacts/lo/lo-polifon-duty-handover-v1.html) |
| P6-B 메모 작성 | 차수·근무 구간·경로·구역·마커 중 대상 선택, 초안 유지·로컬 저장·전송 상태 표시 | [메모](artifacts/lo/lo-polifon-handover-memo-v1.html) |
| P7 종료 안내 | 사건 선택 위 다이얼로그와 백그라운드 정리로 표현. 독립 파기 진행 화면은 제외 | P2 시안의 종료 변형 |
| P8 강조 알림 | 실종자 발견·지원 요청 인지, 확인 또는 해당 마커 위치로 이동 | [알림](artifacts/lo/lo-polifon-incident-alert-v1.html) |
| P9 마커 상세 | 메타·사진 우선 확인, 권한별 수정·삭제, 명시적 삭제 확인 | [마커 상세](artifacts/lo/lo-polifon-marker-detail-v1.html) |
| P10 환경 거부 후보 | Knox가 처리한다고 가정해 별도 화면에서 제외 | 실제 관리 정책·OS 권한은 [Knox 보류 계획](../tasks/knox-tasks.md)과 코드 대조 |

P5 번호는 일부 자료에서 수색 지도, 다른 자료에서 로컬 경고를 가리킨다. 번호만 보고 같은 화면이라고 판단하지 않는다.

## 별도 검토했던 입력·확인 동작

| 옛 구분 | 남길 의도·미확인 사항 |
|---|---|
| M1 새 수색 차수 | 재수색·범위 변경·기타 사유를 기록. 최초 차수 자동 생성과 구분. 기타 사유 메모·이전 차수 메모 처리의 실제 요구는 인수인계 문서 확인 |
| M2 인수인계 메모 | 대상 선택과 본문 입력. Web 모달·앱 시트는 표현 차이. 시안의 500자와 기존 기능 요구의 4,000자는 같지 않음 |
| M3 마커 수정 | 유형·메모·사진 변경을 구분. 앱·웹 권한·지원 API를 확인하고 생성 시트와 같다고 가정하지 않음 |
| M4 위치 수동 조정 | GPS가 부정확할 때 핀 이동·GPS로 복원·취소·확정 후 입력 시트 복귀를 제안. 저장 후 위치 수정 허용 여부는 원문 안에서도 충돌 |
| M5 배정·종료 알림 | 배정은 정보 재조회·패키지 안내, 종료는 새 입력 차단·민감 데이터 정리. 알림 수신만으로 실제 파기 성공을 보장하지 않음 |

삭제·수색 종료·사건 종료는 명시적으로 확인한다. 되돌릴 수 없는 동작에 짧은 길게 누르기나 가짜 실행 취소를 유일한 안전장치로 두지 않는 의도를 유지한다. 카메라·권한·사진 확대의 구체 구현은 플랫폼 코드에서 확인한다.

## 원본과 검증 범위

- 전체 ASCII 도면·W1~W8·P1~P7·M1~M5 설명: [정리 전 원문](https://github.com/sonic8-8/suri-map/blob/3cd777752e2178ebb3470e6f749b1521ce404e7a/docs/screen-design/wireframes.md)
- [시연 프로토타입](artifacts/proto/index.html)은 브라우저 저장소의 가짜 상태를 바꾸는 시연이다. API·DB·FCM 검증이 아니다.
- [실기기 QA](artifacts/device-qa/AUI-T14/report.md)는 디버그 화면 표시 기록이다. 원본 HTML·화면·XML은 이번 정리에서 수정하지 않았다.
- 역할·요약·종료·오프라인 처리의 문서 간 충돌은 [화면 상태의 재검토 항목](screen-state-matrix.md#그대로-구현하면-안-되는-충돌)에 모았다. “시안 1회 작성 후 반복하지 않음”과 같은 과거 제작 규칙은 폐기했다.
