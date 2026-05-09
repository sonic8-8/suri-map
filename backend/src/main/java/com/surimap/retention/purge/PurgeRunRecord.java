package com.surimap.retention.purge;

import java.time.Instant;
import java.util.UUID;

public class PurgeRunRecord {

  private UUID id;
  private UUID incidentId;
  private IncidentDataPurgeStatus status;
  private Instant closedAt;
  private Instant purgeDueAt;
  private Instant completedAt;
  private String lastErrorCode;
  private long version;
  private PurgeEnvironmentPolicy environmentPolicy;
  private Instant createdAt;
  private Instant updatedAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public IncidentDataPurgeStatus getStatus() {
    return status;
  }

  public void setStatus(IncidentDataPurgeStatus status) {
    this.status = status;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public Instant getPurgeDueAt() {
    return purgeDueAt;
  }

  public void setPurgeDueAt(Instant purgeDueAt) {
    this.purgeDueAt = purgeDueAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public void setLastErrorCode(String lastErrorCode) {
    this.lastErrorCode = lastErrorCode;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }

  public PurgeEnvironmentPolicy getEnvironmentPolicy() {
    return environmentPolicy;
  }

  public void setEnvironmentPolicy(PurgeEnvironmentPolicy environmentPolicy) {
    this.environmentPolicy = environmentPolicy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public IncidentDataPurgeRun toRun() {
    return new IncidentDataPurgeRun(
        id,
        incidentId,
        status,
        closedAt,
        purgeDueAt,
        completedAt,
        lastErrorCode,
        version,
        environmentPolicy,
        localPurgeState(status));
  }

  private static LocalPurgeState localPurgeState(IncidentDataPurgeStatus status) {
    return switch (status) {
      case PENDING, RUNNING -> LocalPurgeState.PURGE_PENDING;
      case WAITING_FOR_SYNC -> LocalPurgeState.WAITING_FOR_SYNC;
      case FAILED_RETRYABLE -> LocalPurgeState.FAILED_RETRYABLE;
      case COMPLETED -> LocalPurgeState.LOCAL_PURGED;
    };
  }
}
