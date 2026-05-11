# L3-T05A OP1 Current OP Mock Contract Plan

## 기준 문서

- `docs/spec/boundaries.md`
- `docs/spec/specs/S8.json`
- `docs/tasks/L3-tasks.md`

이 계획은 위 문서를 source of truth로 삼는다. 문서와 코드가 다르면 문서 기준에 맞춘다.

## 작업 요약

`L3-T05A`는 실제 OP 생성 기능을 구현하는 단계가 아니다.

이번 단계의 목표는 다른 Lane이 믿고 사용할 수 있도록 OP1/current OP 관련 mock contract와 guard 실패 fixture를 고정하는 것이다.

구현하지 않는 것:

- 실제 DB 저장
- 실제 OP1 bootstrap handler
- 실제 API
- 실제 `@RequireCurrentOp` annotation/AOP
- 통합테스트

## ID 원칙

- 실제 식별자는 모두 `UUID`만 사용한다.
- `op-precinct-001-op1`, `inc-precinct-first-001` 같은 문자열은 사람이 읽기 위한 alias로만 둔다.
- 테스트 비교와 payload fixture의 `id`, `incidentId`, `opId`, `fromOpId`, `toOpId`는 UUID 타입을 사용한다.

## 구현할 계약

### OperationalPeriodQuery

문서 기준 이름에 맞춘다.

- `current(UUID incidentId)`
- `list(UUID incidentId)`

`currentOf`처럼 문서에 없는 이름은 사용하지 않는다.

### Current OP Fixture

OP1 current fixture는 아래 값을 고정한다.

- `status`: `ACTIVE`
- `sequenceNo`: `1`
- `reason`: `BOOTSTRAP`
- `version`: `1`
- `endedAt`: `null`

### OP1 Bootstrap Completion Fixture

S1-1이 사건을 `BOOTSTRAPPING -> OPEN`으로 전환할 때 소비할 수 있는 fixture다.

고정할 값:

- `type`: `OP_TRANSITIONED`
- `id`: OP1 UUID
- `opId`: OP1 UUID
- `fromOpId`: `null`
- `toOpId`: OP1 UUID
- `status`: `ACTIVE`
- `version`: `1`
- `sequenceNo`: `1`

`eventId`는 T05A에서 고정하지 않는다. 실제 publish 구현 단계에서 UUID not-null 검증으로 넘긴다.

## 테스트 계획

### Op1BootstrapContractTest

검증한다:

- OP1 bootstrap completion fixture가 문서의 `OP_TRANSITIONED` payload shape와 맞는지
- `OperationalPeriodQuery.current(incidentId)` mock이 OP1 current shape를 반환하는지
- `OperationalPeriodQuery.list(incidentId)` mock이 OP1 row 1개를 `sequenceNo ASC` shape로 반환하는지

### CurrentOpGuardContractTest

검증한다:

- current OP가 없으면 `op_required`, `409`
- 요청 `opId`가 서버 current OP와 다르면 `op_mismatch`, `409`

### CurrentOpConsumerContractTest

검증한다:

- S7, S3-1, S5 소비자가 같은 OP1 `opId`, `sequenceNo`, `version` fixture를 본다.
- S8 mock contract는 다른 Lane 소유 데이터를 만들지 않는다.

금지 owner write 목록:

- `offline_package_manifest`
- `offline_package_status`
- `search_session`
- `search_path`
- `path_segment`
- `marker`
- `photo`
- `notification_delivery`

## 완료 기준

- OP1/current OP fixture가 UUID 기반으로 고정된다.
- `op_required`, `op_mismatch` 실패 fixture가 문서의 error code/status와 일치한다.
- L1, S3-1, S5, S7이 mock으로 current OP를 소비할 수 있다.
- 통합테스트 없이 단위 테스트로 검증 가능하다.
