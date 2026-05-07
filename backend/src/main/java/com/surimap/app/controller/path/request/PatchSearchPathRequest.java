package com.surimap.app.controller.path.request;

import com.surimap.app.service.path.request.EndSearchPathServiceRequest;
import java.time.Instant;

public record PatchSearchPathRequest(String action, Instant clientTs, Integer clockOffsetMs) {

  public EndSearchPathServiceRequest toServiceRequest(String idempotencyKey) {
    return new EndSearchPathServiceRequest(clientTs, idempotencyKey);
  }
}
