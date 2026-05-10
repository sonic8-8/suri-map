package com.surimap.retention.purge;

import java.time.Instant;
import java.util.UUID;

public class PurgeHookStepRecord {

  private UUID purgeRunId;
  private PurgeHookName hookName;
  private PurgeHookStatus status;
  private long purgedCount;
  private long retainedCount;
  private String errorCode;
  private Instant completedAt;
  private Instant updatedAt;

  public static PurgeHookStepRecord of(
      UUID purgeRunId,
      PurgeHookName hookName,
      PurgeHookStatus status,
      long purgedCount,
      long retainedCount,
      String errorCode,
      Instant completedAt,
      Instant updatedAt) {
    PurgeHookStepRecord record = new PurgeHookStepRecord();
    record.setPurgeRunId(purgeRunId);
    record.setHookName(hookName);
    record.setStatus(status);
    record.setPurgedCount(purgedCount);
    record.setRetainedCount(retainedCount);
    record.setErrorCode(errorCode);
    record.setCompletedAt(completedAt);
    record.setUpdatedAt(updatedAt);
    return record;
  }

  public UUID getPurgeRunId() {
    return purgeRunId;
  }

  public void setPurgeRunId(UUID purgeRunId) {
    this.purgeRunId = purgeRunId;
  }

  public PurgeHookName getHookName() {
    return hookName;
  }

  public void setHookName(PurgeHookName hookName) {
    this.hookName = hookName;
  }

  public PurgeHookStatus getStatus() {
    return status;
  }

  public void setStatus(PurgeHookStatus status) {
    this.status = status;
  }

  public long getPurgedCount() {
    return purgedCount;
  }

  public void setPurgedCount(long purgedCount) {
    this.purgedCount = purgedCount;
  }

  public long getRetainedCount() {
    return retainedCount;
  }

  public void setRetainedCount(long retainedCount) {
    this.retainedCount = retainedCount;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public void setErrorCode(String errorCode) {
    this.errorCode = errorCode;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void setCompletedAt(Instant completedAt) {
    this.completedAt = completedAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
