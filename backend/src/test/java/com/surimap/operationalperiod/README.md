# operationalperiod 테스트 안내

이 패키지는 S8 operational-period 관련 fixture, test double, contract test를 담는다.

## 디렉터리 역할

- `fixture/`: 고정 값, DTO factory, 계약용 데이터
- `fixturetest/`: fixture 값이 문서 계약과 정확히 맞는지 확인하는 테스트
- `testdouble/`: 실제 구현이 없을 때 동작을 흉내 내는 테스트 대역

## 파일 설명

### 최상위 contract test

- `Op1BootstrapContractTest.java`
  - 주요 검증 메서드: `incident_created_처리_후_op1_completion과_current_list_op를_제공한다()`, `op1_bootstrap_이벤트는_fromOpId가_null이다()`, `중복_incident_created는_같은_op1_completion과_publish_request_1회로_수렴한다()`
  - 용도: `INCIDENT_CREATED -> OP1 completion` bootstrap mock 계약 검증

- `CurrentOpGuardContractTest.java`
  - 주요 검증 메서드: `current_op_없으면_op_required_409로_거부된다()`, `opId_불일치하면_op_mismatch_409로_거부된다()`, `opId_일치하면_guard를_통과한다()`
  - 용도: `op_required`, `op_mismatch`, 허용 경로 검증

- `CurrentOpConsumerContractTest.java`
  - 주요 검증 메서드: `current_op_소비자는_같은_op1_fixture를_사용한다()`, `current_op_mock은_다른_lane_owner_write를_만들지_않는다()`
  - 용도: current OP 소비자 계약과 forbidden owner write 검증

### `fixture/`

- `OperationalPeriodFixtures.java`
  - 주요 상수: `INCIDENT_ALIAS`, `INCIDENT_ID`, `CURRENT_OP_ALIAS`, `CURRENT_OP_ID`, `CURRENT_OP_STATUS`, `CURRENT_OP_SEQUENCE_NO`, `CURRENT_OP_STARTED_AT`, `CURRENT_OP_REASON`, `CURRENT_OP_VERSION`, `NEW_OP_ALIAS`, `NEW_OP_ID`, `OP_STATUSES`, `OP_REASON_PERSISTED`, `OP_REASON_MANUAL_CREATE`
  - 주요 메서드: `currentOp()`, `currentOpResult()`, `currentOpResult(UUID)`, `currentOpListRow()`, `currentOpListRow(UUID)`, `previousOpAfterTransition()`, `newOpAfterTransition()`
  - 포함 record: `OpRow`, `OpTransitionedEvent`
  - 용도: S8 current OP, list row, transition row의 공통 기준 fixture

- `OpBootstrapFixtures.java`
  - 주요 메서드: `incidentCreatedEvent()`, `op1BootstrappedEvent()`, `op1BootstrappedEvent(UUID)`, `op1BootstrapCompletion()`
  - 포함 record: `IncidentCreatedEvent`, `Op1BootstrapCompletion`
  - 용도: OP1 bootstrap 입력과 completion 결과 fixture

- `CurrentOpGuardFixtures.java`
  - 주요 상수: `OP_REQUIRED_ERROR`, `OP_MISMATCH_ERROR`
  - 주요 메서드: `opRequiredGuard()`, `opMismatchGuard()`, `currentOpAllowed(UUID)`, `currentOpRejected(GuardFailure)`
  - 포함 record: `GuardFailure`, `CurrentOpGuardDecision`
  - 용도: `@RequireCurrentOp` 실패/성공 응답 fixture

- `CurrentOpConsumerFixtures.java`
  - 주요 상수: `CURRENT_OP_CONSUMERS`, `FORBIDDEN_S8_OWNER_WRITES`
  - 주요 메서드: `currentOpConsumerProbes()`
  - 포함 record: `CurrentOpConsumerProbe`
  - 용도: current OP를 소비하는 Lane 목록과 owner-write 금지 목록

