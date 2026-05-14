package com.surimap.incident.repository;

import java.time.Instant;
import java.util.UUID;

/** MyBatis resultMap 전용 row 모음. setter는 mapper 주입용이며 domain 판단을 넣지 않는다. */
public final class IncidentReadRows {

  private IncidentReadRows() {}

  /** 목록 응답을 만들기 위한 incident row. */
  public static class ListRow {
    private UUID id;
    private String title;
    private String status;
    private long version;
    private Instant closedAt;

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
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

    public long getVersion() {
      return version;
    }

    public void setVersion(long version) {
      this.version = version;
    }

    public Instant getClosedAt() {
      return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
      this.closedAt = closedAt;
    }
  }

  /** 상세 응답의 incident 핵심 상태 row. */
  public static class DetailRow {
    private UUID id;
    private String title;
    private String status;
    private Instant openedAt;
    private long version;

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
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

    public long getVersion() {
      return version;
    }

    public void setVersion(long version) {
      this.version = version;
    }
  }

  /** 종료 사건 상세 응답의 sanitized terminal 상태 row. */
  public static class TerminalDetailRow {
    private UUID id;
    private String status;
    private long version;
    private Instant closedAt;

    public UUID getId() {
      return id;
    }

    public void setId(UUID id) {
      this.id = id;
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

    public Instant getClosedAt() {
      return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
      this.closedAt = closedAt;
    }
  }

  /** 상세 응답에 붙는 active missing_person row. */
  public static class MissingPersonRow {
    private UUID incidentId;
    private String displayName;
    private String photoObjectKey;
    private String appearanceText;
    private String lastSeenLocationText;
    private Instant lastSeenAt;

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
  }

  /** 상세 응답에 붙는 active incident_assignment row. */
  public static class AssignmentRow {
    private String accountId;
    private String accountDisplayName;
    private String accountType;
    private String organizationType;
    private String incidentRole;
    private Instant assignedAt;

    public String getAccountId() {
      return accountId;
    }

    public void setAccountId(String accountId) {
      this.accountId = accountId;
    }

    public String getAccountDisplayName() {
      return accountDisplayName;
    }

    public void setAccountDisplayName(String accountDisplayName) {
      this.accountDisplayName = accountDisplayName;
    }

    public String getAccountType() {
      return accountType;
    }

    public void setAccountType(String accountType) {
      this.accountType = accountType;
    }

    public String getOrganizationType() {
      return organizationType;
    }

    public void setOrganizationType(String organizationType) {
      this.organizationType = organizationType;
    }

    public String getIncidentRole() {
      return incidentRole;
    }

    public void setIncidentRole(String incidentRole) {
      this.incidentRole = incidentRole;
    }

    public Instant getAssignedAt() {
      return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
      this.assignedAt = assignedAt;
    }
  }

  /** 알림 대상 계산을 위한 active assignment + account + police_phone row. */
  public static class AssignmentTargetRow {
    private String accountId;
    private String incidentRole;
    private String accountType;
    private String organizationType;
    private String policePhoneId;

    public String getAccountId() {
      return accountId;
    }

    public void setAccountId(String accountId) {
      this.accountId = accountId;
    }

    public String getIncidentRole() {
      return incidentRole;
    }

    public void setIncidentRole(String incidentRole) {
      this.incidentRole = incidentRole;
    }

    public String getAccountType() {
      return accountType;
    }

    public void setAccountType(String accountType) {
      this.accountType = accountType;
    }

    public String getOrganizationType() {
      return organizationType;
    }

    public void setOrganizationType(String organizationType) {
      this.organizationType = organizationType;
    }

    public String getPolicePhoneId() {
      return policePhoneId;
    }

    public void setPolicePhoneId(String policePhoneId) {
      this.policePhoneId = policePhoneId;
    }
  }
}
