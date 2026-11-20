package com.surimap.domain.connectionstatus;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계정마다 하나씩 두는 통신 상태 기록. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountConnectionStatus {

  private UUID accountId;
  private Instant lastHeartbeatAt;
  private Instant lastSyncAt;
  private UUID lastPolicePhoneId;
  private long version;
}
