package com.surimap.domain.connectionstatus;

import com.surimap.global.event.EventPublishRequest;
import com.surimap.global.event.EventPublisher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** heartbeat를 받을 때마다 그 계정이 배정된 진행 중 사건마다 ACCOUNT_HEARTBEAT_UPDATED를 하나씩 발행한다. */
@Component
public class AccountHeartbeatEventPublisher {

  public static final String TYPE = "ACCOUNT_HEARTBEAT_UPDATED";
  public static final String SOURCE_ENTITY_TYPE = "account_heartbeat";
  private static final int PAYLOAD_FORMAT_VERSION = 1;

  private final EventPublisher eventPublisher;

  public AccountHeartbeatEventPublisher(EventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  public void publish(AccountConnectionStatus status, List<UUID> openIncidentIds) {
    openIncidentIds.forEach(incidentId -> eventPublisher.publish(request(status, incidentId)));
  }

  private static EventPublishRequest request(AccountConnectionStatus status, UUID incidentId) {
    // 시각은 ISO-8601 문자열로 담는다. Instant를 그대로 넣으면 저장할 때 epoch 초 숫자로 바뀐다.
    // id, status, version은 전송 worker의 공통 검증기에서 모든 이벤트에 필수로 검사되는 필드다.
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
