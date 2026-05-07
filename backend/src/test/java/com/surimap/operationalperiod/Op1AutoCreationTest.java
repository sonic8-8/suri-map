package com.surimap.operationalperiod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.surimap.operationalperiod.event.OpTransitionedPublishRequest;
import com.surimap.operationalperiod.fixture.OperationalPeriodFixtures;
import com.surimap.operationalperiod.testdouble.InMemoryEventPublisher;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** L3-T05B OP1 자동 생성 서비스 단위 테스트. */
@DisplayName("L3-T05B Op1 자동 생성 서비스")
class Op1AutoCreationTest {

  private final OperationalPeriodMapper mapper = mock(OperationalPeriodMapper.class);
  private final InMemoryEventPublisher eventPublisher = new InMemoryEventPublisher();
  private final InitialOperationalPeriodCreationService service =
      new InitialOperationalPeriodCreationService(mapper, eventPublisher);

  @Test
  @DisplayName("OP1이 없으면 DB에 적재하고 OP_TRANSITIONED(fromOpId=null) 이벤트를 발행한다")
  void op1_없으면_생성하고_이벤트_발행한다() {
    when(mapper.findByIncidentAndSequence(OperationalPeriodFixtures.INCIDENT_ID, 1))
        .thenReturn(Optional.empty());

    InitialOperationalPeriodCreationService.Op1CreationResult result =
        service.createOp1(OperationalPeriodFixtures.INCIDENT_ID);

    verify(mapper).insert(any(OperationalPeriod.class));
    assertThat(result.created()).isTrue();
    assertThat(result.opId()).isNotNull();
    assertThat(result.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(result.sequenceNumber()).isEqualTo(1);
    assertThat(result.status()).isEqualTo("ACTIVE");
    assertThat(result.version()).isEqualTo(1L);

    assertThat(eventPublisher.captured()).hasSize(1);
    OpTransitionedPublishRequest req = eventPublisher.captured().get(0);
    assertThat(req.type()).isEqualTo("OP_TRANSITIONED");
    assertThat(req.fromOpId()).isNull();
    assertThat(req.toOpId()).isEqualTo(result.opId());
    assertThat(req.opId()).isEqualTo(result.opId());
    assertThat(req.incidentId()).isEqualTo(OperationalPeriodFixtures.INCIDENT_ID);
    assertThat(req.status()).isEqualTo("ACTIVE");
    assertThat(req.version()).isEqualTo(1L);
    assertThat(req.sequenceNumber()).isEqualTo(1);
  }

  @Test
  @DisplayName("OP1이 이미 존재하면 중복 insert 없이 기존 OP1을 반환한다")
  void op1_이미_존재하면_중복_생성하지_않는다() {
    OperationalPeriod existing =
        new OperationalPeriod(
            OperationalPeriodFixtures.CURRENT_OP_ID,
            OperationalPeriodFixtures.INCIDENT_ID,
            1,
            "ACTIVE",
            "INITIAL",
            null,
            null,
            null,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT,
            null,
            1L,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT,
            OperationalPeriodFixtures.CURRENT_OP_STARTED_AT);

    when(mapper.findByIncidentAndSequence(OperationalPeriodFixtures.INCIDENT_ID, 1))
        .thenReturn(Optional.of(existing));

    InitialOperationalPeriodCreationService.Op1CreationResult result =
        service.createOp1(OperationalPeriodFixtures.INCIDENT_ID);

    verify(mapper, never()).insert(any());
    assertThat(result.created()).isFalse();
    assertThat(result.opId()).isEqualTo(OperationalPeriodFixtures.CURRENT_OP_ID);
    assertThat(eventPublisher.captured()).isEmpty();
  }

  @Test
  @DisplayName("OP_TRANSITIONED 이벤트는 fromOpId=null, toOpId=OP1 ID를 포함한다")
  void op_transitioned_이벤트는_fromOpId가_null이다() {
    when(mapper.findByIncidentAndSequence(OperationalPeriodFixtures.INCIDENT_ID, 1))
        .thenReturn(Optional.empty());

    InitialOperationalPeriodCreationService.Op1CreationResult result =
        service.createOp1(OperationalPeriodFixtures.INCIDENT_ID);

    OpTransitionedPublishRequest req = eventPublisher.captured().get(0);
    assertThat(req.fromOpId()).isNull();
    assertThat(req.toOpId()).isEqualTo(result.opId());
  }
}
