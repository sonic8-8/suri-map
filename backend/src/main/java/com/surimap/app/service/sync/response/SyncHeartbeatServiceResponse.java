package com.surimap.app.service.sync.response;

import com.surimap.domain.connectionstatus.AccountConnectionStatus;
import com.surimap.domain.connectionstatus.ConnectionStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SyncHeartbeatServiceResponse {

  private final ConnectionStatus status;
  private final long version;
  private final UUID accountId;
  private final Instant lastHeartbeatAt;
  private final Instant lastSyncAt;

  // heartbeat를 방금 받았으므로 응답의 통신 상태는 항상 정상이다.
  public static SyncHeartbeatServiceResponse from(AccountConnectionStatus status) {
    return SyncHeartbeatServiceResponse.builder()
        .status(ConnectionStatus.ONLINE)
        .version(status.getVersion())
        .accountId(status.getAccountId())
        .lastHeartbeatAt(status.getLastHeartbeatAt())
        .lastSyncAt(status.getLastSyncAt())
        .build();
  }
}
