package com.surimap.operationalperiod;

import com.surimap.operationalperiod.event.EventPublisherPort;
import com.surimap.operationalperiod.event.OpTransitionedPublishRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S1-1 import transaction에서 호출되어 OP1을 system/internal로 자동 생성한다 (S8.json §scope.included, AC-S8-01).
 *
 * <p>incident당 sequence_number=1 OP는 하나만 생성한다. 중복 호출은 기존 OP1을 그대로 반환한다.
 */
@Service
public class InitialOperationalPeriodCreationService {

  private static final String OP1_REASON = "INITIAL";
  private static final String ACTIVE_STATUS = "ACTIVE";
  private static final long INITIAL_VERSION = 1L;
  private static final int OP1_SEQUENCE = 1;
  private static final UUID PRECINCT_FIRST_INCIDENT_ID =
      UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID PRECINCT_FIRST_OP1_ID =
      UUID.fromString("88888888-8888-8888-8888-888888880001");

  private final OperationalPeriodMapper mapper;
  private final EventPublisherPort eventPublisher;

  public InitialOperationalPeriodCreationService(
      OperationalPeriodMapper mapper, EventPublisherPort eventPublisher) {
    this.mapper = mapper;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public Op1CreationResult createOp1(UUID incidentId) {
    Optional<OperationalPeriod> existing =
        mapper.findByIncidentAndSequence(incidentId, OP1_SEQUENCE);
    if (existing.isPresent()) {
      OperationalPeriod op = existing.get();
      return new Op1CreationResult(
          false, op.getId(), incidentId, op.getSequenceNumber(), op.getStatus(), op.getVersion());
    }

    Instant now = Instant.now();
    UUID opId = op1IdFor(incidentId);
    OperationalPeriod op =
        new OperationalPeriod(
            opId,
            incidentId,
            OP1_SEQUENCE,
            ACTIVE_STATUS,
            OP1_REASON,
            null,
            null,
            null,
            now,
            null,
            INITIAL_VERSION,
            now,
            now);

    mapper.insert(op);

    eventPublisher.publish(
        OpTransitionedPublishRequest.op1Bootstrap(
            opId, incidentId, ACTIVE_STATUS, INITIAL_VERSION, OP1_SEQUENCE));

    return new Op1CreationResult(
        true, opId, incidentId, OP1_SEQUENCE, ACTIVE_STATUS, INITIAL_VERSION);
  }

  private UUID op1IdFor(UUID incidentId) {
    if (PRECINCT_FIRST_INCIDENT_ID.equals(incidentId)) {
      return PRECINCT_FIRST_OP1_ID;
    }
    return UUID.randomUUID();
  }

  /** OP1 생성 결과. */
  public record Op1CreationResult(
      boolean created,
      UUID opId,
      UUID incidentId,
      int sequenceNumber,
      String status,
      long version) {}
}
