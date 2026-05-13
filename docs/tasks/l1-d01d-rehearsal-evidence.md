# L1-D01D 리허설 실행과 시연 증거 수집

## 범위

이 문서는 `L1-D01D` / `S14P31C106-297`의 Phase 5 evidence 문서다. `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md`를 실행 기준으로 삼고, PRD §2.3과 PRD §5.1의 12단계 중 SC-10~12에 해당하는 구역 완료·새 OP 열기, 인수인계·OP 비교·수색 이력 요약, 사건 종료·데이터 파기 증거를 기록한다.

증거는 backend 하네스, frontend board slot 테스트, Android unit/Room local cleanup 테스트, fixture preflight 결과 기준이다. 실제 현장 지휘관 브라우저 조작 영상, 실제 Android 단말 화면, 실제 24시간 soft delete 경과, 실제 외부 OpenAI/FCM/S3 호출은 이 문서에서 주장하지 않는다.

## 실행 메타

| 항목 | 값 |
|---|---|
| Task | `L1-D01D` |
| Jira | `S14P31C106-297` |
| Branch | `feature/S14P31C106-297-l1-d01d-rehearsal-evidence` |
| Base SHA | `e7c1459` |
| Evidence timestamp | `2026-05-13T15:18:42+09:00` |
| 기준 runbook | `docs/tasks/l1-rehearsal-checklist-recovery-runbook.md` |
| 기준 문서 | PRD §2.3, PRD §5.1, `docs/spec/harness-scenarios.md` |
| Backend Test XML directory | `backend/build/test-results/test/` |
| Android Test XML directory | `android/app/build/test-results/testDebugUnitTest/` |

## 실행한 증거 명령

| # | 명령 | 목적 | 결과 | 산출물 |
|---|---|---|---|---|
| 1 | `python3 docs/spec/fixtures/preflight_common_fixtures.py` | SC-10~12 공통 fixture ID와 alias가 기준 문서와 맞는지 사전 확인 | PASS, `common fixture preflight passed` | 콘솔 출력 |
| 2 | `python3 docs/tasks/check_l3_d01_demo_evidence.py` | L1-D01D 선행 L3-D01 demo geometry/OP/handover evidence 확인 | PASS | 콘솔 출력 |
| 3 | `python3 docs/tasks/check_l6_demo_smoke_evidence.py` | L1-D01D 선행 L6-D01 board/package/tile/terminal evidence 확인 | PASS | 콘솔 출력 |
| 4 | `cd backend && ./gradlew --no-daemon test --tests com.surimap.harness.sc10.Sc10OpHandoverHarnessTest --tests com.surimap.summary.SearchHistorySummaryGenerationCompletionTest --tests com.surimap.handover.SearchHistorySummaryReadOnlyApiContractTest --tests com.surimap.handover.S8HandoverApiContractTest --tests com.surimap.harness.sc12.Sc12IncidentCloseDataPurgeIntegrationTest --tests com.surimap.board.IncidentTerminalPackageBadgePrivacyRedTest --tests com.surimap.board.BoardApiSseConvergenceHarnessRedTest --console=plain` | SC-10 OP/handover, SC-11 summary, SC-12 terminal/purge, board 수렴 하네스 증거 수집 | PASS, `BUILD SUCCESSFUL in 42s` | `backend/build/test-results/test/` |
| 5 | `cd frontend && npm test -- src/features/board/components/S8OpHandoverSlots.test.tsx src/features/board/components/SearchHistorySummarySlot.test.tsx src/features/board/components/Sc11OpSearchHistorySummaryHarnessRedTest.test.tsx` | 상황판 OP/handover/search_history_summary slot rendering guard 증거 수집 | PASS, 3 files / 7 tests | vitest 콘솔 출력 |
| 6 | `cd android && GRADLE_USER_HOME=/home/seaung13/workspace/S14P31C106/.gradle-cache ./gradlew :app:testDebugUnitTest --tests com.surimap.testing.AndroidHarnessFixtureCatalogTest --tests com.surimap.core.operationalperiod.HandoverRepositoriesTest --tests com.surimap.feature.handover.HandoverMemoLocalRecorderRoomTest --tests com.surimap.feature.handover.DutyHandoverStateLoaderTest --tests com.surimap.core.sync.IncidentLocalCleanupPolicyTest --console=plain` | Android handover/outbox/local cleanup 핵심 증거 수집 | PASS, `BUILD SUCCESSFUL in 10s` | `android/app/build/test-results/testDebugUnitTest/` |

