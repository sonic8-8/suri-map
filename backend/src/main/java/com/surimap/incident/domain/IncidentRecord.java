package com.surimap.incident.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * incident 테이블 row의 도메인 표현. mock-112 {@code sourceIncidentId}·내부 UUID·상태·버전을 보관하며 MyBatis mapper가
 * 읽고 쓴다.
 */
public class IncidentRecord {

  private UUID id;
  private String sourceIncidentId;
  private String title;
  private String status;
  private Instant openedAt;
  private Instant closedAt;
  private UUID closedByAccountId;
  private long version;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getSourceIncidentId() {
    return sourceIncidentId;
  }

  public void setSourceIncidentId(String sourceIncidentId) {
    this.sourceIncidentId = sourceIncidentId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Instant getOpenedAt() {
    return openedAt;
  }

  public void setOpenedAt(Instant openedAt) {
    this.openedAt = openedAt;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public void setClosedAt(Instant closedAt) {
    this.closedAt = closedAt;
  }

  public UUID getClosedByAccountId() {
    return closedByAccountId;
  }

  public void setClosedByAccountId(UUID closedByAccountId) {
    this.closedByAccountId = closedByAccountId;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
