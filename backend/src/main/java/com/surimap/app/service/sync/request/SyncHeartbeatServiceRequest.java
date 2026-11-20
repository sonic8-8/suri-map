package com.surimap.app.service.sync.request;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncHeartbeatServiceRequest {

  private final UUID accountId;
  private final UUID policePhoneId;
  private final Instant lastSyncAt;
}