선택 XML 증거:

| Test suite | 결과 |
|---|---|
| `TEST-com.surimap.harness.sc10.Sc10OpHandoverHarnessTest$RadioAndDecisionStep.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc10.Sc10OpHandoverHarnessTest$OpTransitionStep.xml` | 5 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc10.Sc10OpHandoverHarnessTest$HandoverSavedStep.xml` | 2 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc10.Sc10OpHandoverHarnessTest$BoardConvergenceStep.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.summary.SearchHistorySummaryGenerationCompletionTest.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.handover.SearchHistorySummaryReadOnlyApiContractTest.xml` | 2 tests, 0 failures, 0 errors |
| `TEST-com.surimap.handover.S8HandoverApiContractTest.xml` | 10 tests, 0 failures, 0 errors |
| `TEST-com.surimap.harness.sc12.Sc12IncidentCloseDataPurgeIntegrationTest.xml` | 1 tests, 0 failures, 0 errors |
| `TEST-com.surimap.board.IncidentTerminalPackageBadgePrivacyRedTest.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.board.BoardApiSseConvergenceHarnessRedTest.xml` | 5 tests, 0 failures, 0 errors |
| `TEST-com.surimap.core.operationalperiod.HandoverRepositoriesTest.xml` | 4 tests, 0 failures, 0 errors |
| `TEST-com.surimap.feature.handover.HandoverMemoLocalRecorderRoomTest.xml` | 1 tests, 0 failures, 0 errors |
| `TEST-com.surimap.feature.handover.DutyHandoverStateLoaderTest.xml` | 3 tests, 0 failures, 0 errors |
| `TEST-com.surimap.core.sync.IncidentLocalCleanupPolicyTest.xml` | 7 tests, 0 failures, 0 errors |
| `TEST-com.surimap.testing.AndroidHarnessFixtureCatalogTest.xml` | 2 tests, 0 failures, 0 errors |

## 공통 fixture ID 증거

| 항목 | 결과 |
|---|---|
| 대표 사건 | PASS. fixture alias는 `inc-precinct-first-001`, 실제 DB/API 식별자 기준 UUID는 `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001`이다. |
| OP1/OP2 | PASS. OP1 alias `op-precinct-001-op1`, OP2 alias `op-precinct-001-op2`, OP2 UUID `88888888-8888-8888-8888-888888880002`가 유지된다. |
| SC-10 OP transition | PASS. `evt-s8-op-transitioned-001`와 실행 순서 `radio_report_received -> commander_decision_recorded -> area_completed -> op_transitioned -> handover_saved -> board_api_refetched`가 유지된다. |
| handover memo | PASS. memo alias `memo-precinct-op2-001`, UUID `eeeeeeee-eeee-eeee-eeee-eeeeeeee0010`, event `evt-s8-handover-memo-001`가 유지된다. |
| search history summary | PASS. summary alias `summary-precinct-op2-001`, UUID `44444444-4444-4444-4444-444444440001`, event `evt-s8-ai-summary-001`가 유지된다. |
| terminal/purge | PASS. close response는 사건 UUID와 terminal snapshot만 노출하고, purge completion은 `INCIDENT_PURGED`와 board `incident_terminal` sanitized row로 수렴한다. |

## SC-10 구역 완료·새 OP 열기 증거

| 확인 항목 | 결과 |
|---|---|
| radio/decision sequence | PASS. 무전 보고 수신과 지휘관 결정 기록이 선행된 뒤 area completion, OP transition, handover 저장, board refetch 순서로 실행된다. |
| OP transition | PASS. OP1은 완료된 이전 OP로, OP2는 `ACTIVE` 현재 OP로 수렴하고 `OP_TRANSITIONED` event fixture가 검증됐다. |
| handover memo | PASS. OP2 context handover memo가 `ACTIVE`, version `1`로 저장되고 `HANDOVER_MEMO_CREATED` event와 연결된다. |
| board 수렴 | PASS. `area`, `op_toggle`, `op_history`, `handover_memo`, `handover_status` slot이 source version 이상 board response로 수렴한다. |
| 금지 조건 | PASS. 무전 보고 seed만으로 자동 구역 완료나 OP2 생성이 처리되지 않고, 지휘관 판단 단계가 유지된다. |

