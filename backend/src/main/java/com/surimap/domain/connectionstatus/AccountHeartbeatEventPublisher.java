package com.surimap.domain.connectionstatus;

import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.sse.ServerSentEventConnectionRegistry;
import com.surimap.global.sse.ServerSentEventMessage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * heartbeat를 받을 때마다 그 계정이 배정된 진행 중 사건마다 ACCOUNT_HEARTBEAT_UPDATED를 하나씩 보낸다. 30초마다 오는 heartbeat를
 * 이벤트 발송 작업으로 저장하지 않으려고, 지금 그 사건에 연결된 상황판에만 순번 없이 바로 보낸다.
 *
 * <p>Backend 서버가 한 대라는 전제다. 서버를 여러 대로 늘리면 다른 서버에 연결된 상황판은 이 이벤트를 받지 못하므로 이 방식을 다시 정한다.
 */
@Component
public class AccountHeartbeatEventPublisher {

  public static final String TYPE = "ACCOUNT_HEARTBEAT_UPDATED";
  public static final String SOURCE_ENTITY_TYPE = "account_heartbeat";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  private final ServerSentEventConnectionRegistry connectionRegistry;

  public AccountHeartbeatEventPublisher(ServerSentEventConnectionRegistry connectionRegistry) {
    this.connectionRegistry = connectionRegistry;
  }

  public void publish(AccountConnectionStatus status, List<UUID> openIncidentIds) {
    openIncidentIds.forEach(
        incidentId ->
            connectionRegistry.sendToIncident(
                incidentId, ServerSentEventMessage.unsequenced(request(status, incidentId))));
  }

  private static EventPublishRequest request(AccountConnectionStatus status, UUID incidentId) {
    // 시각은 ISO-8601 문자열로 담는다. 상황판에서는 lastHeartbeatAt을 서버 시각으로 보고 시계 보정에 쓴다.
    // id, status, version은 #52에서 정한 payload를 그대로 두려고 남겼다.
    // heartbeat를 막 받았으므로 status는 항상 ONLINE이다.
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("id", status.getAccountId().toString());
    payload.put("status", ConnectionStatus.ONLINE.name());
    payload.put("version", status.getVersion());
    payload.put("accountId", status.getAccountId().toString());
    payload.put("lastHeartbeatAt", status.getLastHeartbeatAt().toString());
    payload.put(
        "lastSyncAt", status.getLastSyncAt() == null ? null : status.getLastSyncAt().toString());
    return EventPublishRequest.builder()
        .eventId(UUID.randomUUID())
        .incidentId(incidentId)
        .type(TYPE)
        .payloadFormatVersion(PAYLOAD_FORMAT_VERSION)
        .sourceEntityType(SOURCE_ENTITY_TYPE)
        .sourceEntityId(status.getAccountId())
        .occurredAt(status.getLastHeartbeatAt())
        .payload(payload)
        .build();
  }
}
