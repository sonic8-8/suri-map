package com.surimap.marker.notification.adapter;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm.firebase")
public class FirebaseFcmProperties {

  private String credentialsLocation = "";
  private String credentialsJsonBase64 = "";
  private String projectId = "";
  private boolean dryRun;

  public String getCredentialsLocation() {
    return credentialsLocation;
  }

  public void setCredentialsLocation(String credentialsLocation) {
    this.credentialsLocation = credentialsLocation == null ? "" : credentialsLocation.trim();
  }

  public String getCredentialsJsonBase64() {
    return credentialsJsonBase64;
  }

  public void setCredentialsJsonBase64(String credentialsJsonBase64) {
    this.credentialsJsonBase64 = credentialsJsonBase64 == null ? "" : credentialsJsonBase64.trim();
  }

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId == null ? "" : projectId.trim();
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public void setDryRun(boolean dryRun) {
    this.dryRun = dryRun;
  }

  boolean hasCredentialsJsonBase64() {
    return !credentialsJsonBase64.isBlank();
  }

  boolean hasCredentialsLocation() {
    return !credentialsLocation.isBlank();
  }

  boolean hasProjectId() {
    return !projectId.isBlank();
  }
}
