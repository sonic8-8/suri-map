package com.surimap.app.controller.path.request;

import com.surimap.app.service.path.request.SearchPathLifecycleAction;
import com.surimap.app.service.path.request.SearchPathStatusUpdateServiceRequest;
import com.surimap.domain.path.exception.SearchPathGuardException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SearchPathStatusUpdateRequest {

  private String action;
  private Instant clientTs;
  private Integer clockOffsetMs;

  @Builder
  private SearchPathStatusUpdateRequest(String action, Instant clientTs, Integer clockOffsetMs) {
    this.action = action;
    this.clientTs = clientTs;
    this.clockOffsetMs = clockOffsetMs;
  }

  public SearchPathStatusUpdateServiceRequest toServiceRequest(
      UUID searchPathId, UUID policePhoneId, UUID accountId, String idempotencyKey) {
    return SearchPathStatusUpdateServiceRequest.builder()
        .searchPathId(searchPathId)
        .policePhoneId(policePhoneId)
        .accountId(accountId)
        .action(parseAction())
        .clientTs(clientTs)
        .clockOffsetMs(clockOffsetMs)
        .idempotencyKey(idempotencyKey)
        .build();
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