- `OpTransitionFixtures.java`
  - 주요 상수: `OP_TRANSITIONED_EVENT_ID`, `SC10_BOARD_PROBE_SLOTS`, `SC10_HANDOVER_STATUS`, `PRE_CONVERGENCE_STATE`
  - 주요 메서드: `opTransitionRestResponse()`, `opTransitionedEvent()`, `opTransitionBoardProbe()`
  - 포함 record: `OpTransitionRestResponse`, `OpTransitionBoardProbe`
  - 용도: SC-10 OP 전환 response/event/board 수렴 fixture

- `S8IdempotencyFixtures.java`
  - 주요 상수: `IDEMPOTENCY_KEY`, `IDEMPOTENT_BODY_HASH_ORIGINAL`, `IDEMPOTENT_BODY_HASH_CHANGED`, `IDEMPOTENT_ENDPOINT`, `IDEMPOTENCY_MISMATCH_ERROR`, `IDEMPOTENT_COVERED_ENDPOINTS`
  - 주요 메서드: `idempotentSameBodyReplay()`, `idempotentMismatch()`
  - 포함 record: `IdempotentSameBodyReplay`, `IdempotentMismatch`
  - 용도: S8 write idempotency replay와 mismatch fixture

- `OpAssignmentFixtures.java`
  - 주요 상수: `OP_ASSIGNMENT_STATUSES`, `OP_ASSIGNMENT_TYPES`, `ASSIGNMENT_QUERY_ROW_SHAPE`
  - 주요 메서드: 없음
  - 용도: assignment 전용 문자열 집합과 row shape 기준값

- `HandoverAiFixtures.java` (구조 정리 예정)
  - 주요 상수: `INCIDENT_ALIAS`, `INCIDENT_ID`, `OP_ID`, `HANDOVER_MEMO_TARGET_TYPES`, `HANDOVER_MEMO_WRITE_CHANNELS`, `SC11_*`, `MOCK_*`, `AI_SUMMARY_*`, `FORBIDDEN_AI_SUMMARY_*`
  - 주요 메서드: `sc11HandoverMemo()`, `sc11AiSummary()`, `sc11HandoverMemoCreatedEvent()`, `sc11AiSummaryReadyEvent()`, `sc11HandoverMemoQueryProbe()`, `sc11AiSummaryQueryProbe()`, `sc11BoardProbe()`, `mockAdapterSuccess()`, `mockAdapterFailure()`, `mockAdapterForbiddenPhrase()`, `mockAdapterLowQuality()`
  - 포함 record: `HandoverMemoRow`, `AiSummaryRow`, `HandoverMemoCreatedEvent`, `AiSummaryReadyEvent`, `HandoverMemoQueryProbe`, `AiSummaryQueryProbe`, `Sc11BoardProbe`, `HandoverMemoSlot`, `HandoverStatusSlot`, `AiSummarySlot`, `MockAdapterSuccess`, `MockAdapterFailure`, `MockAdapterForbiddenPhrase`, `MockAdapterLowQuality`
  - 용도: SC-11 handover memo와 AI summary 수렴 fixture

### `fixturetest/`

- `OperationalPeriodFixtureExactnessTest.java`
  - 주요 검증 메서드: `공통_op_fixture_값이_고정되어_있다()`, `bootstrap_fixture_값이_고정되어_있다()`, `guard_consumer_transition_idempotency_fixture_값이_고정되어_있다()`
  - 용도: core operational-period fixture 값이 문서 계약과 맞는지 검증

### `testdouble/`

- `Op1BootstrapMock.java`
  - 주요 메서드: `handle(IncidentCreatedEvent)`, `publishRequestCount()`
  - 용도: `INCIDENT_CREATED`를 OP1 bootstrap completion으로 바꾸는 mock handler
  - 내부 상태: incident별 completion 캐시, publish 요청 횟수

- `CurrentOpGuardMock.java`
  - 주요 메서드: `requireCurrentOp(UUID, UUID)`
  - 용도: `@RequireCurrentOp`의 실패/성공을 흉내 내는 mock guard
  - 의존성: `OperationalPeriodQuery.current(UUID)`
