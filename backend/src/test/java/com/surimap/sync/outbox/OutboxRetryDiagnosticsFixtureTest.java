package com.surimap.sync.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
