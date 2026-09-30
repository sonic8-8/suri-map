package com.surimap.global.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 내부 이벤트 발행 입력. SSE data에도 직렬화되므로 필드 이름은 소비자와의 계약이다. */
@Getter
@Builder
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EventPublishRequest {
  private UUID eventId;
  private UUID incidentId;
  private String type;
  private int payloadFormatVersion;
  private String sourceEntityType;
  private UUID sourceEntityId;
  private Instant occurredAt;
  private Map<String, Object> payload;
}
