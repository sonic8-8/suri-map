package com.surimap.app.controller.policephone.request;

import com.surimap.app.service.policephone.request.PolicePhoneHeartbeatServiceRequest;
import com.surimap.common.auth.AccountType;
import com.surimap.common.auth.OrganizationType;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public class PolicePhoneHeartbeatRequest {

  @NotNull private Instant clientTs;
  @NotNull private Long sequence;
  private Instant lastSyncAt;
  private Integer batteryPercent;

  public Instant getClientTs() {
    return clientTs;
  }

  public void setClientTs(Instant clientTs) {
    this.clientTs = clientTs;
  }

  public Long getSequence() {
    return sequence;
  }

  public void setSequence(Long sequence) {
    this.sequence = sequence;
  }

  public Instant getLastSyncAt() {
    return lastSyncAt;
  }

  public void setLastSyncAt(Instant lastSyncAt) {
    this.lastSyncAt = lastSyncAt;
  }

  public Integer getBatteryPercent() {
    return batteryPercent;
  }

  public void setBatteryPercent(Integer batteryPercent) {
    this.batteryPercent = batteryPercent;
  }

  public PolicePhoneHeartbeatServiceRequest toServiceRequest(
      UUID policePhoneId,
      String accountId,
      AccountType accountType,
      OrganizationType organizationType) {
    return new PolicePhoneHeartbeatServiceRequest(
        policePhoneId,
        accountId,
        accountType,
        organizationType,
        clientTs,
        sequence,
        lastSyncAt,
        batteryPercent);
  }
}
