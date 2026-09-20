package com.surimap.sync.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutboxRetryDiagnosticsFixtureTest {

  private final JsonNode root = CommonFixtureJson.root();

  @Test
  void requeueContractUsesCanonicalApiSpecPathAndRequiredFields() {
    assertThat(OutboxRetryDiagnosticsFixtures.API_PATH).isEqualTo("/api/sync/outbox/requeue");
    assertThat(OutboxRetryDiagnosticsFixtures.SOURCE_SPEC_PATH)
        .isEqualTo("POST /sync/outbox/requeue");
    assertThat(OutboxRetryDiagnosticsFixtures.CANONICAL_API_PATH)
        .isEqualTo("POST /api/sync/outbox/requeue");

    assertThat(OutboxRetryDiagnosticsFixtures.REQUIRED_REQUEST_FIELDS)
        .containsExactly(
            "operationId",
            "incidentId",
            "reason",
            "clientTs",
            "clockOffsetMs",
            "clockSyncedAt");
    assertThat(OutboxRetryDiagnosticsFixtures.REQUEUE_REASONS)
        .containsExactly(
            "NETWORK_RESTORED",
            "USER_RETRY",
            "WORKER_BACKOFF_DUE",
            "PARTIAL_FAILURE",
            "RESPONSE_CACHE_RECOVERY");
  }

  @Test
  void commonFixtureStatusMappingMatchesS6OutboxStates() {
    var sharedRules = commonFixtureOutboxSharedRules();

    assertThat(sharedRules.get("s6Statuses"))
        .extracting(JsonNode::asText)
        .containsExactlyElementsOf(OutboxRetryDiagnosticsFixtures.S6_STATUSES);
    assertThat(sharedRules.get("harnessStatuses"))
        .extracting(JsonNode::asText)
        .containsExactlyElementsOf(OutboxRetryDiagnosticsFixtures.HARNESS_STATUSES);

    var statusMapping = sharedRules.get("statusMapping");
    assertThat(OutboxRetryDiagnosticsFixtures.STATUS_MAPPINGS)
        .allSatisfy(
            mapping ->
                assertThat(statusMapping.get(mapping.harnessStatus()).asText())
                    .isEqualTo(mapping.mapsTo()));
  }

  @Test
  void commonFixtureClockRulesKeepStaleClockRowsOutOfSending() {
    var clockRules = commonFixtureOutboxSharedRules().get("clockRules");

    assertThat(clockRules.get("maxAllowedSkewMs").asInt()).isEqualTo(30_000);
    assertThat(clockRules.get("staleClockSyncAfterMs").asInt())
        .isEqualTo(OutboxRetryDiagnosticsFixtures.STALE_CLOCK_SYNC_AFTER_MS);
    assertThat(clockRules.get("enqueueRule").asText()).contains("/api/sync/clock", "SENDING");
  }

  @Test
  void commonFixtureFailureRowsMatchUserSafeCategoriesAndRetryability() {
    var failureRows =
        CommonFixtureJson.required(
            CommonFixtureJson.required(commonFixtureRoot(), "terminalStateRules"),
            "failureCategoryRows");

    assertThat(failureRows).hasSize(OutboxRetryDiagnosticsFixtures.FAILURE_CATEGORY_ROWS.size());
    assertThat(OutboxRetryDiagnosticsFixtures.FAILURE_CATEGORY_ROWS)
        .allSatisfy(
            expected -> {
              var actual = findFailureRow(failureRows, expected.operationId());

              assertThat(actual.get("lastError").asText()).isEqualTo(expected.lastError());
              assertThat(actual.get("userSafeFailureCategory").asText())
                  .isEqualTo(expected.userSafeFailureCategory());
              assertThat(actual.get("retryable").asBoolean()).isEqualTo(expected.retryable());
            });
  }

  @Test
  void retryableAndTerminalErrorsStaySeparatedBeforeDiagnosticImplementation() {
    assertThat(OutboxRetryDiagnosticsFixtures.RETRYABLE_ERRORS)
        .contains(
            "network_unavailable",
            "low_connectivity_timeout",
            "http_503",
            "clock_skew_exceeded_after_resync")
        .doesNotContain("idempotency_mismatch", "post_close_requeue_rejected");
    assertThat(OutboxRetryDiagnosticsFixtures.FINAL_ERRORS)
        .contains(
            "idempotency_mismatch",
            "police_phone_not_assigned",
            "write_conflict",
            "post_close_requeue_rejected")
        .doesNotContain("low_connectivity_timeout", "http_503");
  }

  @Test
  void requeueDiagnosticFixturesUseCommonOutboxOperations() {
    var pathReplay =
        CommonFixtureJson.required(
            CommonFixtureJson.required(commonFixtureOutboxReplay(), "sc05PathReplay"),
            "operationId");
    var packageReplay =
        CommonFixtureJson.required(
            CommonFixtureJson.required(commonFixtureOutboxReplay(), "sc09PackageReplay"),
            "operationId");

    assertThat(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.operationId())
        .isEqualTo(pathReplay.asText());
    assertThat(OutboxRetryDiagnosticsFixtures.NETWORK_RESTORED_REQUEUE.reason())
        .isEqualTo("NETWORK_RESTORED");
    assertThat(OutboxRetryDiagnosticsFixtures.PARTIAL_FAILURE_REQUEUE.operationId())
        .isEqualTo(packageReplay.asText());
    assertThat(OutboxRetryDiagnosticsFixtures.PARTIAL_FAILURE_REQUEUE.reason())
        .isEqualTo("PARTIAL_FAILURE");
  }

  @Test
  @DisplayName("공용 재전송 fixture는 작업 식별자와 경로·패키지 유형을 유지한다")
  void replay_fixtures_keep_operation_ids_and_dependency_groups() {
    // given: 공용 재전송 fixture를 준비한다.
    JsonNode replay = commonFixtureOutboxReplay();

    // when: 경로·마커·사진·패키지의 재전송 입력을 읽는다.
    JsonNode path = CommonFixtureJson.required(replay, "sc05PathReplay");
    JsonNode markerPhoto = CommonFixtureJson.required(replay, "sc06MarkerPhotoReplay");
    JsonNode packageReplay = CommonFixtureJson.required(replay, "sc09PackageReplay");

    // then: 기존 작업 식별자와 의존 그룹·대상 유형이 유지된다.
    assertThat(CommonFixtureJson.required(path, "dependencyGroup").asText()).isEqualTo("PATH");
    assertThat(CommonFixtureJson.required(markerPhoto, "markerOperationAlias").asText())
        .isEqualTo("op-outbox-marker-001");
    assertThat(CommonFixtureJson.required(markerPhoto, "markerOperationId").asText())
        .isEqualTo("66666666-0000-4000-8000-000000000601");
    assertThat(CommonFixtureJson.required(markerPhoto, "photoOperationAlias").asText())
        .isEqualTo("op-outbox-photo-001");
    assertThat(CommonFixtureJson.required(markerPhoto, "photoOperationId").asText())
        .isEqualTo("66666666-0000-4000-8000-000000000602");
    assertThat(CommonFixtureJson.required(packageReplay, "dependencyGroup").asText())
        .isEqualTo("PACKAGE_INSTALLATION");
    assertThat(
            CommonFixtureJson.required(
                    CommonFixtureJson.required(packageReplay, "writeOperation"), "entityType")
                .asText())
        .isEqualTo("offline_package_installation");
  }

  @Test
  @DisplayName("공용 fixture의 오프라인 쓰기 실패는 로컬·전송 대기 상태로 남는다")
  void offline_write_fixture_keeps_local_and_send_pending_states() {
    // given: 공용 네트워크 fixture를 준비한다.
    JsonNode networkScripts = CommonFixtureJson.required(commonFixtureRoot(), "networkScripts");

    // when: 오프라인 쓰기의 기대 상태를 읽는다.
    JsonNode expectations = CommonFixtureJson.required(networkScripts, "domainWriteExpectations");

    // then: 로컬 기록과 전송 요청이 각각 대기 상태를 유지한다.
    assertThat(CommonFixtureJson.required(expectations, "offlineFailureLocalStatus").asText())
        .isEqualTo("PENDING_LOCAL");
    assertThat(CommonFixtureJson.required(expectations, "offlineFailureOutboxStatus").asText())
        .isEqualTo("PENDING_SEND");
  }

  private JsonNode commonFixtureRoot() {
    return CommonFixtureJson.required(root, "confirmed");
  }

  private JsonNode commonFixtureOutboxSharedRules() {
    return CommonFixtureJson.required(commonFixtureRoot(), "outboxSharedRules");
  }

  private JsonNode commonFixtureOutboxReplay() {
    return CommonFixtureJson.required(commonFixtureRoot(), "outboxReplay");
  }

  private static JsonNode findFailureRow(JsonNode rows, String operationId) {
    for (JsonNode row : rows) {
      if (operationId.equals(row.get("operationId").asText())) {
        return row;
      }
    }
    throw new AssertionError("Missing failure category row: " + operationId);
  }
}
