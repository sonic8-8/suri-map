package com.surimap.app.controller.sync.request;

import com.surimap.app.service.sync.request.SyncHeartbeatServiceRequest;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class SyncHeartbeatRequest {

  @NotNull private Instant clientTs;
  private Instant lastSyncAt;

  public SyncHeartbeatServiceRequest toServiceRequest(UUID accountId, UUID policePhoneId) {
    return SyncHeartbeatServiceRequest.builder()
        .accountId(accountId)
        .policePhoneId(policePhoneId)
        .lastSyncAt(lastSyncAt)
        .build();
  }
}
