package com.surimap.domain.path;

import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SearchPathLifecycleEvent {

  private UUID id;
  private UUID searchPathId;
  private String eventType;
  private Instant clientTs;
  private Instant serverReceivedAt;
  private long version;
  private Instant createdAt;

  @Builder
  private SearchPathLifecycleEvent(
      UUID id,
      UUID searchPathId,
      String eventType,
      Instant clientTs,
      Instant serverReceivedAt,
      long version,
      Instant createdAt) {
    this.id = id;
    this.searchPathId = searchPathId;
    this.eventType = eventType;
    this.clientTs = clientTs;
    this.serverReceivedAt = serverReceivedAt;
    this.version = version;
    this.createdAt = createdAt;
  }
}
