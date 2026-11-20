package com.surimap.app.controller.sync.response;

import com.surimap.app.service.sync.response.SyncHeartbeatServiceResponse;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncHeartbeatResponse {

  private final String status;
  private final long version;
  private final UUID accountId;
  private final Instant lastHeartbeatAt;
  private final Instant lastSyncAt;

  public static SyncHeartbeatResponse from(SyncHeartbeatServiceResponse response) {
    return SyncHeartbeatResponse.builder()
        .status(response.getStatus().name())
        .version(response.getVersion())
        .accountId(response.getAccountId())
        .lastHeartbeatAt(response.getLastHeartbeatAt())
        .lastSyncAt(response.getLastSyncAt())
        .build();
  }
}
