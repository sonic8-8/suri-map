package com.surimap.dutyshift;

import java.time.Instant;
import java.util.UUID;

/** duty_shift row with joined incident context. */
public class DutyShift {

  private UUID id;
  private UUID incidentId;
  private UUID opId;
  private UUID incidentAssignmentId;
  private UUID policePhoneId;
  private String policePhoneCode;
  private String policePhoneDisplayName;
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
      UUID policePhoneId,
      String status,
      UUID startedByAccountId,
      UUID endedByAccountId,
      Instant startedAt,
      Instant endedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this(
        id,
        incidentId,
        opId,
        incidentAssignmentId,
        policePhoneId,
        null,
        null,
        status,
        startedByAccountId,
        endedByAccountId,
        startedAt,
        endedAt,
        version,
        createdAt,
        updatedAt);
  }

  public DutyShift(
      UUID id,
      UUID incidentId,
      UUID opId,
      UUID incidentAssignmentId,
      UUID policePhoneId,
      String policePhoneCode,
      String policePhoneDisplayName,
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
    this.policePhoneId = policePhoneId;
    this.policePhoneCode = policePhoneCode;
    this.policePhoneDisplayName = policePhoneDisplayName;
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

  public UUID getPolicePhoneId() {
    return policePhoneId;
  }

  public void setPolicePhoneId(UUID policePhoneId) {
    this.policePhoneId = policePhoneId;
  }

  public String getPolicePhoneCode() {
    return policePhoneCode;
  }

  public void setPolicePhoneCode(String policePhoneCode) {
    this.policePhoneCode = policePhoneCode;
  }

  public String getPolicePhoneDisplayName() {
    return policePhoneDisplayName;
  }

  public void setPolicePhoneDisplayName(String policePhoneDisplayName) {
    this.policePhoneDisplayName = policePhoneDisplayName;
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
