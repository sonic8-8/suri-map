# 화면 설계 참고 자료

화면을 바꿀 때 필요한 사용 의도·설계 이유·미확인 요구를 찾는 곳이다. 현재 UI 값과 동작은 제품 코드에서 확인하며, 과거 시안이나 화면 캡처를 현재 구현 완료의 근거로 사용하지 않는다.

## 필요한 자료 찾기

| 확인할 내용 | 문서 |
|---|---|
| 현장 환경·규모 가정과 사용자 조사 출처 | [field-context.md](field-context.md) |
| 화면별 상태·행동·서로 충돌하는 기존 요구 | [screen-state-matrix.md](screen-state-matrix.md) |
| 역할별 접근·수정 권한을 나눈 배경 | [permission-matrix.md](permission-matrix.md) |
| 라벨·색·배치의 의도와 화면에서 확인할 항목 | [screen-guidelines.md](screen-guidelines.md) |
| Android UI 요구·실제 코드 위치·과거 검증 범위 | [android-ui-requirements.md](android-ui-requirements.md) |
| 구역 편집의 진입·초안 보존·저장 동선 | [area-edit.md](area-edit.md) |
| 전체 수색 구역이 없을 때 최초 지도 범위 | [situation-board-initial-map.md](situation-board-initial-map.md) |

## Git에서 찾는 과거 시안

제품·빌드에서 직접 참조하지 않는 시안 84개는 작업 트리에서 제거했다. 원본은 아래 고정 커밋에서 복원할 수 있다. 제품의 폰트·CSS·화면 코드는 정리 대상이 아니다.

| 자료 | 원문 |
|---|---|
| HTML 시안·리뷰·CSS 18개 | [artifacts/lo](https://github.com/sonic8-8/suri-map/tree/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/screen-design/artifacts/lo) |
| 브라우저 저장소로 동작하는 시연 코드 11개 | [artifacts/proto](https://github.com/sonic8-8/suri-map/tree/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/screen-design/artifacts/proto) |
| 가져온 디자인 참고 자료·포털 예제·React 시안 55개 | [Korean Government UIUX Design System](https://github.com/sonic8-8/suri-map/tree/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/screen-design/Korean%20Government%20UIUX%20Design%20System) |

시연 코드는 실제 API 대신 가짜 상태를 사용하며 일부 초기화 버튼은 `localStorage.clear()`를 호출한다. 복원해 실행할 때는 실제 서비스와 분리된 origin·브라우저 프로필을 사용한다. 외부 CDN·한글 폰트와 가져온 자료의 라이선스도 별도 확인한다. 포함된 옛 `SKILL.md`는 현재 에이전트 지침이 아니다.

옛 W·P·M·G 번호, 화면 도면과 제작 절차는 [정리 전 문서 묶음](https://github.com/sonic8-8/suri-map/tree/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/screen-design)에서 찾는다. 전체 화면 명세 작성이나 고정 횟수의 시안 생성을 구현의 선행 조건으로 두지 않는다.

## 실기기 QA 자료의 쓰임

2026-05-11 Galaxy S22에서 당시 화면 상태를 표시한 기록이다. 보고서 1개·화면 12개·UI XML 11개·검색 결과 1개는 [고정 Git 원본](https://github.com/sonic8-8/suri-map/tree/403b383dc2693fd15d1616ee7206e6cb0e420df5/docs/screen-design/artifacts/device-qa/AUI-T14)에서 복원한다. 현재 테스트 입력·결함 재현 자료로 쓰이지 않아 사용자와 합의해 작업 트리에서 제거했다.

2026-09-21에 보고서·대표 화면 3개·XML 11개·검색 결과·현재 QA 코드를 대조한 뒤 다음 관찰과 한계를 남겼다.

- 일반 실행은 접속 확인 실패까지만 기록했다. 나머지는 주입한 디버그 상태이며 동기화·요약 완료 표시나 서로 다른 미전송 건수는 실제 서버 처리·복구의 증거가 아니다.
- 알림과 인수인계 배너가 지도를 가린 화면이 있었다. 당시 클릭 영역 검사는 px 기준이라 48dp 접근성 충족을 입증하지 않는다.
- 도메인 검색 결과는 원본 로그가 없어 재실행할 수 없다. 문자열을 찾지 못했다는 기록만으로 외부 통신이 없었다고 판단하지 않는다.
- 현재 [DeviceQaActivity](../../android/app/src/debug/java/com/surimap/ui/qa/DeviceQaActivity.kt)의 수색 지도 분기는 실제 세션·API를 읽도록 달라졌다. 당시 캡처를 현재 기능·현장 사용성·회귀 검증의 통과 기준으로 삼지 않는다.
