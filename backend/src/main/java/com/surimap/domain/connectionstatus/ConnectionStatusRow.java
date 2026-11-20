package com.surimap.domain.connectionstatus;

import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import java.time.Instant;
import java.util.UUID;

/**
 * 사건에 배정된 현장 계정과 그 계정의 통신 상태 기록을 합친 조회 결과. heartbeat를 한 번도 받지 않은 계정은 시각이 비어 있고
 * version이 0이다.
 */
public record ConnectionStatusRow(
    UUID accountId,
    AccountType accountType,
    OrganizationType organizationType,
    UUID incidentId,
    Instant lastHeartbeatAt,
    Instant lastSyncAt,
    long version,
    ConnectionStatus status) {

  // MyBatis는 DB 값만으로 이 생성자를 쓴다. 통신 상태는 조회한 시각을 알아야 하므로 withStatusAt에서 채운다.
  public ConnectionStatusRow(
      UUID accountId,
      AccountType accountType,
      OrganizationType organizationType,
      UUID incidentId,
      Instant lastHeartbeatAt,
      Instant lastSyncAt,
      long version) {
    this(
        accountId,
        accountType,
        organizationType,
        incidentId,
        lastHeartbeatAt,
        lastSyncAt,
        version,
        null);
  }

  public ConnectionStatusRow withStatusAt(Instant now) {
    return new ConnectionStatusRow(
        accountId,
        accountType,
        organizationType,
        incidentId,
        lastHeartbeatAt,
        lastSyncAt,
        version,
        ConnectionStatus.of(lastHeartbeatAt, now));
  }
}
