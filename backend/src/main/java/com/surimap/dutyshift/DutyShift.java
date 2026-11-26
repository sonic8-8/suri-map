package com.surimap.dutyshift;

import java.time.Instant;
import java.util.UUID;

/** duty_shift row with joined incident and account context. */
public class DutyShift {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private UUID incidentAssignmentId;
  private UUID accountId;
  private String accountDisplayName;
  private String status;
  private UUID startedByAccountId;
  private UUID endedByAccountId;
  private Instant startedAt;
  private Instant endedAt;
  private long version;
  private Instant createdAt;
  private Instant updatedAt;

  public DutyShift() {}

  public DutyShift(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID incidentAssignmentId,
      String status,
      UUID startedByAccountId,
      UUID endedByAccountId,
      Instant startedAt,
      Instant endedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.incidentId = incidentId;
    this.opId = opId;
    this.incidentAssignmentId = incidentAssignmentId;
    this.status = status;
    this.startedByAccountId = startedByAccountId;
    this.endedByAccountId = endedByAccountId;
    this.startedAt = startedAt;
    this.endedAt = endedAt;
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

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public UUID getOpId() {
    return opId;
  }

  public void setOpId(UUID opId) {
    this.opId = opId;
  }

  public UUID getIncidentAssignmentId() {
    return incidentAssignmentId;
  }

  public void setIncidentAssignmentId(UUID incidentAssignmentId) {
    this.incidentAssignmentId = incidentAssignmentId;
  }

  /** 근무한 계정. incident_assignment에서 조회할 때만 채워진다. */
  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  public String getAccountDisplayName() {
    return accountDisplayName;
  }

  public void setAccountDisplayName(String accountDisplayName) {
    this.accountDisplayName = accountDisplayName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public UUID getStartedByAccountId() {
    return startedByAccountId;
  }

  public void setStartedByAccountId(UUID startedByAccountId) {
    this.startedByAccountId = startedByAccountId;
  }

  public UUID getEndedByAccountId() {
    return endedByAccountId;
  }

  public void setEndedByAccountId(UUID endedByAccountId) {
    this.endedByAccountId = endedByAccountId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getEndedAt() {
    return endedAt;
  }

  public void setEndedAt(Instant endedAt) {
    this.endedAt = endedAt;
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
