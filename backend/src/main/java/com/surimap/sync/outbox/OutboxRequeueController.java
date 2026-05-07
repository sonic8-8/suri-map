package com.surimap.sync.outbox;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OutboxRequeueController {

  private final Clock clock;

  public OutboxRequeueController(Clock clock) {
    this.clock = clock;
  }

  @PostMapping("/api/sync/outbox/requeue")
  public ResponseEntity<OutboxRequeueResponse> requeue(
      @RequestBody OutboxRequeueRequest request) {
    return ResponseEntity.accepted()
        .body(
            new OutboxRequeueResponse(
                request.operationId(), true, OffsetDateTime.now(clock).toString()));
  }
}
