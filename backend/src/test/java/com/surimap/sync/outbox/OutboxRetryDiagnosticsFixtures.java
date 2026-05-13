package com.surimap.sync.outbox;

import java.time.Instant;
import java.util.List;

final class OutboxRetryDiagnosticsFixtures {

  static final String API_PATH = "/api/sync/outbox/requeue";
  static final String SOURCE_SPEC_PATH = "POST /sync/outbox/requeue";
  static final String CANONICAL_API_PATH = "POST /api/sync/outbox/requeue";
  static final String POLICE_PHONE_HEADER = "X-PolicePhone-Id";
  static final String ASSIGNED_AUTH_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000101";
  static final String UNREGISTERED_AUTH_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000201";
  static final String UNASSIGNED_AUTH_POLICE_PHONE_ID = "00000000-0000-0000-0000-000000000301";
  static final String INCIDENT_ID = "b5fdbad6-57ce-4d64-a6f2-82b1d3e16699";
  static final String CLOSED_INCIDENT_ID = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0012";
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
              "66666666-0000-4000-8000-000000001701",
              "clock_skew_exceeded",
              "CLOCK_RESYNC_REQUIRED",
              true),
          new FailureCategoryFixture(
              "66666666-0000-4000-8000-000000001702",
              "idempotency_mismatch",
              "NON_RETRYABLE_CONFLICT",
              false),
          new FailureCategoryFixture(
              "66666666-0000-4000-8000-000000001703",
              "police_phone_not_assigned",
              "POLICE_PHONE_ACCESS_REQUIRED",
              false),
          new FailureCategoryFixture(
              "66666666-0000-4000-8000-000000001704",
              "post_close_requeue_rejected",
              "CLOSED_NO_RETRY",
              false),
          new FailureCategoryFixture(
              "66666666-0000-4000-8000-000000001705",
              "low_connectivity_timeout",
              "RETRYABLE_NETWORK",
              true));

  static final RequeueRequestFixture NETWORK_RESTORED_REQUEUE =
      new RequeueRequestFixture(
          "66666666-0000-4000-8000-000000000501",
          INCIDENT_ID,
          "NETWORK_RESTORED",
          Instant.parse("2026-04-28T00:00:45Z"),
          0,
          Instant.parse("2026-04-28T00:00:35Z"),
          1);

  static final RequeueRequestFixture PARTIAL_FAILURE_REQUEUE =
      new RequeueRequestFixture(
          "66666666-0000-4000-8000-000000000901",
          INCIDENT_ID,
          "PARTIAL_FAILURE",
          Instant.parse("2026-04-28T00:00:45Z"),
          0,
          Instant.parse("2026-04-28T00:00:35Z"),
          2);

  static final RequeueRequestFixture STALE_CLOCK_REQUEUE =
      new RequeueRequestFixture(
          "66666666-0000-4000-8000-000000001701",
          INCIDENT_ID,
          "USER_RETRY",
          Instant.parse("2026-04-28T00:10:45Z"),
          0,
          Instant.parse("2026-04-28T00:00:35Z"),
          3);

  static final RequeueRequestFixture CLOSED_INCIDENT_REQUEUE =
      new RequeueRequestFixture(
          "66666666-0000-4000-8000-000000001704",
          CLOSED_INCIDENT_ID,
          "USER_RETRY",
          Instant.parse("2026-04-28T12:00:45Z"),
          0,
          Instant.parse("2026-04-28T12:00:35Z"),
          1);

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
