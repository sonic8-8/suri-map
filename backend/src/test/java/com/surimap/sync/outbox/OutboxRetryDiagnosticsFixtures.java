package com.surimap.sync.outbox;

import java.time.Instant;
import java.util.List;

final class OutboxRetryDiagnosticsFixtures {

  static final String API_PATH = "/api/sync/outbox/requeue";
  static final String SOURCE_SPEC_PATH = "POST /sync/outbox/requeue";
  static final String CANONICAL_API_PATH = "POST /api/sync/outbox/requeue";
  static final String POLICE_PHONE_HEADER = "X-PolicePhone-Id";
  static final String HARNESS_POLICE_PHONE_ID = "dev-precinct-car-01";
  static final String INCIDENT_ID = "inc-precinct-first-001";
  static final int STALE_CLOCK_SYNC_AFTER_MS = 300_000;

  static final List<String> REQUIRED_REQUEST_FIELDS =
      List.of(
          "operationId",
          "incidentId",
          "reason",
          "clientTs",
          "clockOffsetMs",
          "clockSyncedAt");

  static final List<String> REQUEUE_REASONS =
      List.of(
          "NETWORK_RESTORED",
          "USER_RETRY",
          "WORKER_BACKOFF_DUE",
          "PARTIAL_FAILURE",
          "RESPONSE_CACHE_RECOVERY");

  static final List<String> RETRYABLE_ERRORS =
      List.of(
          "network_unavailable",
          "low_connectivity_timeout",
          "http_408",
          "http_429",
          "http_500",
          "http_502",
          "http_503",
          "http_504",
          "clock_skew_exceeded_after_resync");

  static final List<String> FINAL_ERRORS =
      List.of(
          "invalid_payload",
          "request_body_hash_mismatch",
          "idempotency_mismatch",
          "police_phone_not_registered",
          "police_phone_not_assigned",
          "channel_not_allowed",
          "role_denied",
          "write_conflict",
          "operation_dependency_missing",
          "post_close_requeue_rejected");

  static final List<String> S6_STATUSES =
      List.of("PENDING", "SENDING", "ACKED", "FAILED_RETRYABLE", "FAILED_FINAL", "PURGED");

  static final List<String> HARNESS_STATUSES =
      List.of("PENDING_LOCAL", "PENDING_SEND", "SENDING", "SYNCED", "FAILED", "PURGED");

  static final List<StatusMappingFixture> STATUS_MAPPINGS =
      List.of(
          new StatusMappingFixture("PENDING_LOCAL", "PENDING"),
          new StatusMappingFixture("PENDING_SEND", "PENDING"),
          new StatusMappingFixture("SENDING", "SENDING"),
          new StatusMappingFixture("SYNCED", "ACKED"),
          new StatusMappingFixture("FAILED", "FAILED_RETRYABLE|FAILED_FINAL"),
          new StatusMappingFixture("PURGED", "PURGED"));

  static final List<FailureCategoryFixture> FAILURE_CATEGORY_ROWS =
      List.of(
          new FailureCategoryFixture(
              "op-fail-clock-001", "clock_skew_exceeded", "CLOCK_RESYNC_REQUIRED", true),
          new FailureCategoryFixture(
              "op-fail-idem-001", "idempotency_mismatch", "NON_RETRYABLE_CONFLICT", false),
          new FailureCategoryFixture(
              "op-fail-PolicePhone-001",
              "police_phone_not_assigned",
              "DEVICE_ACCESS_REQUIRED",
              false),
          new FailureCategoryFixture(
              "op-fail-closed-001",
              "post_close_requeue_rejected",
              "CLOSED_NO_RETRY",
              false),
          new FailureCategoryFixture(
              "op-fail-network-001", "low_connectivity_timeout", "RETRYABLE_NETWORK", true));

  static final RequeueRequestFixture NETWORK_RESTORED_REQUEUE =
      new RequeueRequestFixture(
          "op-outbox-path-001",
          INCIDENT_ID,
          "NETWORK_RESTORED",
          Instant.parse("2026-04-28T00:00:45Z"),
          0,
          Instant.parse("2026-04-28T00:00:35Z"),
          1);

  static final RequeueRequestFixture PARTIAL_FAILURE_REQUEUE =
      new RequeueRequestFixture(
          "op-outbox-package-001",
          INCIDENT_ID,
          "PARTIAL_FAILURE",
          Instant.parse("2026-04-28T00:00:45Z"),
          0,
          Instant.parse("2026-04-28T00:00:35Z"),
          2);

  private OutboxRetryDiagnosticsFixtures() {}

  record StatusMappingFixture(String harnessStatus, String mapsTo) {}

  record FailureCategoryFixture(
      String operationId, String lastError, String userSafeFailureCategory, boolean retryable) {}

  record RequeueRequestFixture(
      String operationId,
      String incidentId,
      String reason,
      Instant clientTs,
      int clockOffsetMs,
      Instant clockSyncedAt,
      int attemptCount) {

    String json() {
      return """
          {
            "operationId": "%s",
            "incidentId": "%s",
            "reason": "%s",
            "clientTs": "%s",
            "clockOffsetMs": %d,
            "clockSyncedAt": "%s",
            "attemptCount": %d
          }
          """
          .formatted(
              operationId, incidentId, reason, clientTs, clockOffsetMs, clockSyncedAt, attemptCount);
    }
  }
}
