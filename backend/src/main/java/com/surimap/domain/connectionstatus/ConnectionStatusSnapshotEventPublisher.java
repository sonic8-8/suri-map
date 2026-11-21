package com.surimap.domain.connectionstatus;

import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.sse.ServerSentEventMessage;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 상황판이 진행 중인 사건의 SSE에 연결될 때 맨 먼저 보내는 CONNECTION_STATUS_SNAPSHOT을 조립한다. 상황판 연결이 끊긴 동안 놓친
 * heartbeat를 다시 연결하자마자 채우려고 보낸다. 이 이벤트는 저장하지 않는다. 순번도 붙이지 않는다.
 */
@Component
public class ConnectionStatusSnapshotEventPublisher {

  public static final String TYPE = "CONNECTION_STATUS_SNAPSHOT";
  private static final String SOURCE_ENTITY_TYPE = "incident";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  private final ConnectionStatusQuery connectionStatusQuery;
  private final Clock clock;

  public ConnectionStatusSnapshotEventPublisher(
      ConnectionStatusQuery connectionStatusQuery, Clock clock) {
    this.connectionStatusQuery = connectionStatusQuery;
    this.clock = clock;
  }

  public ServerSentEventMessage snapshot(UUID incidentId) {
    List<Map<String, Object>> accounts =
        connectionStatusQuery.byIncident(incidentId).stream().map(this::account).toList();
    Instant serverTime = clock.instant();
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("serverTime", serverTime.toString());
    payload.put("accounts", accounts);
    return ServerSentEventMessage.unsequenced(
        EventPublishRequest.builder()
            .eventId(UUID.randomUUID())
            .incidentId(incidentId)
            .type(TYPE)
            .payloadFormatVersion(PAYLOAD_FORMAT_VERSION)
            .sourceEntityType(SOURCE_ENTITY_TYPE)
            .sourceEntityId(incidentId)
            .occurredAt(serverTime)
            .payload(payload)
            .build());
  }

  // heartbeat를 받은 적 없는 계정은 두 시각이 null이다. 상황판에서는 끊김으로 표시한다.
  private Map<String, Object> account(ConnectionStatusRow row) {
    Map<String, Object> account = new LinkedHashMap<>();
    account.put("accountId", row.accountId().toString());
    account.put("lastHeartbeatAt", isoOrNull(row.lastHeartbeatAt()));
    account.put("lastSyncAt", isoOrNull(row.lastSyncAt()));
    return account;
  }

  private static String isoOrNull(Instant instant) {
    return instant == null ? null : instant.toString();
  }
}
