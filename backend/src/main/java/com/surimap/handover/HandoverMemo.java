package com.surimap.handover;

import java.time.Instant;
import java.util.UUID;

public class HandoverMemo {

  private UUID id;
  private UUID opId;
  private String memoTargetType;
  private UUID memoTargetId;
  private String content;
  private UUID createdByAccountId;
  private UUID dutyShiftId;
  private String status;
  private long version;
  private Instant createdAt;
  private Instant updatedAt;

  public HandoverMemo() {}

  public HandoverMemo(
      UUID id,
      UUID opId,
      String memoTargetType,
      UUID memoTargetId,
      String content,
      UUID createdByAccountId,
      UUID dutyShiftId,
      String status,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.opId = opId;
    this.memoTargetType = memoTargetType;
    this.memoTargetId = memoTargetId;
    this.content = content;
    this.createdByAccountId = createdByAccountId;
    this.dutyShiftId = dutyShiftId;
    this.status = status;
    this.version = version;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getOpId() {
    return opId;
  }

  public void setOpId(UUID opId) {
    this.opId = opId;
  }

  public String getMemoTargetType() {
    return memoTargetType;
  }

  public void setMemoTargetType(String memoTargetType) {
    this.memoTargetType = memoTargetType;
  }

  public UUID getMemoTargetId() {
    return memoTargetId;
  }

  public void setMemoTargetId(UUID memoTargetId) {
    this.memoTargetId = memoTargetId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public UUID getCreatedByAccountId() {
    return createdByAccountId;
  }

  public void setCreatedByAccountId(UUID createdByAccountId) {
    this.createdByAccountId = createdByAccountId;
  }

  public UUID getDutyShiftId() {
    return dutyShiftId;
  }

  public void setDutyShiftId(UUID dutyShiftId) {
    this.dutyShiftId = dutyShiftId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
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
}
