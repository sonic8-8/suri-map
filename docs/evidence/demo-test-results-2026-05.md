# 2026년 5월 시연 준비 테스트 기록

팀 프로젝트 당시 L1·L3·L5·L6에서 기록한 테스트 결과를 모았다. 아래 PASS는 **당시 실행 기록의 판정**이며 현재 제품이나 실제 현장 흐름의 통과를 뜻하지 않는다. 문서 정리 중 제품 테스트를 다시 실행하지 않았다.

## 무엇을 확인했는가

| 날짜·옛 분류 | 대상·당시 결과 | 검증 범위·한계와 원문 |
|---|---|---|
| 05-08 · L5-D01 | 마커 생성·사진 첨부·지원 요청·발견 알림 — PASS | Backend 하네스와 업로드·FCM 대역. 실제 S3·푸시 수신·Android 알림 화면은 미검증. [명령·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l5-demo-smoke-evidence.md) |
| 05-10 · L6-D01 | 패키지·지도 입력·상황판 조립·요약 표시·종료 응답 — PASS | Backend 12개·Frontend 1개 테스트와 입력·문구 검사 기록. 실제 타일 요청 캡처·브라우저 SSE 재연결은 미검증. [명령·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l6-demo-smoke-evidence.md) |
| 05-11 · L1-D01A | 사건 가져오기·추가 배정·패키지 준비 — PASS | Backend 하네스·공용 입력 검사. 실제 112·FCM·외부 타일·단말 장시간 시험은 제외. [명령·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l1-d01a-rehearsal-evidence.md) |
| 05-13 · L1-D01B | 구역·경로·마커·사진과 상황판 조립 — PASS | 고정 입력·대역 기반 결과. 실제 앱 입력·브라우저 화면·GPS 이동 시험과 구분. [명령·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l1-d01b-rehearsal-evidence.md) |
| 05-13 · L1-D01C | 단절 중 저장·알림·복구·중복 방지 — PASS | Backend 대역과 Android Unit·Room 검사. 30분 경과는 고정 입력이며 실제 30분 단절·OS 알림·자동 복구 시험이 아님. [명령·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l1-d01c-rehearsal-evidence.md) |
| 05-13 · L1-D01D | 차수 전환·인수인계·요약·종료·파기 — PASS | Backend·Frontend·Android 테스트 및 선행 문구 검사 결과. 실제 24시간 경과·서버와 단말의 전체 파기·외부 OpenAI 호출은 미검증. [명령·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l1-d01d-rehearsal-evidence.md) |
| 05-13 · L3-D01 | 구역·차수·메모·요약 입력 — PASS | OP1 seed 메모와 OP2 작성 메모를 분리해 입력 충돌 해결. 당시 Backend 전체 테스트 통과도 기록했으나 실제 시연 성공으로 확대하지 않음. [명령·결함·결과](https://github.com/sonic8-8/suri-map/blob/c88365473bc459637f1e3c664b30e241f7489715/docs/tasks/l3-d01-demo-evidence.md) |

## 어떻게 해석해야 하는가

- L1의 종합 기록은 L3·L5·L6와 같은 입력·테스트를 다시 사용한다. 문서가 여러 개라는 이유로 독립된 실환경 검증 횟수로 세지 않는다.
- OP1의 `memo-precinct-handover-001`과 OP2의 `memo-precinct-op2-001`은 서로 다른 입력이다. 당시 수정 기록 `S14P31C106-204`와 상세 식별자는 L3 원문에 보존했다.
- 문서에 특정 문구·Jira 번호·PASS가 있는지 확인한 결과도 포함돼 있다. 이 검사들은 제거했으며 제품 동작의 증거로 사용하지 않는다.
- 원문은 당시 명령·커밋·Jira·기대값·테스트 수·한계를 보존한다. `build/test-results` 같은 경로는 실행 당시 위치이지 당시 보고서가 현재도 남아 있다는 보장이 아니다. 옛 테스트 이름·슬롯·수치도 현재 계약으로 다시 채택하지 않는다.
- 실제 단말 시험은 [Android 네트워크 복구 기록](android-network-recovery-2026-05-14/README.md)에 따로 남겼다. 최종 판정은 `PARTIAL / NOT FINAL`이다.

새 검증에서는 [기능별 검증 흐름](../tasks/scenario-exit-criteria.md)과 [공용 테스트 입력](../../test-fixtures/README.md)을 확인하고 실제로 연결한 구간·대역·실행 조건을 구분한다.
