package com.surimap.incident.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IncidentMapperTest extends PostGisIntegrationTestSupport {

  @Autowired private IncidentMapper mapper;

  @Test
  @DisplayName("SSE 순번은 사건별로 증가하며 업무 버전을 바꾸지 않는다")
  void increment_sse_sequence_preserves_business_version_and_other_incidents() {
    // given: 업무 버전이 7인 사건 두 개가 있고, 아직 SSE 순번을 배정하지 않았다.
    UUID incidentId = insertIncident();
    UUID otherIncidentId = insertIncident();
    assertThat(mapper.findLastSseSequence(incidentId)).isZero();

    // when: 한 사건의 SSE 순번을 두 번 증가시킨다.
    assertThat(mapper.incrementAndGetSseSequence(incidentId)).isEqualTo(1L);
    assertThat(mapper.incrementAndGetSseSequence(incidentId)).isEqualTo(2L);

    // then: 해당 사건의 순번만 저장되고 업무 버전과 다른 사건은 그대로다.
    assertThat(mapper.findLastSseSequence(incidentId)).isEqualTo(2L);
    assertThat(mapper.findLastSseSequence(otherIncidentId)).isZero();
    assertThat(mapper.findByIncidentId(incidentId).orElseThrow().getVersion()).isEqualTo(7L);
  }

  private UUID insertIncident() {
    UUID incidentId = UUID.randomUUID();
    Instant now = Instant.parse("2026-09-26T00:00:00Z");
    mapper.insertIncident(incidentId, UUID.randomUUID(), "SSE sequence test", "OPEN", now, 7L, now);
    return incidentId;
  }
}
