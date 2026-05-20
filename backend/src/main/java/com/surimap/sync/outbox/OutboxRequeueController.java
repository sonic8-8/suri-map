package com.surimap.sync.outbox;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutboxRequeueController {

  private static final long MAX_ALLOWED_SKEW_MS = 30_000L;
  private static final long STALE_CLOCK_SYNC_AFTER_MS = 300_000L;
  private static final Set<String> CLOSED_INCIDENT_IDS =
      Set.of("inc-precinct-closed-001", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaa0012");

  private final Clock clock;

  public OutboxRequeueController(Clock clock) {
    this.clock = clock;
  }

  @PostMapping("/api/sync/outbox/requeue")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  public ResponseEntity<OutboxRequeueResponse> requeue(
      @RequestHeader(value = "X-PolicePhone-Id", required = false) String policePhoneId,
      @RequestBody OutboxRequeueRequest request) {
    if (policePhoneId == null || policePhoneId.isBlank()) {
      throw new OutboxRequeueApiException(HttpStatus.BAD_REQUEST, "police_phone_required");
    }

    if (CLOSED_INCIDENT_IDS.contains(request.incidentId())) {
      throw new OutboxRequeueApiException(
          HttpStatus.CONFLICT,
          new OutboxRequeueErrorResponse("incident_closed", "FAILED_FINAL", null, null, null));
    }

    RequeueDiagnostic diagnostic = diagnose(request);
    return ResponseEntity.accepted()
        .body(
            new OutboxRequeueResponse(
                request.operationId(),
                diagnostic.accepted(),
                OffsetDateTime.now(clock).toString(),
                diagnostic.outboxStatus(),
                diagnostic.retryable(),
                diagnostic.state(),
                diagnostic.userSafeFailureCategory()));
  }

  private RequeueDiagnostic diagnose(OutboxRequeueRequest request) {
    if (requiresClockResync(request)) {
      return new RequeueDiagnostic(
          false, "FAILED_RETRYABLE", true, "RETRYABLE", "CLOCK_RESYNC_REQUIRED");
    }

    return new RequeueDiagnostic(true, "PENDING", true, "RETRYABLE", "RETRYABLE_NETWORK");
  }

  private boolean requiresClockResync(OutboxRequeueRequest request) {
    if (request.clockOffsetMs() == null
        || Math.abs(request.clockOffsetMs()) > MAX_ALLOWED_SKEW_MS) {
      return true;
    }

    if (request.clientTs() == null || request.clockSyncedAt() == null) {
      return true;
    }

    try {
      OffsetDateTime clientTs = OffsetDateTime.parse(request.clientTs());
      OffsetDateTime clockSyncedAt = OffsetDateTime.parse(request.clockSyncedAt());
      long clockSyncAgeMs =
          Duration.between(clockSyncedAt.toInstant(), clientTs.toInstant()).toMillis();
      return clockSyncAgeMs > STALE_CLOCK_SYNC_AFTER_MS;
    } catch (DateTimeParseException ex) {
      return true;
    }
  }

  private record RequeueDiagnostic(
      boolean accepted,
      String outboxStatus,
      boolean retryable,
      String state,
      String userSafeFailureCategory) {}
}
