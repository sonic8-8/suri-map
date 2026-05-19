package com.surimap.opcomparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.surimap.maparea.support.PostGisIntegrationTestSupport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

@DisplayName("AI-CMP op_comparison_analysis MyBatis persistence")
class OpComparisonAnalysisMapperIntegrationTest extends PostGisIntegrationTestSupport {

  private static final UUID COMPARISON_ID = UUID.fromString("99000000-0000-0000-0000-000000000101");
  private static final UUID INCIDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0001");
  private static final UUID OP1_ID = UUID.fromString("88888888-8888-8888-8888-888888880001");
  private static final UUID OP2_ID = UUID.fromString("88888888-8888-8888-8888-888888880002");
  private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111110001");
  private static final Instant REQUESTED_AT = Instant.parse("2026-05-19T00:00:00Z");
  private static final String SOURCE_HASH = "a".repeat(64);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Autowired private OpComparisonAnalysisMapper mapper;

  @BeforeEach
  void cleanComparisonRows() {
    jdbcTemplate.execute("TRUNCATE TABLE op_comparison_analysis");
  }

  @Test
  @DisplayName(
      "request row keeps sorted OP ids, hashes, split statuses, and empty evidence payloads")
  void insertAndFindByRequestHash() throws Exception {
    assertThat(mapper.insert(generationRequest(COMPARISON_ID, "request-hash-ai-cmp-001")))
        .isEqualTo(1);

    OpComparisonAnalysisRecord row =
        mapper.findByRequestHash("request-hash-ai-cmp-001").orElseThrow();

    assertThat(row.id()).isEqualTo(COMPARISON_ID);
    assertThat(row.incidentId()).isEqualTo(INCIDENT_ID);
    assertJson(row.operationalPeriodIdsJson(), opIdsJson());
    assertThat(row.requestHash()).isEqualTo("request-hash-ai-cmp-001");
    assertThat(row.sourceDataHash()).isEqualTo(SOURCE_HASH);
    assertThat(row.status()).isEqualTo(OpComparisonAnalysisStatus.GENERATING);
    assertThat(row.narrativeStatus()).isEqualTo(OpComparisonNarrativeStatus.GENERATING);
    assertJson(row.metricsJson(), "[]");
    assertJson(row.diffFactsJson(), "[]");
    assertJson(row.commonRegionsGeojson(), "[]");
    assertThat(row.observationsJson()).isNull();
    assertThat(row.failureReason()).isNull();
    assertThat(row.requestedByAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(row.requestedAt()).isEqualTo(REQUESTED_AT);
    assertThat(row.generatedAt()).isNull();
    assertThat(row.version()).isEqualTo(1L);
  }

  @Test
  @DisplayName("request_hash is the durable idempotency key for the same comparison request")
  void requestHashIsUnique() {
    mapper.insert(generationRequest(COMPARISON_ID, "request-hash-ai-cmp-unique"));

    assertThatThrownBy(
            () ->
                mapper.insert(
                    generationRequest(
                        UUID.fromString("99000000-0000-0000-0000-000000000102"),
                        "request-hash-ai-cmp-unique")))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName(
      "deterministic result and AI narrative update independent statuses and increment version")
  void updatesResultAndNarrativeIndependently() throws Exception {
    mapper.insert(generationRequest(COMPARISON_ID, "request-hash-ai-cmp-update"));

    int deterministicUpdated =
        mapper.markDeterministicReady(
            COMPARISON_ID,
            metricsJson(),
            diffFactsJson(),
            commonRegionsJson(),
            OpComparisonNarrativeStatus.SKIPPED,
            null,
            REQUESTED_AT.plusSeconds(30),
            REQUESTED_AT.plusSeconds(30));

    assertThat(deterministicUpdated).isEqualTo(1);
    OpComparisonAnalysisRecord deterministic = mapper.findById(COMPARISON_ID).orElseThrow();
    assertThat(deterministic.status()).isEqualTo(OpComparisonAnalysisStatus.READY);
    assertThat(deterministic.narrativeStatus()).isEqualTo(OpComparisonNarrativeStatus.SKIPPED);
    assertJson(deterministic.metricsJson(), metricsJson());
    assertJson(deterministic.diffFactsJson(), diffFactsJson());
    assertJson(deterministic.commonRegionsGeojson(), commonRegionsJson());
    assertThat(deterministic.version()).isEqualTo(2L);

    int narrativeUpdated =
        mapper.updateNarrativeResult(
            COMPARISON_ID,
            OpComparisonAnalysisStatus.READY,
            OpComparisonNarrativeStatus.READY,
            observationsJson(),
            null,
            REQUESTED_AT.plusSeconds(60),
            REQUESTED_AT.plusSeconds(60));

    assertThat(narrativeUpdated).isEqualTo(1);
    OpComparisonAnalysisRecord completed = mapper.findById(COMPARISON_ID).orElseThrow();
    assertThat(completed.status()).isEqualTo(OpComparisonAnalysisStatus.READY);
    assertThat(completed.narrativeStatus()).isEqualTo(OpComparisonNarrativeStatus.READY);
    assertJson(completed.observationsJson(), observationsJson());
    assertThat(completed.generatedAt()).isEqualTo(REQUESTED_AT.plusSeconds(60));
    assertThat(completed.version()).isEqualTo(3L);
  }

  private static OpComparisonAnalysisRecord generationRequest(UUID id, String requestHash) {
    return new OpComparisonAnalysisRecord(
        id,
        INCIDENT_ID,
        opIdsJson(),
        requestHash,
        SOURCE_HASH,
        OpComparisonAnalysisStatus.GENERATING,
        "[]",
        "[]",
        "[]",
        OpComparisonNarrativeStatus.GENERATING,
        null,
        null,
        ACCOUNT_ID,
        REQUESTED_AT,
        null,
        1L,
        REQUESTED_AT,
        REQUESTED_AT);
  }

  private static String opIdsJson() {
    return "[\"" + OP1_ID + "\",\"" + OP2_ID + "\"]";
  }

  private static String metricsJson() {
    return """
        [{"operationalPeriodId":"%s","pathDistanceMeters":11342}]
        """
        .formatted(OP1_ID)
        .trim();
  }

  private static String diffFactsJson() {
    return """
        [{"factId":"distance-op1-op2","metricKey":"pathDistanceMeters","delta":3742}]
        """
        .trim();
  }

  private static String commonRegionsJson() {
    return """
        [{"regionId":"common-region-001","type":"FeatureCollection","features":[]}]
        """
        .trim();
  }

  private static String observationsJson() {
    return """
        [{"observation":"OP1의 전체 이동 거리는 11342m입니다.","evidence":[{"type":"metric","opId":"%s","key":"pathDistanceMeters","value":11342}]}]
        """
        .formatted(OP1_ID)
        .trim();
  }

  private static void assertJson(String actual, String expected) throws Exception {
    JsonNode actualNode = OBJECT_MAPPER.readTree(actual);
    JsonNode expectedNode = OBJECT_MAPPER.readTree(expected);
    assertThat(actualNode).isEqualTo(expectedNode);
  }
}
