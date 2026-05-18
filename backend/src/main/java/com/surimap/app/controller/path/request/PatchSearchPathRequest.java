package com.surimap.app.controller.path.request;

import com.surimap.app.service.path.request.PatchSearchPathServiceRequest;
import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.domain.path.exception.SearchPathGuardException;
import java.time.Instant;
import java.util.Locale;

public record PatchSearchPathRequest(String action, Instant clientTs, Integer clockOffsetMs) {

  public PatchSearchPathServiceRequest toServiceRequest(String idempotencyKey) {
    return new PatchSearchPathServiceRequest(parseAction(), clientTs, idempotencyKey);
  }

  private SearchPathLifecycleAction parseAction() {
    if (action == null || action.isBlank()) {
      throw new SearchPathGuardException("write_conflict");
    }
    try {
      return SearchPathLifecycleAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new SearchPathGuardException("write_conflict");
    }
  }
}
