package com.surimap.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("S8 search_history_summary MyBatis evidence mapper")
class SearchHistorySummaryMapperIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0111");
  private static final UUID OP_ID = UUID.fromString("88888888-8888-8888-8888-888888881111");
  private static final Instant BASE_TIME = Instant.parse("2026-05-20T00:00:00Z");
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private SearchHistorySummaryMapper mapper;

  @BeforeEach
  void cleanAndSeed() {
    jdbcTemplate.execute(
        """
        TRUNCATE TABLE
          search_path_lifecycle_event,
          search_path_excluded_point,
          search_path_segment,
          search_path,
          duty_shift,
          handover_memo,
          search_history_summary,
          operational_period,
          incident
        CASCADE
        """);
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa1111'::uuid, 'summary evidence fixture', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_at, ended_at,
          version, created_at, updated_at
        )
        VALUES (?, ?, 1, 'ENDED', 'OTHER', ?, ?, 1, ?, ?)
        """,
        OP_ID,
        INCIDENT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(3600)),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
  }

  @Test
  @DisplayName("OP scope evidence supports null dutyShiftId after OP transition")
  void sourceEvidenceForOpScopeSupportsNullDutyShiftId() throws Exception {
    String evidence = mapper.sourceEvidenceForScope(OP_ID, null);

    JsonNode root = OBJECT_MAPPER.readTree(evidence);
    assertThat(root.path("briefingKind").asText()).isEqualTo("OP_SUMMARY");
    assertThat(root.path("sourceRefs").path("opId").asText()).isEqualTo(OP_ID.toString());
    assertThat(root.path("sourceRefs").path("dutyShiftId").isNull()).isTrue();
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.from(instant);
  }
}
