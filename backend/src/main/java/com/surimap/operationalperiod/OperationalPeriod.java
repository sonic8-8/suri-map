package com.surimap.operationalperiod;

import java.time.Instant;
import java.util.UUID;

/** operational_period 도메인 엔티티 (S8.json §domain_model.entities). */
public class OperationalPeriod {

  private UUID id;
  private UUID incidentId;
  private int sequenceNumber;
  private String status;
  private String reason;
  private String reasonMemo;
  private UUID startedByAccountId;
  private UUID endedByAccountId;
  private Instant startedAt;
  private Instant endedAt;
  private long version;
  private Instant createdAt;
  private Instant updatedAt;

  public OperationalPeriod() {}

  public OperationalPeriod(
      UUID id,
      UUID incidentId,
      int sequenceNumber,
      String status,
      String reason,
      String reasonMemo,
      UUID startedByAccountId,
      UUID endedByAccountId,
      Instant startedAt,
      Instant endedAt,
      long version,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.incidentId = incidentId;
    this.sequenceNumber = sequenceNumber;
    this.status = status;
    this.reason = reason;
    this.reasonMemo = reasonMemo;
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

  public int getSequenceNumber() {
    return sequenceNumber;
  }

  public void setSequenceNumber(int sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getReasonMemo() {
    return reasonMemo;
  }

  public void setReasonMemo(String reasonMemo) {
    this.reasonMemo = reasonMemo;
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
