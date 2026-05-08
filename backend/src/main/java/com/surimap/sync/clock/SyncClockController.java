package com.surimap.sync.clock;

import com.surimap.common.auth.Channel;
import com.surimap.common.auth.RequireChannel;
import com.surimap.common.auth.RequirePolicePhone;
import com.surimap.common.auth.RequirePolicePhoneAssigned;
import com.surimap.common.auth.RequirePolicePhoneRegistered;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SyncClockController {

  private static final long MAX_ALLOWED_SKEW_MS = 30_000L;

  private final Clock clock;

  public SyncClockController(Clock clock) {
    this.clock = clock;
  }

  @PostMapping("/api/sync/clock")
  @RequireChannel(Channel.APP)
  @RequirePolicePhone
  @RequirePolicePhoneRegistered
  @RequirePolicePhoneAssigned
  public ResponseEntity<?> syncClock(@RequestBody SyncClockRequest request) {
    OffsetDateTime clientTs = OffsetDateTime.parse(request.clientTs());
    OffsetDateTime serverTs = OffsetDateTime.now(clock);
    long clockOffsetMs =
        Duration.between(clientTs.toInstant(), serverTs.toInstant()).toMillis();

    if (Math.abs(clockOffsetMs) > MAX_ALLOWED_SKEW_MS) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(Map.of("error", "clock_skew_exceeded"));
    }

    return ResponseEntity.ok(
        new SyncClockResponse(
            request.clientTs(),
            serverTs.toString(),
            clockOffsetMs,
            serverTs.toString(),
            MAX_ALLOWED_SKEW_MS));
  }
}
