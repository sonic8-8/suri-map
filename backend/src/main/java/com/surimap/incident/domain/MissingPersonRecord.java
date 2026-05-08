package com.surimap.incident.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * missing_person 테이블 row. {@code importedAt}은 import 추적용 내부 값이라 소비자 DTO에는 노출하지 않는다.
 */
public class MissingPersonRecord {

  private UUID incidentId;
  private String displayName;
  private String photoObjectKey;
  private String appearanceText;
  private String lastSeenLocationText;
  private Instant lastSeenAt;
  private Instant importedAt;

  public UUID getIncidentId() {
    return incidentId;
  }

  public void setIncidentId(UUID incidentId) {
    this.incidentId = incidentId;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getPhotoObjectKey() {
    return photoObjectKey;
  }

  public void setPhotoObjectKey(String photoObjectKey) {
    this.photoObjectKey = photoObjectKey;
  }

  public String getAppearanceText() {
    return appearanceText;
  }

  public void setAppearanceText(String appearanceText) {
    this.appearanceText = appearanceText;
  }

  public String getLastSeenLocationText() {
    return lastSeenLocationText;
  }

  public void setLastSeenLocationText(String lastSeenLocationText) {
    this.lastSeenLocationText = lastSeenLocationText;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public void setLastSeenAt(Instant lastSeenAt) {
    this.lastSeenAt = lastSeenAt;
  }

  public Instant getImportedAt() {
    return importedAt;
  }

  public void setImportedAt(Instant importedAt) {
    this.importedAt = importedAt;
  }
}