## SC-11 인수인계·OP 비교·수색 이력 요약 증거

| 확인 항목 | 결과 |
|---|---|
| summary generation | PASS. provider success는 `READY`, provider failure/timeout/schema failure는 `FAILED`로 저장되고 `SEARCH_HISTORY_SUMMARY_CHANGED`를 발행한다. |
| forbidden phrase guard | PASS. `다음 구역 추천`, `위험도 높음`, `누락 확정`, `자동 판단` 계열 응답은 최종 요약으로 노출되지 않는다. |
| read-only API | PASS. APP/WEB 공개 생성·재시도 API 없이 read-only summary 조회 계약이 유지된다. |
| frontend rendering | PASS. OP/handover/search_history_summary slot 테스트 7개가 통과했고, 추천 CTA나 위험도 판단 UI를 만들지 않는다. |
| Android handover | PASS. Android handover repository와 Room local recorder가 handover memo/outbox 경계를 검증했다. |

## SC-12 사건 종료·데이터 파기 증거

| 확인 항목 | 결과 |
|---|---|
| close command | PASS. `POST /api/incidents/{incidentId}/close` WEB command가 `CLOSED`, version `2`, `writeDisabledReason=incident_closed` 응답과 terminal snapshot을 반환한다. |
| PII removal | PASS. close 후 `missing_person` active row가 제거되고 terminal snapshot, event payload, board terminal row에서 displayName/photoObjectKey/lastSeenLocationText가 노출되지 않는다. |
| write block | PASS. close 후 assignment/domain write는 `incident_closed`로 차단되고 row/event/board를 변경하지 않는다. |
| purge orchestration | PASS. `INCIDENT_CLOSED` 소비 후 purge run이 `PENDING`에서 `COMPLETED`, local purge state가 `LOCAL_PURGED`로 전환된다. |
| terminal board | PASS. board `incident_terminal` row가 `PURGED`, `closedStatus=purged`, `localPurgeState=completed`로 수렴하고 package reload/stream resubscribe hint를 포함하지 않는다. |
| Android local cleanup | PASS. `IncidentLocalCleanupPolicyTest`가 post-close local sync purge, failed retry retention, incident-scoped cleanup을 검증했다. |

## 실패 로그와 follow-up

| 구분 | failure log | follow-up fix task ID | 처리 |
|---|---|---|---|
| SC-10 failure log | 없음 | None | OP transition/handover/board evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| SC-11 failure log | 없음 | None | summary generation/rendering/forbidden guard evidence가 PASS라 별도 결함 task를 만들지 않았다. |
| SC-12 failure log | 없음 | None | close/purge/local cleanup/terminal board evidence가 PASS라 별도 결함 task를 만들지 않았다. |

## 결함 task 목록

| 결함 | Jira | 영향 증거 | 상태 |
|---|---|---|---|
| 없음 | N/A | SC-10~12 전체 | No defect task created |

## 남은 리스크

- 이 증거는 하네스/test-report evidence다. 실제 브라우저 상황판 조작 영상, 실제 Android 화면, 실제 연결 실기기 화면 검증은 `AUI-T14` 범위다.
- 실제 단말 2대와 상황판 1대의 1시간 안정성 및 네트워크 on/off 10회 검증은 `L4-D01` 범위다.
- 실제 24시간 soft delete 경과나 운영 보존 스토리지 장기 보관은 시간 이동 fixture와 local purge contract로만 검증했다.
- 실제 외부 OpenAI provider 호출은 이 문서에서 주장하지 않는다. summary provider success/failure/금지 문장 처리는 mock provider 기반이다.

## 완료 판정

Verdict: PASS.

완료 근거:

- SC-10 구역 완료·OP2 전환·인수인계 메모·board 수렴 증거가 fixture ID와 함께 남았다.
- SC-11 OP 비교·수색 이력 요약 생성/실패/금지 문장 guard·frontend slot evidence가 fixture ID와 함께 남았다.
- SC-12 사건 종료·PII 제거·purge orchestration·terminal board·Android local cleanup evidence가 fixture ID와 함께 남았다.
- SC-10~12 failure log가 모두 없음으로 기록됐고 follow-up fix task ID는 `None`이다.
- 결함 수정은 이 task에 섞지 않았고, 별도 결함 task는 생성하지 않았다.
