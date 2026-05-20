package com.surimap.opcomparison;

import static org.assertj.core.api.Assertions.assertThat;

import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("AI-CMP OP comparison region fact PostGIS mapper")
class OpComparisonRegionFactMapperIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  private static final UUID DUTY_SHIFT1_ID =
      UUID.fromString("66000000-0000-0000-0000-000000000001");
  private static final UUID DUTY_SHIFT2_ID =
      UUID.fromString("66000000-0000-0000-0000-000000000002");
  private static final UUID ACCOUNT1_ID = UUID.fromString("11111111-1111-1111-1111-111111110002");
  private static final UUID ACCOUNT2_ID = UUID.fromString("11111111-1111-1111-1111-111111110005");
  private static final UUID PATH1_ID = UUID.fromString("77000000-0000-0000-0000-000000000001");
  private static final UUID PATH2_ID = UUID.fromString("77000000-0000-0000-0000-000000000002");
  private static final Instant BASE_TIME = Instant.parse("2026-05-18T00:00:00Z");

  @Autowired private OpComparisonRegionFactMapper mapper;

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
    seedIncidentAndOps();
    seedPaths();
  }

  @Test
  @DisplayName("common and per-OP different regions include geometry, area, and per-OP occupancy")
  void findsCommonAndDifferentRegionFacts() {
    List<OpComparisonRegionFact> facts =
        mapper.findRegionFacts(List.of(OP1_ID, OP2_ID), BigDecimal.valueOf(15), BigDecimal.ONE);

    assertThat(facts).hasSize(3);
    OpComparisonRegionFact common =
        facts.stream()
            .filter(fact -> fact.type() == OpComparisonRegionFactType.COMMON_REGION)
            .findFirst()
            .orElseThrow();
    assertThat(common.factId()).startsWith("common-region-");
    assertThat(common.operationalPeriodIds()).containsExactly(OP1_ID, OP2_ID);
    assertThat(common.geometryGeojson()).contains("\"type\"");
    assertThat(common.areaSquareMeters()).isGreaterThan(BigDecimal.ONE);
    assertThat(common.occupancies())
        .extracting(OpComparisonRegionOccupancy::operationalPeriodId)
        .containsExactly(OP1_ID, OP2_ID);
    assertThat(common.occupancies())
        .extracting(OpComparisonRegionOccupancy::durationSeconds)
        .containsExactly(600L, 600L);

    List<OpComparisonRegionFact> different =
        facts.stream()
            .filter(fact -> fact.type() == OpComparisonRegionFactType.DIFFERENT_REGION)
            .sorted(Comparator.comparing(fact -> fact.operationalPeriodIds().get(0)))
            .toList();
    assertThat(different).hasSize(2);
    assertThat(different.get(0).operationalPeriodIds()).containsExactly(OP1_ID);
    assertThat(different.get(1).operationalPeriodIds()).containsExactly(OP2_ID);
    assertThat(different)
        .allSatisfy(
            fact -> {
              assertThat(fact.geometryGeojson()).contains("\"type\"");
              assertThat(fact.areaSquareMeters()).isGreaterThan(BigDecimal.ONE);
              assertThat(fact.occupancies()).hasSize(1);
              assertThat(fact.occupancies().get(0).durationSeconds()).isEqualTo(600L);
            });
  }

  @Test
  @DisplayName("minimum area threshold filters small overlaps before AI evidence generation")
  void appliesMinimumAreaThreshold() {
    List<OpComparisonRegionFact> facts =
        mapper.findRegionFacts(
            List.of(OP1_ID, OP2_ID), BigDecimal.valueOf(15), BigDecimal.valueOf(1_000_000));

    assertThat(facts).isEmpty();
  }

  private void seedIncidentAndOps() {
    jdbcTemplate.update(
        """
        INSERT INTO incident (
          id, source_incident_id, title, status, opened_at, version, created_at, updated_at
        )
        VALUES (?, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0099'::uuid, 'AI-CMP-3 fixture', 'OPEN', ?, 1, ?, ?)
        """,
        INCIDENT_ID,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
    insertOperationalPeriod(OP1_ID, 1);
    insertOperationalPeriod(OP2_ID, 2);
    insertDutyShift(DUTY_SHIFT1_ID, OP1_ID);
    insertDutyShift(DUTY_SHIFT2_ID, OP2_ID);
  }

  private void seedPaths() {
    insertPath(
        PATH1_ID,
        DUTY_SHIFT1_ID,
        ACCOUNT1_ID,
        "LINESTRING(126.9000 35.1600,126.9040 35.1600)");
    insertPath(
        PATH2_ID,
        DUTY_SHIFT2_ID,
        ACCOUNT2_ID,
        "LINESTRING(126.9000 35.1600,126.9040 35.1620)");
    insertSegment(
        "77000000-0000-0000-0000-000000000011",
        PATH1_ID,
        "LINESTRING(126.9000 35.1600,126.9010 35.1600)",
        BASE_TIME,
        BASE_TIME.plusSeconds(600));
    insertSegment(
        "77000000-0000-0000-0000-000000000012",
        PATH1_ID,
        "LINESTRING(126.9030 35.1600,126.9040 35.1600)",
        BASE_TIME.plusSeconds(900),
        BASE_TIME.plusSeconds(1500));
    insertSegment(
        "77000000-0000-0000-0000-000000000021",
        PATH2_ID,
        "LINESTRING(126.9000 35.1600,126.9010 35.1600)",
        BASE_TIME.plusSeconds(300),
        BASE_TIME.plusSeconds(900));
    insertSegment(
        "77000000-0000-0000-0000-000000000022",
        PATH2_ID,
        "LINESTRING(126.9030 35.1620,126.9040 35.1620)",
        BASE_TIME.plusSeconds(1200),
        BASE_TIME.plusSeconds(1800));
  }

  private void insertOperationalPeriod(UUID opId, int sequenceNumber) {
    jdbcTemplate.update(
        """
        INSERT INTO operational_period (
          id, incident_id, sequence_number, status, reason, started_at, ended_at,
          version, created_at, updated_at
        )
        VALUES (?, ?, ?, 'ENDED', 'SCHEDULED_ROTATION', ?, ?, 1, ?, ?)
        """,
        opId,
        INCIDENT_ID,
        sequenceNumber,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(3600)),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
  }

  private void insertDutyShift(UUID dutyShiftId, UUID opId) {
    jdbcTemplate.update(
        """
        INSERT INTO duty_shift (
          id, operational_period_id, incident_assignment_id, police_phone_id, status,
          started_at, ended_at, version, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, 'ENDED', ?, ?, 1, ?, ?)
        """,
        dutyShiftId,
        opId,
        UUID.randomUUID(),
        UUID.randomUUID(),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(3600)),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
  }

  private void insertPath(UUID pathId, UUID dutyShiftId, UUID accountId, String wkt) {
    jdbcTemplate.update(
        """
        INSERT INTO search_path (
          id, duty_shift_id, account_id, status, started_at, ended_at, geometry, version, created_at, updated_at
        )
        VALUES (?, ?, ?, 'ENDED', ?, ?, ST_GeomFromText(?, 4326), 1, ?, ?)
        """,
        pathId,
        dutyShiftId,
        accountId,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME.plusSeconds(1800)),
        wkt,
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
  }

  private void insertSegment(
      String segmentId, UUID pathId, String wkt, Instant startedAt, Instant endedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO search_path_segment (
          id, search_path_id, movement_type, movement_type_source, geometry,
          started_at, ended_at, version, created_at, updated_at
        )
        VALUES (?::uuid, ?, 'FOOT', 'AUTO', ST_GeomFromText(?, 4326), ?, ?, 1, ?, ?)
        """,
        segmentId,
        pathId,
        wkt,
        timestamp(startedAt),
        timestamp(endedAt),
        timestamp(BASE_TIME),
        timestamp(BASE_TIME));
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }
}
