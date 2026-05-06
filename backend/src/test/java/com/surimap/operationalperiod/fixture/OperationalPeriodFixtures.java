package com.surimap.operationalperiod.fixture;

import com.surimap.operationalperiod.query.CurrentOpResult;
import com.surimap.operationalperiod.query.OperationalPeriodRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * S8 operational_period 공통 fixture.
 *
 * <p>OP1 current OP, OP2 전환 후 상태, OperationalPeriodQuery row shape에서 공유한다.
 */
public final class OperationalPeriodFixtures {

  // TODO: S8 production enum이 생기면 OP 상태, OP reason, 이벤트 타입 문자열을 enum 또는 wireValue() 기준으로 교체한다.

  /** 문서에 적힌 사람이 읽기 쉬운 incident alias. 실제 DB ID는 UUID를 사용한다. */
  public static final String INCIDENT_ALIAS = "inc-precinct-first-001";

  /** SC-10/SC-11 시나리오에서 사용하는 고정 incident UUID. */
  public static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");

  /** bootstrap으로 자동 생성된 OP1 기대 상태. */
  public static final String CURRENT_OP_ALIAS = "op-precinct-001-op1";

  public static final UUID CURRENT_OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  public static final String CURRENT_OP_STATUS = "ACTIVE";
  public static final int CURRENT_OP_SEQUENCE_NO = 1;
  public static final Instant CURRENT_OP_STARTED_AT = Instant.parse("2026-04-28T00:00:00Z");
  public static final Instant CURRENT_OP_ENDED_AT = null;
  public static final String CURRENT_OP_REASON = "BOOTSTRAP";
  public static final long CURRENT_OP_VERSION = 1L;

  /** OP 전환 후 닫힌 이전 OP1 기대 상태. */
  public static final UUID PREVIOUS_OP_ID = CURRENT_OP_ID;

  public static final String PREVIOUS_OP_STATUS = "CLOSED";
  public static final int PREVIOUS_OP_SEQUENCE_NO = 1;
  public static final Instant PREVIOUS_OP_STARTED_AT = CURRENT_OP_STARTED_AT;
  public static final Instant PREVIOUS_OP_ENDED_AT = Instant.parse("2026-04-28T09:00:00Z");
  public static final String PREVIOUS_OP_REASON = CURRENT_OP_REASON;
  public static final long PREVIOUS_OP_VERSION = 2L;

  /** OP 전환으로 새로 활성화된 OP2 기대 상태. */
  public static final String NEW_OP_ALIAS = "op-precinct-001-op2";

  public static final UUID NEW_OP_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  public static final String NEW_OP_STATUS = "ACTIVE";
  public static final int NEW_OP_SEQUENCE_NO = 2;
  public static final Instant NEW_OP_STARTED_AT = PREVIOUS_OP_ENDED_AT;
  public static final Instant NEW_OP_ENDED_AT = null;
  public static final String NEW_OP_REASON = "SHIFT_CHANGE";
  public static final long NEW_OP_VERSION = 1L;

  /** operational_period.reason에 실제 저장될 수 있는 값. */
  public static final List<String> OP_REASON_PERSISTED =
      List.of("BOOTSTRAP", "SHIFT_CHANGE", "RE_SEARCH", "NEW_AREA", "OTHER");

  /** OP2 이상 수동 생성 시 허용되는 reason. BOOTSTRAP은 OP1 자동 생성 전용이다. */
  public static final List<String> OP_REASON_MANUAL_CREATE =
      List.of("SHIFT_CHANGE", "RE_SEARCH", "NEW_AREA", "OTHER");

  /** operational_period.status에 실제 저장될 수 있는 값. */
  public static final List<String> OP_STATUSES = List.of("ACTIVE", "CLOSED");

  private OperationalPeriodFixtures() {}

  /** bootstrap으로 자동 생성된 현재 OP1 기대값. */
  public static OpRow currentOp() {
    return new OpRow(
        CURRENT_OP_ID,
        INCIDENT_ID,
        CURRENT_OP_STATUS,
        CURRENT_OP_SEQUENCE_NO,
        CURRENT_OP_STARTED_AT,
        CURRENT_OP_ENDED_AT,
        CURRENT_OP_REASON,
        CURRENT_OP_VERSION);
  }

  /** bootstrap 이후 OperationalPeriodQuery.current가 반환해야 하는 현재 OP1 DTO. */
  public static CurrentOpResult currentOpResult() {
    return currentOpResult(INCIDENT_ID);
  }

  /** bootstrap 이후 지정 incident에 대해 OperationalPeriodQuery.current가 반환해야 하는 현재 OP1 DTO. */
  public static CurrentOpResult currentOpResult(UUID incidentId) {
    OpRow currentOp = currentOp();
    return new CurrentOpResult(
        currentOp.opId(),
        incidentId,
        currentOp.status(),
        currentOp.sequenceNo(),
        currentOp.startedAt(),
        currentOp.endedAt(),
        currentOp.reason(),
        currentOp.version());
  }

  /** OperationalPeriodQuery.list가 반환해야 하는 OP1 row fixture. */
  public static OperationalPeriodRow currentOpListRow() {
    return currentOpListRow(INCIDENT_ID);
  }

  /** 지정 incident에 대해 OperationalPeriodQuery.list가 반환해야 하는 OP1 row fixture. */
  public static OperationalPeriodRow currentOpListRow(UUID incidentId) {
    OpRow currentOp = currentOp();
    return new OperationalPeriodRow(
        currentOp.opId(),
        incidentId,
        currentOp.status(),
        currentOp.sequenceNo(),
        currentOp.startedAt(),
        currentOp.endedAt(),
        currentOp.reason(),
        currentOp.version());
  }

  /** SC-10 전환 후 CLOSED/version=2가 된 이전 OP1 기대값. */
  public static OpRow previousOpAfterTransition() {
    return new OpRow(
        PREVIOUS_OP_ID,
        INCIDENT_ID,
        PREVIOUS_OP_STATUS,
        PREVIOUS_OP_SEQUENCE_NO,
        PREVIOUS_OP_STARTED_AT,
        PREVIOUS_OP_ENDED_AT,
        PREVIOUS_OP_REASON,
        PREVIOUS_OP_VERSION);
  }

  /** SC-10 전환 후 ACTIVE/version=1로 생성된 새 OP2 기대값. */
  public static OpRow newOpAfterTransition() {
    return new OpRow(
        NEW_OP_ID,
        INCIDENT_ID,
        NEW_OP_STATUS,
        NEW_OP_SEQUENCE_NO,
        NEW_OP_STARTED_AT,
        NEW_OP_ENDED_AT,
        NEW_OP_REASON,
        NEW_OP_VERSION);
  }

  /** OP 조회/전환 결과 비교 모델. */
  public record OpRow(
      UUID opId,
      UUID incidentId,
      String status,
      int sequenceNo,
      Instant startedAt,
      Instant endedAt,
      String reason,
      long version) {}

  /** OP_TRANSITIONED PublishRequest payload 비교 모델 (S8.json events_published[].payload_schema). */
  public record OpTransitionedEvent(
      UUID eventId,
      String type,
      UUID id,
      UUID incidentId,
      UUID opId,
      String status,
      long version,
      int sequenceNo,
      UUID fromOpId,
      UUID toOpId) {}
}
