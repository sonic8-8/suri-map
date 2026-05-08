package com.surimap.app.controller.policephone.response;

import com.surimap.policephone.PolicePhoneHeartbeatResult;
import java.time.Instant;
import java.util.UUID;

public class PolicePhoneHeartbeatResponse {

  private final UUID id;
  private final String status;
  private final long version;
  private final UUID policePhoneId;
  private final long sequence;
  private final Instant lastHeartbeatAt;
  private final Instant lastSyncAt;

  public PolicePhoneHeartbeatResponse(
      UUID id,
      String status,
      long version,
      UUID policePhoneId,
      long sequence,
      Instant lastHeartbeatAt,
      Instant lastSyncAt) {
    this.id = id;
    this.status = status;
    this.version = version;
    this.policePhoneId = policePhoneId;
    this.sequence = sequence;
    this.lastHeartbeatAt = lastHeartbeatAt;
    this.lastSyncAt = lastSyncAt;
  }

  public static PolicePhoneHeartbeatResponse from(PolicePhoneHeartbeatResult result) {
    return new PolicePhoneHeartbeatResponse(
        result.id(),
        result.status().name(),
        result.version(),
        result.policePhoneId(),
        result.sequence(),
        result.lastHeartbeatAt(),
        result.lastSyncAt());
  }

  public UUID getId() {
    return id;
  }

  public String getStatus() {
    return status;
  }

  public long getVersion() {
    return version;
  }

  public UUID getPolicePhoneId() {
    return policePhoneId;
  }

  public long getSequence() {
    return sequence;
  }

  public Instant getLastHeartbeatAt() {
    return lastHeartbeatAt;
  }

  public Instant getLastSyncAt() {
    return lastSyncAt;
  }
}
