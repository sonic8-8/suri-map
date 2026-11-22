package com.surimap.offlinepackage.query;

public record OfflinePackageInstallationStatus(
    String id,
    String incidentId,
    String policePhoneId,
    String status,
    long version,
    long sequence,
    int manifestVersion,
    int activeManifestVersion,
    boolean readyForOfflineUse) {

  public OfflinePackageInstallationStatus(
      String id,
      String incidentId,
      String policePhoneId,
      String status,
      long version,
      long sequence,
      int manifestVersion,
      boolean readyForOfflineUse) {
    this(
        id,
        incidentId,
        policePhoneId,
        status,
        version,
        sequence,
        manifestVersion,
        manifestVersion,
        readyForOfflineUse);
  }
}
